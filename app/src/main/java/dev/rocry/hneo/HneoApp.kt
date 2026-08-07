package dev.rocry.hneo

import android.app.Application
import dev.rocry.hneo.di.AppContainer

class HneoApp : Application() {
    /** The composition root. Built once, here, and handed down from [MainActivity]. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
