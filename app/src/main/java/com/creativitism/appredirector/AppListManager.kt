package com.creativitism.appredirector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

class AppListManager(private val context: Context) {
    
    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        
        return resolveInfos
            .filter { it.activityInfo.packageName != context.packageName } // Exclude our own app
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                AppInfo(
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            }
            .distinctBy { it.packageName } // Remove duplicates
            .sortedBy { it.appName.lowercase() } // Sort alphabetically
    }
    
    fun getAppInfo(packageName: String): AppInfo? {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            AppInfo(
                appName = packageManager.getApplicationLabel(appInfo).toString(),
                packageName = packageName,
                icon = packageManager.getApplicationIcon(appInfo)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
} 