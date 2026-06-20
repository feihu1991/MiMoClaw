package com.xiaomi.mimoclaw

import android.app.Application
import android.webkit.CookieManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MiMoClawApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // API 21+ 的 WebView CookieManager 自动持久化, 无需 CookieSyncManager
        CookieManager.getInstance().setAcceptCookie(true)
    }
}
