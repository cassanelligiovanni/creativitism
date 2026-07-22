package com.creativitism.appredirector

import android.graphics.drawable.Drawable

data class ManagedItem(
    val id: String, // Package name or domain
    val displayName: String,
    val icon: Drawable,
    val isApp: Boolean
) 