package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

class RedirectionManager(private val context: Context) {
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences("redirections", Context.MODE_PRIVATE)

    fun setRedirection(sourcePackage: String, targetPackage: String) {
        sharedPrefs.edit()
            .putString(sourcePackage, targetPackage)
            .apply()
    }

    fun getRedirection(sourcePackage: String): String? {
        return sharedPrefs.getString(sourcePackage, null)
    }

    fun removeRedirection(sourcePackage: String) {
        sharedPrefs.edit()
            .remove(sourcePackage)
            .apply()
    }

    fun getAllRedirections(): Map<String, String> {
        return sharedPrefs.all.filterValues { it is String }
            .mapValues { it.value as String }
    }

    fun hasRedirection(sourcePackage: String): Boolean {
        return sharedPrefs.contains(sourcePackage)
    }
} 