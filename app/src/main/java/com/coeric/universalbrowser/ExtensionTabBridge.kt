package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Connects GeckoView's WebExtension tabs API to Universal Browser's real tab
 * manager. This keeps extension-created and extension-controlled tabs inside
 * the same GeckoSession lifecycle used by the browser UI.
 */
class ExtensionTabBridge(
    private val controller: WebExtensionController,
    private val tabs: BrowserTabManager,
    private val onSessionReady: (GeckoSession) -> Unit,
    private val onSessionClosed: (GeckoSession) -> Unit,
    private val onSessionActivated: (GeckoSession) -> Unit
) {
    private val attached = mutableSetOf<String>()

    fun attachInstalledExtensions() {
        controller.list().accept(
            { extensions ->
                extensions.orEmpty().forEach { attach(it) }
            },
            { error ->
                android.util.Log.w("UniversalBrowser", "Could not enumerate extensions", error)
            }
        )
    }

    fun attach(extension: WebExtension) {
        if (!attached.add(extension.id)) return

        extension.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession> {
                val tab = tabs.create(privateMode = false)
                val created = tab.session
                created.openedForExtension(source.id)
                onSessionReady(created)
                val requestedUrl = createDetails.url?.trim().orEmpty()
                if (requestedUrl.isNotBlank()) created.loadUri(requestedUrl)
                if (createDetails.active) {
                    val index = tabs.indexOf(created)
                    tabs.activate(index)
                    onSessionActivated(created)
                }
                return GeckoResult.fromValue(created)
            }
        })
    }

    fun installSessionDelegate(extension: WebExtension, session: GeckoSession) {
        session.getWebExtensionController().let { sessionController ->
            sessionController.setTabDelegate(extension, object : WebExtension.SessionTabDelegate {
                override fun onUpdateTab(
                    extension: WebExtension,
                    session: GeckoSession,
                    details: WebExtension.UpdateTabDetails
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                    details.url?.trim()?.takeIf { it.isNotBlank() }?.let { session.loadUri(it) }
                    if (details.active) {
                        tabs.activate(tabs.indexOf(session))
                        onSessionActivated(session)
                    }
                    return GeckoResult.allow
                }

                override fun onCloseTab(
                    source: WebExtension?,
                    session: GeckoSession
                ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                    if (tabs.indexOf(session) < 0) return GeckoResult.deny
                    tabs.close(tabs.indexOf(session))
                    onSessionClosed(session)
                    return GeckoResult.allow
                }
            })
        }
    }

    private fun GeckoSession.openedForExtension(@Suppress("UNUSED_PARAMETER") extensionId: String) {
        // Marker helper kept deliberately side-effect free; Gecko owns the
        // extension association while BrowserTabManager owns the session.
    }
}
