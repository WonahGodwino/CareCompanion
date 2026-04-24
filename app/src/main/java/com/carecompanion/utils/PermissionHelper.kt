package com.carecompanion.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PermissionHelper {
    fun showPermissionDeniedDialog(context: Context, denied: List<String>) {
        AlertDialog.Builder(context)
            .setTitle("Permissions Required")
            .setMessage("Required permissions:\n\n${denied.joinToString("\n") { "• $it" }}\n\nPlease grant them in Settings.")
            .setPositiveButton("Open Settings") { _,_ ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") })
            }
            .setNegativeButton("Cancel", null).show()
    }
}