package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Real Gecko tab/session owner with persistent normal tabs and private-tab isolation. */
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
    data class ClosedTab(val url: String, val label: String)

    private val tabs = mutableListOf<Tab>()
    private val sessionStates = mutableMapOf<Long, String>()
    private val closedTabs = ArrayDeque<ClosedTab>()
    private var nextId = 1L
    private var activeIndex = -1

    fun count(): Int = tabs.size
    fun all(): List<Tab> = tabs.toList()
    fun active(): Tab? = tabs.getOrNull(activeIndex)
    fun activeIndex(): Int = activeIndex
    fun closed(): List<ClosedTab> = closedTabs.toList()

    private fun settings(privateMode: Boolean) = GeckoSessionSettings.Builder()
        .usePrivateMode(privateMode).useTrackingProtection(true).suspendMediaWhenInactive(true).build()

    fun create(privateMode: Boolean = false): Tab {
        val tab = Tab(nextId++, GeckoSession(settings(privateMode)), privateMode)
        tab.session.progressDelegate = progressDelegate()
        tabs.add(tab); activeIndex = tabs.lastIndex
        return tab
    }

    private fun progressDelegate() = object : GeckoSession.ProgressDelegate {
        override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) { updateSessionState(session, state) }
    }

    private fun updateUrlFromState(tab: Tab, state: GeckoSession.SessionState) {
        val index = state.currentIndex
        if (index >= 0 && index < state.size) { tab.url = state[index].uri ?: tab.url; tab.label = state[index].title ?: tab.label }
    }

    fun updateSessionState(session: GeckoSession, state: GeckoSession.SessionState) {
        val tab = tabs.firstOrNull { it.session === session } ?: return
        if (tab.privateMode) return
        sessionStates[tab.id] = state.toString().orEmpty(); updateUrlFromState(tab, state)
    }

    fun createAndOpen(privateMode: Boolean = false): Tab { val tab = create(privateMode); tab.session.open(runtime); activate(tabs.lastIndex); return tab }

    fun activate(index: Int): Tab? {
        if (index !in tabs.indices) return null
        if (activeIndex != index) { tabs.getOrNull(activeIndex)?.session?.setFocused(false); tabs.getOrNull(activeIndex)?.session?.setActive(false); activeIndex = index }
        active()?.session?.setActive(true); active()?.session?.setFocused(true); return active()
    }

    fun close(index: Int): Tab? {
        if (index !in tabs.indices) return null
        val wasActive = index == activeIndex
        val removed = tabs.removeAt(index)
        if (!removed.privateMode && removed.url.isNotBlank()) { closedTabs.addFirst(ClosedTab(removed.url, removed.label)); while (closedTabs.size > 20) closedTabs.removeLast() }
        sessionStates.remove(removed.id); removed.session.setActive(false); removed.session.setFocused(false); removed.session.close()
        if (tabs.isEmpty()) { activeIndex = -1; persist(); return null }
        activeIndex = when { !wasActive && index < activeIndex -> activeIndex - 1; activeIndex >= tabs.size -> tabs.lastIndex; else -> activeIndex }
        activate(activeIndex); persist(); return active()
    }

    fun closeActive(): Tab? = if (activeIndex >= 0) close(activeIndex) else null

    fun closeOthers(keepIndex: Int) {
        if (keepIndex !in tabs.indices) return
        val keep = tabs[keepIndex]
        tabs.toList().filter { it !== keep }.forEach { tab -> if (!tab.privateMode && tab.url.isNotBlank()) { closedTabs.addFirst(ClosedTab(tab.url, tab.label)); while (closedTabs.size > 20) closedTabs.removeLast() }; sessionStates.remove(tab.id); tab.session.close() }
        tabs.clear(); tabs.add(keep); activeIndex = 0; activate(0); persist()
    }

    fun closeToRight(index: Int) {
        if (index !in tabs.indices) return
        for (i in tabs.lastIndex downTo index + 1) { val tab = tabs.removeAt(i); if (!tab.privateMode && tab.url.isNotBlank()) { closedTabs.addFirst(ClosedTab(tab.url, tab.label)); while (closedTabs.size > 20) closedTabs.removeLast() }; sessionStates.remove(tab.id); tab.session.close() }
        activeIndex = activeIndex.coerceAtMost(tabs.lastIndex); if (activeIndex >= 0) activate(activeIndex); persist()
    }

    fun reopenClosed(): Tab? {
        val closed = closedTabs.removeFirstOrNull() ?: return null
        val tab = createAndOpen(false); tab.label = closed.label; tab.url = closed.url; tab.session.loadUri(closed.url); return tab
    }

    fun duplicate(index: Int): Tab? {
        val source = tabs.getOrNull(index) ?: return null
        val copy = createAndOpen(source.privateMode); copy.label = source.label; copy.url = source.url
        if (source.url.isNotBlank()) copy.session.loadUri(source.url)
        return copy
    }

    fun updateLabel(session: GeckoSession, label: String) { tabs.firstOrNull { it.session === session }?.label = label.ifBlank { "New tab" } }
    fun updateUrl(session: GeckoSession, url: String) { tabs.firstOrNull { it.session === session }?.url = url }
    fun indexOf(session: GeckoSession): Int = tabs.indexOfFirst { it.session === session }

    fun persist() {
        val store = stateStore ?: return
        tabs.filterNot { it.privateMode }.forEach { it.session.flushSessionState() }
        store.save(tabs.filterNot { it.privateMode }.map { tab -> TabStateStore.Tab(tab.id.toString(), tab.url, tab.label, false, tabs.indexOf(tab) == activeIndex, sessionStates[tab.id]) })
    }

    fun restorePersistedTabs(): List<Tab> {
        val store = stateStore ?: return emptyList(); val restored = store.restore()
        restored.forEach { saved ->
            val id = saved.id.toLongOrNull() ?: nextId++
            val tab = Tab(id, GeckoSession(settings(false)), false, saved.title.ifBlank { "Restored tab" }, saved.url)
            tab.session.progressDelegate = progressDelegate(); tabs.add(tab); if (tab.id >= nextId) nextId = tab.id + 1; tab.session.open(runtime)
            saved.sessionState?.let { GeckoSession.SessionState.fromString(it)?.let(tab.session::restoreState) } ?: tab.session.loadUri(saved.url)
        }
        activeIndex = restored.indexOfFirst { it.active }.let { if (it >= 0) it else if (tabs.isNotEmpty()) 0 else -1 }
        if (activeIndex >= 0) activate(activeIndex); return all()
    }

    fun openAllSessions() { tabs.forEach { if (!it.session.isOpen) it.session.open(runtime) }; if (activeIndex >= 0) activate(activeIndex) }
    fun closeAll() { persist(); tabs.forEach { it.session.close() }; tabs.clear(); sessionStates.clear(); activeIndex = -1 }
}
