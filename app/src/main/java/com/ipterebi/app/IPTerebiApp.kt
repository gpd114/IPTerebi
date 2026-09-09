package com.ipterebi.app

import android.app.Application

class IPTerebiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
