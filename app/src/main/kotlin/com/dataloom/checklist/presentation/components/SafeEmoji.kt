package com.dataloom.checklist.presentation.components

/**
 * Emoji that every supported phone can draw. The app supports Android 8 (API 26), whose emoji font stops
 * at Unicode Emoji 5.0 (Emoji 11 arrived with Android 9, Emoji 12 with Android 10). A newer emoji such
 * as the diya lamp (Emoji 12), the lotion bottle or the luggage (Emoji 11) shows as an empty box there.
 *
 * Use this list for the emoji picker of "New category" and keep new seed icons inside Emoji 5.0 (Unicode
 * 10 or older). The decision and the numbers are in the CL-260 report: swapping three seed icons was
 * smaller and safer than bundling an emoji font with androidx.emoji2.
 *
 * Every entry is written with escapes so the file does not depend on an editor's emoji support.
 */
object SafeEmoji {
    /** The 12 icons of the "Choose an icon" grid (mockup 07), all drawable on API 26. */
    val picker: List<String> = listOf(
        "🔔", // bell (pooja items)
        "🛒", // shopping cart
        "🎁", // wrapped gift
        "🎉", // party popper
        "💊", // pill
        "🚿", // shower (toiletries)
        "🔌", // electric plug
        "✏️", // pencil
        "📦", // package
        "🍬", // candy
        "🌸", // cherry blossom
        "🏠", // house
    )
}
