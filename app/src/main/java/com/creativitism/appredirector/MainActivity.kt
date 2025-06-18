package com.creativitism.appredirector

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativitism.appredirector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var appListManager: AppListManager
    private lateinit var redirectionManager: RedirectionManager

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startRedirectorService()
                updateSwitchState()
            } else {
                Toast.makeText(this, "Notification permission is required for the service to run.", Toast.LENGTH_LONG).show()
                binding.serviceSwitch.isChecked = false
            }
        }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        appListManager = AppListManager(this)
        redirectionManager = RedirectionManager(this)
        
        // Setup UI
        setupRecyclerViews()
        setupServiceSwitch()

        // Load data
        loadAppData()
    }

    override fun onResume() {
        super.onResume()
        // Update switch state in case user grants/revokes permission in settings
        updateSwitchState()
    }
    
    private fun setupRecyclerViews() {
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.redirectionsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupServiceSwitch() {
        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    requestUsageStatsPermission()
                    binding.serviceSwitch.isChecked = false
                } else if (!hasDrawOverlayPermission()) {
                    requestDrawOverlayPermission()
                    binding.serviceSwitch.isChecked = false
                } else {
                    checkAndStartService()
                }
            } else {
                stopRedirectorService()
            }
        }
    }
    
    private fun loadAppData() {
        val installedApps = appListManager.getInstalledApps()
        val appsAdapter = AppsAdapter(installedApps) { app ->
            showTargetAppSelectionDialog(app, installedApps)
        }
        binding.appsRecyclerView.adapter = appsAdapter
        loadCurrentRedirections(installedApps)
    }
    
    private fun loadCurrentRedirections(installedApps: List<AppInfo>) {
        val redirectionMap = redirectionManager.getAllRedirections()
        
        val currentRedirections = redirectionMap.mapNotNull { (sourcePackage, targetPackage) ->
            val sourceApp = installedApps.find { it.packageName == sourcePackage }
            val targetApp = installedApps.find { it.packageName == targetPackage }
            
            if (sourceApp != null && targetApp != null) {
                RedirectionInfo(sourceApp, targetApp)
            } else {
                // Clean up invalid redirections (e.g., if an app was uninstalled)
                redirectionManager.removeRedirection(sourcePackage)
                null
            }
        }
        
        val redirectionsAdapter = RedirectionsAdapter(currentRedirections) { redirection ->
            removeRedirection(redirection)
        }
        binding.redirectionsRecyclerView.adapter = redirectionsAdapter
    }
    
    private fun showTargetAppSelectionDialog(sourceApp: AppInfo, allApps: List<AppInfo>) {
        val availableApps = allApps.filter { it.packageName != sourceApp.packageName }
        val appNames = availableApps.map { it.appName }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Redirect ${sourceApp.appName} to...")
            .setItems(appNames) { _, which ->
                val targetApp = availableApps[which]
                setRedirection(sourceApp, targetApp)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun setRedirection(sourceApp: AppInfo, targetApp: AppInfo) {
        redirectionManager.setRedirection(sourceApp.packageName, targetApp.packageName)
        Toast.makeText(this, "Redirecting ${sourceApp.appName} → ${targetApp.appName}", Toast.LENGTH_SHORT).show()
        loadAppData() // Refresh both lists
    }
    
    private fun removeRedirection(redirection: RedirectionInfo) {
        redirectionManager.removeRedirection(redirection.sourceApp.packageName)
        Toast.makeText(this, "Redirection removed", Toast.LENGTH_SHORT).show()
        loadAppData() // Refresh both lists
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permission 1 of 3: Usage Access")
            .setMessage(getString(R.string.permission_description))
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startRedirectorService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show a rationale dialog if needed
                    requestNotificationPermission()
                }
                else -> {
                    // Directly request the permission
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startRedirectorService()
        }
    }

    private fun requestNotificationPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permission 2 of 3: Notifications")
            .setMessage("The redirection service runs in the background and requires showing a persistent notification. Please grant notification permission to allow this.")
            .setPositiveButton("Grant") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.serviceSwitch.isChecked = false
            }
            .show()
    }

    private fun hasDrawOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Not needed for older versions
        }
    }

    private fun requestDrawOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permission 3 of 3: Display Over Other Apps")
            .setMessage("This final permission is required to launch other apps from the background. It allows the redirection to happen seamlessly.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startRedirectorService() {
        val intent = Intent(this, RedirectorService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Redirection service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRedirectorService() {
        val intent = Intent(this, RedirectorService::class.java)
        stopService(intent)
        Toast.makeText(this, "Redirection service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateSwitchState() {
        val hasUsagePerms = hasUsageStatsPermission()
        val hasOverlayPerms = hasDrawOverlayPermission()
        val hasNotificationPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed for older versions
        }

        binding.serviceSwitch.isChecked = hasUsagePerms && hasOverlayPerms && hasNotificationPerms
    }
} 