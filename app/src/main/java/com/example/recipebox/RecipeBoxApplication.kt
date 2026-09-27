package com.example.recipebox

import android.app.Application

class RecipeBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}
