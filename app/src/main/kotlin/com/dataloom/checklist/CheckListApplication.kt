package com.dataloom.checklist

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hosts the app-wide Hilt component (database, repositories). */
@HiltAndroidApp
class CheckListApplication : Application()
