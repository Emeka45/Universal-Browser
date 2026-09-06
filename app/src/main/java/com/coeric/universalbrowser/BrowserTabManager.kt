package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Owns the browser's GeckoSession instances.
 * A GeckoSession represents one browser tab/window, so keeping them here
 * gives Universal a real tab lifecycle instead of a single-session browser.
 */
class BrowserTabManager(private val runtime: GeckoRuntime) {
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

    fun activate(index: Int): Tab? {
        if (index !in tabs.indices) return null
        if (activeIndex != index) {
            active()?.session?.setActive(false)
            active()?.session?.setFocused(false)
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
        removed.session.close()
        if (tabs.isEmpty()) {
            activeIndex = -1
            return null
        }
        activeIndex = when {
            !wasActive && index < activeIndex -> activeIndex - 1
            activeIndex >= tabs.size -> tabs.lastIndex
            else -> activeIndex
        }
        activate(activeIndex)
        return active()
    }

    fun closeActive(): Tab? = if (activeIndex >= 0) close(activeIndex) else null

    fun updateLabel(session: GeckoSession, label: String) {
        tabs.firstOrNull { it.session === session }?.label = label.ifBlank { "New tab" }
    }

    fun updateUrl(session: GeckoSession, url: String) {
        tabs.firstOrNull { it.session === session }?.url = url
    }

    fun indexOf(session: GeckoSession): Int = tabs.indexOfFirst { it.session === session }

    fun openAllSessions() {
        tabs.forEach { tab ->
            if (!tab.session.isOpen) tab.session.open(runtime)
        }
    }

    fun closeAll() {
        tabs.forEach { it.session.close() }
        tabs.clear()
        activeIndex = -1
    }
}
