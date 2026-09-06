package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Real Gecko tab/session owner with normal-tab crash recovery and private-tab isolation. */
class BrowserTabManager(
    private val runtime: GeckoRuntime,
    private val stateStore: TabStateStore? = null
) {
    data class Tab(
        val id: Long,
        val session: GeckoSession,
        val privateMode: Boolean,
        var label: String = "New tab",
        var url: String = ""
    )

    private val tabs = mutableListOf<Tab>()
    private val sessionStates = mutableMapOf<Long, String>()
    private var nextId = 1L
    private var activeIndex = -1

    fun count(): Int = tabs.size
    fun all(): List<Tab> = tabs.toList()
    fun active(): Tab? = tabs.getOrNull(activeIndex)
    fun activeIndex(): Int = activeIndex

    fun create(privateMode: Boolean = false): Tab {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .build()
        val tab = Tab(nextId++, GeckoSession(settings), privateMode)
        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                if (!tab.privateMode) {
                    sessionState.toString()?.let { sessionStates[tab.id] = it }
                    updateUrlFromState(tab, sessionState)
                    persist()
                }
            }
        }
        tabs.add(tab)
        activeIndex = tabs.lastIndex
        return tab
    }

    private fun updateUrlFromState(tab: Tab, state: GeckoSession.SessionState) {
        val index = state.currentIndex
        if (index >= 0 && index < state.size) {
            tab.url = state[index].uri ?: tab.url
            tab.label = state[index].title ?: tab.label
        }
    }

    fun createAndOpen(privateMode: Boolean = false): Tab {
        val tab = create(privateMode)
        tab.session.open(runtime)
        activate(tabs.lastIndex)
        return tab
    }

    fun activate(index: Int): Tab? {
        if (index !in tabs.indices) return null
        if (activeIndex != index) {
            tabs.getOrNull(activeIndex)?.session?.setFocused(false)
            tabs.getOrNull(activeIndex)?.session?.setActive(false)
            activeIndex = index
        }
        active()?.session?.setActive(true)
        active()?.session?.setFocused(true)
        return active()
    }

    fun close(index: Int): Tab? {
        if (index !in tabs.indices) return null
        val wasActive = index == activeIndex
        val removed = tabs.removeAt(index)
        sessionStates.remove(removed.id)
        removed.session.setActive(false)
        removed.session.setFocused(false)
        removed.session.close()
        if (tabs.isEmpty()) {
            activeIndex = -1
            persist()
            return null
        }
        activeIndex = when {
            !wasActive && index < activeIndex -> activeIndex - 1
            activeIndex >= tabs.size -> tabs.lastIndex
            else -> activeIndex
        }
        activate(activeIndex)
        persist()
        return active()
    }

    fun closeActive(): Tab? = if (activeIndex >= 0) close(activeIndex) else null

    fun updateLabel(session: GeckoSession, label: String) {
        tabs.firstOrNull { it.session === session }?.label = label.ifBlank { "New tab" }
        persist()
    }

    fun updateUrl(session: GeckoSession, url: String) {
        tabs.firstOrNull { it.session === session }?.url = url
        persist()
    }

    fun indexOf(session: GeckoSession): Int = tabs.indexOfFirst { it.session === session }

    /** Flushes and persists normal sessions. Private sessions are intentionally skipped. */
    fun persist() {
        val store = stateStore ?: return
        tabs.filterNot { it.privateMode }.forEach { it.session.flushSessionState() }
        val snapshots = tabs.filterNot { it.privateMode }.map { tab ->
            TabStateStore.Tab(
                id = tab.id.toString(),
                url = tab.url,
                title = tab.label,
                privateMode = false,
                active = tabs.indexOf(tab) == activeIndex,
                sessionState = sessionStates[tab.id]
            )
        }
        store.save(snapshots)
    }

    /** Restores persisted normal tabs. Private tabs are never restored because they are never stored. */
    fun restorePersistedTabs(): List<Tab> {
        val store = stateStore ?: return emptyList()
        val restored = store.restore()
        restored.forEach { saved ->
            val settings = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .useTrackingProtection(true)
                .suspendMediaWhenInactive(true)
                .build()
            val id = saved.id.toLongOrNull() ?: nextId++
            val tab = Tab(id, GeckoSession(settings), false, saved.title.ifBlank { "Restored tab" }, saved.url)
            tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                    sessionStates[tab.id] = sessionState.toString().orEmpty()
                    updateUrlFromState(tab, sessionState)
                    persist()
                }
            }
            tabs.add(tab)
            if (tab.id >= nextId) nextId = tab.id + 1
            tab.session.open(runtime)
            saved.sessionState?.let { encoded ->
                GeckoSession.SessionState.fromString(encoded)?.let { state -> tab.session.restoreState(state) }
            } ?: tab.session.loadUri(saved.url)
        }
        activeIndex = restored.indexOfFirst { it.active }.let { if (it >= 0) it else if (tabs.isNotEmpty()) 0 else -1 }
        if (activeIndex >= 0) activate(activeIndex)
        return all()
    }

    fun openAllSessions() {
        tabs.forEach { tab -> if (!tab.session.isOpen) tab.session.open(runtime) }
        if (activeIndex >= 0) activate(activeIndex)
    }

    fun closeAll() {
        persist()
        tabs.forEach { it.session.close() }
        tabs.clear()
        sessionStates.clear()
        activeIndex = -1
    }
}
