package com.kline.training

import android.app.Application
import com.kline.training.data.DataManager

/**
 * Application类
 */
class KlineTrainingApp : Application() {

    lateinit var dataManager: DataManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        dataManager = DataManager(this)
    }

    companion object {
        @JvmStatic
        lateinit var instance: KlineTrainingApp
            private set
    }
}
