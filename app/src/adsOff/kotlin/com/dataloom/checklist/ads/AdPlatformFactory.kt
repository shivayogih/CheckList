package com.dataloom.checklist.ads

import android.content.Context

/** Ads are switched off for this flavor (checklist.ads.<flavor>=false): no SDK, no slots, no space. */
@Suppress("UNUSED_PARAMETER", "UnusedParameter")
fun createAdPlatform(context: Context): AdPlatform = AdPlatform.None
