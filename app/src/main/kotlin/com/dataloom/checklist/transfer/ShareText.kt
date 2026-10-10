package com.dataloom.checklist.transfer

/**
 * The Play Store link printed on PDFs and added to shared files. It comes from one place, the
 * `PLAY_STORE_URL` buildConfigField in app/build.gradle.kts, which is empty until the store
 * listing exists. While it is empty (or not an https URL) nothing links to the store: no Google
 * Play line, no QR code, no link in share messages.
 */
object StoreLink {
    /** The usable link, or null when none is configured. */
    fun of(configured: String): String? = configured.trim().takeIf { it.startsWith("https://") && it.length > "https://".length }
}

/** Subject and body sent with a shared file (`EXTRA_SUBJECT` and `EXTRA_TEXT`). */
data class ShareMessage(val subject: String, val text: String)

/**
 * Localized pieces of the message that goes with a shared .json export, already formatted with
 * the app name. [getApp] gives the "Get the app on Google Play" line for a store URL.
 */
data class ExportShareStrings(
    val subject: String,
    val intro: String,
    val getApp: (storeUrl: String) -> String,
    val stepsTitle: String,
    val steps: List<String>,
)

/** Builds the message for a shared .json export. Pure, so both store-link states are unit tested. */
object ExportShareText {
    fun build(strings: ExportShareStrings, storeUrl: String?): ShareMessage {
        val text = buildString {
            append(strings.intro)
            if (storeUrl != null) {
                append("\n\n")
                append(strings.getApp(storeUrl))
            }
            append("\n\n")
            append(strings.stepsTitle)
            strings.steps.forEach { append('\n').append(it) }
        }
        return ShareMessage(strings.subject, text)
    }
}
