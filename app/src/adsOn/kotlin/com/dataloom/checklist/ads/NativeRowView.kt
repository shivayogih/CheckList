package com.dataloom.checklist.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/** Colours and the "Ad" label for a native row, taken from the Compose theme. */
internal data class NativeRowStyle(
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val label: String,
)

/**
 * A native ad drawn like a catalogue row: icon, "Ad" badge with the headline, one line of body text and
 * the call-to-action button. AdMob needs a [NativeAdView] with registered asset views, so this is plain
 * Android views hosted by Compose. [root] carries this object as its tag for AndroidView's update.
 */
internal class NativeRowView(context: Context) {

    val root = NativeAdView(context)
    private val density = context.resources.displayMetrics.density
    private val icon = ImageView(context)
    private val badge = TextView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val action = Button(context)

    init {
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_SP)
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.setPadding(dp(BADGE_PAD_H), dp(BADGE_PAD_V), dp(BADGE_PAD_H), dp(BADGE_PAD_V))
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADLINE_SP)
        headline.maxLines = 1
        headline.setPadding(dp(BADGE_PAD_H), 0, 0, 0)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
        body.maxLines = 1
        action.isAllCaps = false

        val titleLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge)
            addView(headline)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(GAP), 0, dp(GAP), 0)
            addView(titleLine)
            addView(body)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_MIN_HEIGHT)
            setPadding(dp(EDGE), dp(GAP / 2), dp(EDGE), dp(GAP / 2))
            addView(icon, LinearLayout.LayoutParams(dp(ICON), dp(ICON)))
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(action, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        root.addView(row)
        root.iconView = icon
        root.headlineView = headline
        root.bodyView = body
        root.callToActionView = action
        root.tag = this
    }

    fun bind(ad: NativeAd, style: NativeRowStyle) {
        badge.text = style.label
        badge.setTextColor(style.onAccent)
        badge.background = GradientDrawable().apply {
            cornerRadius = dp(BADGE_RADIUS).toFloat()
            setColor(style.accent)
        }
        headline.text = ad.headline
        headline.setTextColor(style.text)
        body.text = ad.body
        body.setTextColor(style.secondaryText)
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) View.GONE else View.VISIBLE
        action.text = ad.callToAction
        action.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        root.setNativeAd(ad)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val EDGE = 16
        const val GAP = 12
        const val ICON = 40
        const val ROW_MIN_HEIGHT = 56
        const val BADGE_PAD_H = 6
        const val BADGE_PAD_V = 2
        const val BADGE_RADIUS = 4
        const val BADGE_SP = 12f
        const val HEADLINE_SP = 16f
        const val BODY_SP = 14f
    }
}
