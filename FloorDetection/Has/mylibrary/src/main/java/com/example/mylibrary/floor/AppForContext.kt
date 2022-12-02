package com.example.mylibrary.floor

import android.app.Application
import android.content.Context

class AppforContext: Application() {
    lateinit var context: Context
    init{
        instance = this
    }

    companion object {
        lateinit var instance: AppforContext
        fun myappContext() : Context {
            return instance.applicationContext
        }
    }
}