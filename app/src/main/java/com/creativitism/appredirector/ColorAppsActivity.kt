package com.creativitism.appredirector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativitism.appredirector.databinding.ActivityColorAppsBinding

/**
 * Full-screen picker for which installed apps keep their color while grayscale mode
 * is on. Apps already in [GrayscaleStore.colorApps] sort to the top (alphabetically),
 * then everything else (alphabetically). Tapping a row toggles membership immediately
 * and persists via the store — the list is deliberately NOT re-sorted on every tap so
 * rows don't jump out from under the user's finger; re-sorting only happens on initial
 * load, search changes, and onResume.
 */
class ColorAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColorAppsBinding
    private lateinit var appListManager: AppListManager
    private lateinit var grayscaleStore: GrayscaleStore
    private lateinit var adapter: ColorAppsAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var appsLoaded = false
    private var loadingApps = false
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityColorAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        appListManager = AppListManager(this)
        grayscaleStore = GrayscaleStore(this)

        binding.colorAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ColorAppsAdapter(
            initialSelected = grayscaleStore.colorApps(),
            onToggle = { app, isNowColor ->
                if (isNowColor) {
                    grayscaleStore.addColorApp(app.packageName)
                } else {
                    grayscaleStore.removeColorApp(app.packageName)
                }
            },
        )
        binding.colorAppsRecyclerView.adapter = adapter

        binding.searchInput.doAfterTextChanged { text ->
            searchQuery = text?.toString()?.trim().orEmpty()
            refreshSortedList()
        }

        ensureAppsLoaded()
    }

    override fun onResume() {
        super.onResume()
        if (appsLoaded) {
            refreshSortedList()
        }
    }

    /**
     * Package-manager enumeration + icon decode is expensive. Do the work off the main
     * thread, same pattern as MainActivity.ensureAllAppsLoaded, so opening this screen
     * stays snappy.
     */
    private fun ensureAppsLoaded() {
        if (appsLoaded || loadingApps) return
        loadingApps = true
        Thread {
            val apps = appListManager.getInstalledApps()
            runOnUiThread {
                allApps = apps
                appsLoaded = true
                loadingApps = false
                refreshSortedList()
            }
        }.start()
    }

    private fun refreshSortedList() {
        if (!appsLoaded) return
        val filtered = allApps.filter {
            searchQuery.isEmpty() || it.appName.contains(searchQuery, ignoreCase = true)
        }
        val sorted = filtered.sortedWith(
            compareByDescending<AppInfo> { adapter.isSelected(it.packageName) }
                .thenBy { it.appName.lowercase() },
        )
        adapter.submitList(sorted)
    }
}
