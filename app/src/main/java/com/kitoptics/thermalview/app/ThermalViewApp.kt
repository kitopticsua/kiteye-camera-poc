package com.kitoptics.thermalview.app

import android.app.Application

class ThermalViewApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Sentry init will be added when DSN is configured
    }
}
