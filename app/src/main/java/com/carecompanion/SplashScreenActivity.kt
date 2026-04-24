package com.carecompanion

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.carecompanion.utils.SharedPreferencesHelper

class SplashScreenActivity : AppCompatActivity() {
    private var keepSplash = true
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplash }
        Handler(Looper.getMainLooper()).postDelayed({ checkFirstLaunchAndNavigate() }, 1500)
    }
    private fun checkFirstLaunchAndNavigate() {
        val intent = if (SharedPreferencesHelper.isFirstLaunch(this)) {
            SharedPreferencesHelper.setFirstLaunchComplete(this)
            Intent(this, SetupActivity::class.java)
        } else Intent(this, MainActivity::class.java)
        keepSplash = false
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}