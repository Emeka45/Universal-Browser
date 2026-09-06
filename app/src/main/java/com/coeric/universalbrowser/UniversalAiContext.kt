package com.coeric.universalbrowser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.PageExtractionController

/** Extracts page context for Universal AI without shipping a model or API key in the APK. */
object UniversalAiContext {
    data class ContextData(val url: String, val text: String)

    fun extract(session: GeckoSession, url: String = "", callback: (ContextData?) -> Unit) {
        session.getSessionPageExtractor().getPageContent(
            PageExtractionController.ContentParams(true)
        ).accept(
            { content ->
                val text = content?.toString()?.trim().orEmpty().take(30000)
                callback(if (text.isBlank()) null else ContextData(url, text))
            },
            { callback(null) }
        )
    }

    fun selectedTextAction(
        session: GeckoSession,
        selectedText: String,
        action: String,
        callback: (prompt: String) -> Unit
    ) {
        val clean = selectedText.trim().take(12000)
        if (clean.isBlank()) return
        callback("$action the following selected text while preserving its meaning:\n\n$clean")
    }
}
