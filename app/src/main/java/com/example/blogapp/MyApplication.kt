package com.example.blogapp

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Cloudinary
        val config = HashMap<String, String>()
        config["cloud_name"] = BuildConfig.CLOUDINARY_CLOUD_NAME
        config["api_key"] = BuildConfig.CLOUDINARY_API_KEY
        config["api_secret"] = BuildConfig.CLOUDINARY_API_SECRET
        
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // MediaManager might already be initialized in some cases
            e.printStackTrace()
        }
    }
}
