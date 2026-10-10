package com.dataloom.checklist.ads

import android.content.Context

/** Ads are switched on for this flavor (checklist.ads.<flavor>=true): Google AdMob. */
fun createAdPlatform(context: Context): AdPlatform = AdMobPlatform(context.applicationContext)
