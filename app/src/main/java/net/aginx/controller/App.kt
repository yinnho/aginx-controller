package net.aginx.controller

import android.app.Application
import net.aginx.controller.db.AppDatabase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
    }
}
