package com.creativitism.appredirector

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativitism.appredirector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appListManager: AppListManager
    private lateinit var redirectionManager: RedirectionManager

    private lateinit var soulSuckingAdapter: ManagedItemsAdapter
    private lateinit var creativityBoostingAdapter: ManagedItemsAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var searchQuery: String = ""

    // True while updatePermissionUI() sets the switch programmatically, so the
    // change listener only reacts to real user taps.
    private var updatingSwitchFromCode = false

    private val appStatePrefs by lazy {
        getSharedPreferences("app_state", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appListManager = AppListManager(this)
        redirectionManager = RedirectionManager(this)

        setupUI()
        loadAllData()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    private fun setupUI() {
        binding.soulSuckingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.creativityBoostingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.allAppsRecyclerView.layoutManager = LinearLayoutManager(this)

        soulSuckingAdapter = ManagedItemsAdapter(emptyList()) { item ->
            redirectionManager.removeSoulSuckingItem(item.id)
            loadAllData()
        }
        binding.soulSuckingRecyclerView.adapter = soulSuckingAdapter

        creativityBoostingAdapter = ManagedItemsAdapter(emptyList()) { item ->
            redirectionManager.removeCreativityBoostingItem(item.id)
            loadAllData()
        }
        binding.creativityBoostingRecyclerView.adapter = creativityBoostingAdapter

        binding.searchInput.doAfterTextChanged { text ->
            searchQuery = text?.toString()?.trim().orEmpty()
            refreshAllAppsList()
        }

        binding.addWebsiteButton.setOnClickListener { showAddWebsiteDialog(isSoulSucking = true) }
        binding.addCreativeWebsiteButton.setOnClickListener { showAddWebsiteDialog(isSoulSucking = false) }
        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitchFromCode) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    requestUsageStatsPermission()
                    binding.serviceSwitch.isChecked = false
                } else if (!Settings.canDrawOverlays(this)) {
                    requestDrawOverlayPermission()
                    binding.serviceSwitch.isChecked = false
                } else {
                    checkNotificationPermsAndStartService()
                }
            } else {
                stopRedirectorService()
            }
        }
        binding.accessibilityPermissionButton.setOnClickListener {
            showAccessibilityDisclosure()
        }
    }

    private fun loadAllData() {
        allApps = appListManager.getInstalledApps()
        val soulSuckingIds = redirectionManager.getSoulSuckingItems()
        val creativeIds = redirectionManager.getCreativityBoostingItems()

        refreshAllAppsList()

        val soulSuckingItems = soulSuckingIds.map { id -> toManagedItem(id) }
            .sortedBy { it.displayName.lowercase() }
        soulSuckingAdapter.updateData(soulSuckingItems)
        binding.emptyBlockedText.visibility =
            if (soulSuckingItems.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val creativeItems = creativeIds.map { id -> toManagedItem(id) }
            .sortedBy { it.displayName.lowercase() }
        creativityBoostingAdapter.updateData(creativeItems)
        binding.emptyCreativeText.visibility =
            if (creativeItems.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toManagedItem(id: String): ManagedItem {
        val app = allApps.find { it.packageName == id }
        return if (app != null) {
            ManagedItem(id, app.appName, app.icon, true)
        } else { // It's a website
            val icon = ContextCompat.getDrawable(this, R.drawable.ic_globe)!!
            ManagedItem(id, id, icon, false)
        }
    }

    private fun refreshAllAppsList() {
        val soulSuckingIds = redirectionManager.getSoulSuckingItems()
        val creativeIds = redirectionManager.getCreativityBoostingItems()
        val availableApps = allApps
            .filter { !soulSuckingIds.contains(it.packageName) && !creativeIds.contains(it.packageName) }
            .filter { searchQuery.isEmpty() || it.appName.contains(searchQuery, ignoreCase = true) }

        binding.allAppsRecyclerView.adapter = AllAppsAdapter(availableApps,
            onAddToCreative = { app ->
                redirectionManager.addCreativityBoostingItem(app.packageName)
                loadAllData()
            },
            onAddToSoulSucking = { app ->
                redirectionManager.addSoulSuckingItem(app.packageName)
                loadAllData()
            }
        )
    }

    private fun showAddWebsiteDialog(isSoulSucking: Boolean) {
        val editText = EditText(this).apply {
            hint = "example.com"
        }
        val title = if (isSoulSucking) {
            getString(R.string.add_blocked_website_title)
        } else {
            getString(R.string.add_creative_website_title)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(R.string.add_website_dialog_message)
            .setView(editText)
            .setPositiveButton(R.string.add) { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (isSoulSucking) {
                        redirectionManager.addSoulSuckingItem(text)
                    } else {
                        redirectionManager.addCreativityBoostingItem(text)
                    }
                    loadAllData()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Permissions ---

    private fun updatePermissionUI() {
        val hasAllAppPerms = hasUsageStatsPermission() &&
                Settings.canDrawOverlays(this) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val serviceEnabled = hasAllAppPerms && appStatePrefs.getBoolean("service_enabled", false)
        updatingSwitchFromCode = true
        binding.serviceSwitch.isChecked = serviceEnabled
        updatingSwitchFromCode = false
        binding.statusText.text = getString(
            if (serviceEnabled) R.string.protection_on else R.string.protection_off
        )

        if (isAccessibilityServiceEnabled()) {
            binding.accessibilityPermissionButton.text = getString(R.string.website_blocking_active)
            binding.accessibilityPermissionButton.isEnabled = false
        } else {
            binding.accessibilityPermissionButton.text = getString(R.string.enable_website_blocking)
            binding.accessibilityPermissionButton.isEnabled = true
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_usage_title)
            .setMessage(R.string.permission_description)
            .setPositiveButton(R.string.grant) { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestDrawOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_overlay_title)
            .setMessage(R.string.permission_overlay_description)
            .setPositiveButton(R.string.grant) { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startRedirectorService()
            } else {
                Toast.makeText(this, R.string.permission_notifications_needed, Toast.LENGTH_LONG).show()
            }
            updatePermissionUI()
        }

    private fun checkNotificationPermsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startRedirectorService()
        }
    }

    private fun startRedirectorService() {
        // Request to disable battery optimization for better reliability
        requestBatteryOptimizationExemption()

        appStatePrefs.edit().putBoolean("service_enabled", true).apply()

        val intent = Intent(this, RedirectorService::class.java)
        startForegroundService(intent)

        // Schedule periodic checks to keep the service alive
        ServiceKeepAliveManager.schedulePeriodicCheck(this)

        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
        updatePermissionUI()
    }

    private fun stopRedirectorService() {
        appStatePrefs.edit().putBoolean("service_enabled", false).apply()

        ServiceKeepAliveManager.cancelPeriodicCheck(this)

        val intent = Intent(this, RedirectorService::class.java)
        stopService(intent)

        Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
        updatePermissionUI()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.name == UrlInterceptorService::class.java.name }
    }

    // Prominent disclosure required by Play's AccessibilityService API policy: shown
    // before sending the user to Accessibility settings, states what is read and why.
    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure)
            .setPositiveButton(R.string.accessibility_disclosure_accept) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, R.string.accessibility_find_hint, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.battery_dialog_title)
                .setMessage(R.string.battery_dialog_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general battery optimization settings
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                .setNegativeButton(R.string.skip, null)
                .show()
        }
    }
}
