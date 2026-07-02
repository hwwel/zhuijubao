package com.tv.zhuiju

import android.app.Application
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.local.db.DatabaseProvider
import com.tv.zhuiju.utils.SettingsManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseProvider.init(this)
        SettingsManager.init(this)
        YouthModeManager.init(this)
    }
}
