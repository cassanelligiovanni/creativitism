package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

class AppSettingsStore(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    var resumeLastWork: Boolean
        get() = prefs.getBoolean(KEY_RESUME_LAST_WORK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RESUME_LAST_WORK, value).apply()
        }

    var lastCreativeItem: String?
        get() = prefs.getString(KEY_LAST_CREATIVE_ITEM, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_LAST_CREATIVE_ITEM)
                } else {
                    putString(KEY_LAST_CREATIVE_ITEM, value)
                }
            }.apply()
        }

    var lastReceiptWeekId: String?
        get() = prefs.getString(KEY_LAST_RECEIPT_WEEK_ID, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_LAST_RECEIPT_WEEK_ID)
                } else {
                    putString(KEY_LAST_RECEIPT_WEEK_ID, value)
                }
            }.apply()
        }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_RESUME_LAST_WORK = "resume_last_work"
        private const val KEY_LAST_CREATIVE_ITEM = "last_creative_item"
        private const val KEY_LAST_RECEIPT_WEEK_ID = "last_receipt_week_id"
    }
}
