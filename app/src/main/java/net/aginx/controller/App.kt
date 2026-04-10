package net.aginx.controller

import android.app.Application
import net.aginx.controller.db.AppDatabase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)

        // 加载 aginxium native library（UniFFI 生成的 Kotlin 代码通过 JNA 自动加载，
        // 但需要确保 .so 文件在正确的 jniLibs 路径下）
        System.loadLibrary("aginxium")
    }
}
