package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Connects GeckoView's WebExtension tabs API to Universal Browser's real tab
 * manager. Extension-created tabs therefore enter the same GeckoSession
 * lifecycle as browser-created tabs.
 */
class ExtensionTabBridge(
    private val controller: WebExtensionController,
    private val tabs: BrowserTabManager,
    private val onSessionReady: (GeckoSession) -> Unit,
    private val onSessionClosed: (GeckoSession) -> Unit,
    private val onSessionActivated: (GeckoSession) -> Unit
) {
    private val attached = linkedMapOf<String, WebExtension>()

    fun attachInstalledExtensions() {
        controller.list().accept(
            { extensions -> extensions.orEmpty().forEach { attach(it) } },
            { error -> android.util.Log.w("UniversalBrowser", "Could not enumerate extensions", error) }
        )
    }

    fun attach(extension: WebExtension) {
        if (attached.containsKey(extension.id)) return
        attached[extension.id] = extension

        extension.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession> {
                val tab = tabs.create(privateMode = false)
                val created = tab.session
                installSessionDelegate(source, created)
                onSessionReady(created)
                val requestedUrl = createDetails.url?.trim().orEmpty()
                if (requestedUrl.isNotBlank()) created.loadUri(requestedUrl)
                if (createDetails.active) {
                    tabs.activate(tabs.indexOf(created))
                    notifyActive(created)
                    onSessionActivated(created)
                }
                return GeckoResult.fromValue(created)
            }
        })

        tabs.all().forEach { installSessionDelegate(extension, it.session) }
    }

    fun bindSession(session: GeckoSession) {
        attached.values.forEach { installSessionDelegate(it, session) }
    }

    fun notifyActive(activeSession: GeckoSession) {
        tabs.all().forEach { tab ->
            controller.setTabActive(tab.session, tab.session === activeSession)
        }
    }

    private fun installSessionDelegate(extension: WebExtension, session: GeckoSession) {
        session.getWebExtensionController().setTabDelegate(
            extension,
            object : WebExtension.SessionTabDelegate {
                override fun onUpdateTab(
                    extension: WebExtension,
                    session: GeckoSession,
                    details: WebExtension.UpdateTabDetails
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                    details.url?.trim()?.takeIf { it.isNotBlank() }?.let { session.loadUri(it) }
                    if (details.active) {
                        tabs.activate(tabs.indexOf(session))
                        notifyActive(session)
                        onSessionActivated(session)
                    }
                    return GeckoResult.allow
                }

                override fun onCloseTab(
                    source: WebExtension?,
                    session: GeckoSession
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                    val index = tabs.indexOf(session)
                    if (index < 0) return GeckoResult.deny
                    tabs.close(index)
                    onSessionClosed(session)
                    return GeckoResult.allow
                }
            }
        )
    }
}
