package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

class RedirectionManager(private val context: Context) {
    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("redirections_v2", Context.MODE_PRIVATE)

    companion object {
        private const val SOUL_SUCKING_KEY = "soul_sucking_items"
        private const val CREATIVITY_BOOSTING_KEY = "creativity_boosting_items"
    }

    // --- Soul-Sucking List Management ---

    fun getSoulSuckingItems(): Set<String> {
        return sharedPrefs.getStringSet(SOUL_SUCKING_KEY, emptySet()) ?: emptySet()
    }

    fun addSoulSuckingItem(item: String) {
        val items = getSoulSuckingItems().toMutableSet()
        items.add(item)
        sharedPrefs.edit().putStringSet(SOUL_SUCKING_KEY, items).apply()
    }

    fun removeSoulSuckingItem(item: String) {
        val items = getSoulSuckingItems().toMutableSet()
        items.remove(item)
        sharedPrefs.edit().putStringSet(SOUL_SUCKING_KEY, items).apply()
    }

    fun isSoulSucking(item: String): Boolean {
        return BlockMatcher.matches(item, getSoulSuckingItems())
    }

    // --- Creativity-Boosting List Management ---

    fun getCreativityBoostingItems(): Set<String> {
        return sharedPrefs.getStringSet(CREATIVITY_BOOSTING_KEY, emptySet()) ?: emptySet()
    }

    fun addCreativityBoostingItem(item: String) {
        val items = getCreativityBoostingItems().toMutableSet()
        items.add(item)
        sharedPrefs.edit().putStringSet(CREATIVITY_BOOSTING_KEY, items).apply()
    }

    fun removeCreativityBoostingItem(item: String) {
        val items = getCreativityBoostingItems().toMutableSet()
        items.remove(item)
        sharedPrefs.edit().putStringSet(CREATIVITY_BOOSTING_KEY, items).apply()
    }

    fun getRandomCreativityBoostingItem(): String? {
        val items = getCreativityBoostingItems()
        return if (items.isNotEmpty()) items.random() else null
    }
} 