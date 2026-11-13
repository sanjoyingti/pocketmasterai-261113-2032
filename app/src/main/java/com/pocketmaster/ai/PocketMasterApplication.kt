package com.pocketmaster.ai

import android.app.Application

class PocketMasterApplication : Application() {
    val container: AppContainer by lazy { AppContainer.create(this) }
}
