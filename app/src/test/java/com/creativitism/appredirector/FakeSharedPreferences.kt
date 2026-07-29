package com.creativitism.appredirector

import android.content.SharedPreferences

/** Minimal in-memory [SharedPreferences] for JVM unit tests. */
class FakeSharedPreferences : SharedPreferences {

    private val data = mutableMapOf<String, Any>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(key: String, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        unsupported()

    override fun getInt(key: String, defValue: Int): Int = unsupported()

    override fun getLong(key: String, defValue: Long): Long =
        data[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = unsupported()

    override fun getBoolean(key: String, defValue: Boolean): Boolean = unsupported()

    override fun contains(key: String): Boolean = key in data

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = unsupported()

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = unsupported()

    private inner class Editor : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key)
            return this
        }

        override fun apply() {
            removals.forEach { data.remove(it) }
            pending.forEach { (key, value) ->
                if (value != null) {
                    data[key] = value
                }
            }
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = unsupported()

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = unsupported()

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = unsupported()

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = unsupported()

        override fun clear(): SharedPreferences.Editor = unsupported()
    }

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Not implemented in FakeSharedPreferences")
}
