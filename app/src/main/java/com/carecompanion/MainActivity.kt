package com.carecompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.carecompanion.presentation.navigation.AppNavHost
import com.carecompanion.presentation.ui.theme.CareCompanionTheme
import com.carecompanion.presentation.viewmodels.SharedViewModel
import com.carecompanion.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.INTERNET,Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CAMERA,Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.VIBRATE,Manifest.permission.FOREGROUND_SERVICE)
        } else {
            arrayOf(Manifest.permission.INTERNET,Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CAMERA,Manifest.permission.VIBRATE)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
        setContent {
            CareCompanionTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val sharedViewModel: SharedViewModel = viewModel()
                    AppNavHost(navController = navController, sharedViewModel = sharedViewModel)
                }
            }
        }
    }
    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.zip(grantResults.toList()).filter { it.second != PackageManager.PERMISSION_GRANTED }.map { it.first }
            if (denied.isNotEmpty()) PermissionHelper.showPermissionDeniedDialog(this, denied)
        }
    }
}