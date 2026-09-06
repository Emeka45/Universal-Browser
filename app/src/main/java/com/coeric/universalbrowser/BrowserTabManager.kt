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
            .build()
        val tab = Tab(nextId++, GeckoSession(settings), privateMode)
        tabs.add(tab)
        activeIndex = tabs.lastIndex
        return tab
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
        val snapshots = tabs.filterNot { it.privateMode }.map { tab ->
            tab.session.flushSessionState()
            TabStateStore.Tab(
                id = tab.id.toString(),
                url = tab.url,
                title = tab.label,
                privateMode = false,
                active = tabs.indexOf(tab) == activeIndex,
                sessionState = null
            )
        }
        store.save(snapshots)
    }

    /** Restores persisted normal tabs. Never restores private tabs because they are not stored. */
    fun restorePersistedTabs(): List<Tab> {
        val store = stateStore ?: return emptyList()
        val restored = store.restore()
        restored.forEach { saved ->
            val settings = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .useTrackingProtection(true)
                .build()
            val tab = Tab(
                id = saved.id.toLongOrNull() ?: nextId++,
                session = GeckoSession(settings),
                privateMode = false,
                label = saved.title.ifBlank { "Restored tab" },
                url = saved.url
            )
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
        activeIndex = -1
    }
}
