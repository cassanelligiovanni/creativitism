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
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appListManager: AppListManager
    private lateinit var redirectionManager: RedirectionManager

    private lateinit var soulSuckingAdapter: ManagedItemsAdapter
    private lateinit var creativityBoostingAdapter: ManagedItemsAdapter
    private lateinit var policyStore: BlockedItemPolicyStore
    private lateinit var allowanceManager: TemporaryAllowanceManager
    private lateinit var visitTracker: VisitTracker
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var scheduleStore: ScheduleStore
    private lateinit var interventionLog: InterventionLog

    private var allApps: List<AppInfo> = emptyList()
    private var searchQuery: String = ""
    private var activeHours: ActiveHours = ActiveHours(
        enabled = false,
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 18 * 60,
        weekdaysBitmask = 0,
    )
    private var pendingReceiptWeekId: String? = null
    private var pendingReceiptCopy: String? = null

    // True while updatePermissionUI() sets the switch programmatically, so the
    // change listener only reacts to real user taps.
    private var updatingSwitchFromCode = false
    private var updatingScheduleFromCode = false

    private val scheduleStatusHandler = Handler(Looper.getMainLooper())
    private val scheduleStatusRunnable = object : Runnable {
        override fun run() {
            updateScheduleStatus()
            scheduleStatusHandler.postDelayed(this, 30_000L)
        }
    }

    private val weekdayChips: List<Pair<Chip, Int>> by lazy {
        listOf(
            binding.chipMon to 0,
            binding.chipTue to 1,
            binding.chipWed to 2,
            binding.chipThu to 3,
            binding.chipFri to 4,
            binding.chipSat to 5,
            binding.chipSun to 6,
        )
    }

    private val appStatePrefs by lazy {
        getSharedPreferences("app_state", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appListManager = AppListManager(this)
        redirectionManager = RedirectionManager(this)
        policyStore = BlockedItemPolicyStore(this)
        allowanceManager = TemporaryAllowanceManager(this)
        visitTracker = VisitTracker(this)
        settingsStore = AppSettingsStore(this)
        scheduleStore = ScheduleStore(this)
        interventionLog = InterventionLog(this)
        activeHours = scheduleStore.load()

        setupUI()
        loadAllData()
    }

    override fun onPause() {
        super.onPause()
        scheduleStatusHandler.removeCallbacks(scheduleStatusRunnable)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
        refreshActiveHoursUI()
        refreshStatsAndReceipt()
        scheduleStatusHandler.removeCallbacks(scheduleStatusRunnable)
        scheduleStatusHandler.post(scheduleStatusRunnable)
    }

    private fun setupUI() {
        binding.soulSuckingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.creativityBoostingRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.allAppsRecyclerView.layoutManager = LinearLayoutManager(this)

        soulSuckingAdapter = ManagedItemsAdapter(
            items = emptyList(),
            rowMode = ManagedItemsAdapter.RowMode.BLOCKED,
            onRemoveClicked = { item ->
                redirectionManager.removeSoulSuckingItem(item.id)
                policyStore.remove(item.id)
                allowanceManager.clear(item.id)
                visitTracker.endVisit(item.id)
                loadAllData()
            },
            onPolicyChanged = { id, policy -> policyStore.set(id, policy) },
            getPolicy = { id -> policyStore.get(id) },
        )
        binding.soulSuckingRecyclerView.adapter = soulSuckingAdapter

        creativityBoostingAdapter = ManagedItemsAdapter(
            items = emptyList(),
            rowMode = ManagedItemsAdapter.RowMode.CREATIVE,
            onRemoveClicked = { item ->
                redirectionManager.removeCreativityBoostingItem(item.id)
                loadAllData()
            },
        )
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
        binding.resumeLastWorkSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitchFromCode) return@setOnCheckedChangeListener
            settingsStore.resumeLastWork = isChecked
        }
        binding.receiptShareButton.setOnClickListener { shareWeeklyReceipt() }
        binding.receiptDismissButton.setOnClickListener { dismissWeeklyReceipt() }
        setupActiveHoursUI()
    }

    private fun setupActiveHoursUI() {
        binding.activeHoursSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingScheduleFromCode) return@setOnCheckedChangeListener
            activeHours = activeHours.copy(enabled = isChecked)
            persistActiveHours()
            refreshActiveHoursUI()
        }
        binding.startTimeButton.setOnClickListener { showTimePicker(isStart = true) }
        binding.endTimeButton.setOnClickListener { showTimePicker(isStart = false) }
        weekdayChips.forEach { (chip, _) ->
            chip.setOnCheckedChangeListener { _, _ -> onWeekdaySelectionChanged() }
        }
        refreshActiveHoursUI()
    }

    private fun showTimePicker(isStart: Boolean) {
        val minute = if (isStart) activeHours.startMinuteOfDay else activeHours.endMinuteOfDay
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minute / 60)
            .setMinute(minute % 60)
            .setTitleText(
                getString(if (isStart) R.string.active_hours_start else R.string.active_hours_end),
            )
            .build()
        picker.addOnPositiveButtonClickListener {
            val newMinute = picker.hour * 60 + picker.minute
            activeHours = if (isStart) {
                activeHours.copy(startMinuteOfDay = newMinute)
            } else {
                activeHours.copy(endMinuteOfDay = newMinute)
            }
            persistActiveHours()
            refreshActiveHoursUI()
        }
        picker.show(supportFragmentManager, if (isStart) "start_time_picker" else "end_time_picker")
    }

    private fun onWeekdaySelectionChanged() {
        if (updatingScheduleFromCode) return
        val selected = weekdayChips.filter { it.first.isChecked }.map { it.second }.toSet()
        activeHours = activeHours.copy(
            weekdaysBitmask = scheduleStore.weekdayBitmaskFromSelection(selected),
        )
        persistActiveHours()
        updateScheduleStatus()
    }

    private fun persistActiveHours() {
        scheduleStore.save(activeHours)
    }

    private fun refreshActiveHoursUI() {
        updatingScheduleFromCode = true
        binding.activeHoursSwitch.isChecked = activeHours.enabled
        binding.startTimeButton.text = getString(
            R.string.active_hours_start,
        ) + " · " + SchedulePolicy.formatMinuteOfDay(activeHours.startMinuteOfDay)
        binding.endTimeButton.text = getString(
            R.string.active_hours_end,
        ) + " · " + SchedulePolicy.formatMinuteOfDay(activeHours.endMinuteOfDay)
        syncWeekdayChips(activeHours.weekdaysBitmask)
        updatingScheduleFromCode = false
        updateScheduleStatus()
    }

    private fun syncWeekdayChips(bitmask: Int) {
        val selected = scheduleStore.selectedWeekdaysMonday0(bitmask)
        weekdayChips.forEach { (chip, day) ->
            chip.isChecked = selected.contains(day)
        }
    }

    private fun updateScheduleStatus() {
        if (!activeHours.enabled) {
            binding.scheduleStatusText.visibility = android.view.View.GONE
            return
        }
        binding.scheduleStatusText.visibility = android.view.View.VISIBLE
        val now = LocalDateTime.now()
        val minuteOfDay = now.hour * 60 + now.minute
        val weekdayMonday0 = now.dayOfWeek.value - 1
        val armed = SchedulePolicy.isArmed(activeHours, minuteOfDay, weekdayMonday0)
        binding.scheduleStatusText.text = if (armed) {
            getString(R.string.schedule_status_armed)
        } else {
            val resume = SchedulePolicy.nextResumeLabel(activeHours, minuteOfDay, weekdayMonday0)
            getString(R.string.schedule_status_dormant, resume ?: "--:--")
        }
    }

    private fun refreshStatsAndReceipt() {
        val today = LocalDate.now()
        val events = interventionLog.all()
        val snapshot = StatsAggregator.aggregate(events, today)
        bindStats(snapshot, today)
        maybeShowWeeklyReceipt(events, today)
    }

    private fun bindStats(snapshot: StatsSnapshot, today: LocalDate) {
        binding.statsReclaimedText.text = formatStatsDuration(snapshot.reclaimedMillis)
        binding.statsBoughtText.text = formatStatsDuration(snapshot.boughtMillis)
        val alarmsToday = snapshot.alarmsByDay[today.toString()] ?: 0
        binding.statsAlarmsTodayText.text = getString(R.string.stats_alarms_count, alarmsToday)
        binding.statsStreakText.text =
            getString(R.string.stats_streak_days, snapshot.giveInFreeStreakDays)
        binding.statsOffendersText.text = if (snapshot.worstOffenders.isEmpty()) {
            getString(R.string.stats_offenders_empty)
        } else {
            snapshot.worstOffenders.take(5).joinToString("\n") { (label, count) ->
                getString(R.string.stats_offender_row, label, count)
            }
        }
    }

    private fun formatStatsDuration(millis: Long): String =
        if (millis <= 0L) {
            getString(R.string.stats_duration_zero)
        } else {
            WeeklyReceiptHelper.formatDuration(millis)
        }

    private fun maybeShowWeeklyReceipt(events: List<InterventionEvent>, today: LocalDate) {
        val previousWeekId = WeeklyReceiptHelper.previousWeekId(today)
        val priorWeekEvents = WeeklyReceiptHelper.eventsInWeek(events, previousWeekId)
        if (!WeeklyReceiptHelper.shouldShowReceipt(
                settingsStore.lastReceiptWeekId,
                previousWeekId,
                priorWeekEvents,
            )
        ) {
            binding.receiptCard.visibility = android.view.View.GONE
            pendingReceiptWeekId = null
            pendingReceiptCopy = null
            return
        }
        val priorSnapshot = StatsAggregator.aggregate(priorWeekEvents, today.minusWeeks(1))
        val copy = WeeklyReceiptHelper.receiptCopy(priorSnapshot.reclaimedMillis)
        pendingReceiptWeekId = previousWeekId
        pendingReceiptCopy = copy
        binding.receiptCopyText.text = copy
        binding.receiptCard.visibility = android.view.View.VISIBLE
    }

    private fun shareWeeklyReceipt() {
        val copy = pendingReceiptCopy ?: return
        val intent = WeeklyReceiptHelper.shareReceiptIntent(this, copy)
        startActivity(Intent.createChooser(intent, getString(R.string.receipt_share_chooser)))
        // Mark shown after explicit share; do not auto-share.
        dismissWeeklyReceipt()
    }

    private fun dismissWeeklyReceipt() {
        val weekId = pendingReceiptWeekId
        if (weekId != null) {
            settingsStore.lastReceiptWeekId = weekId
        }
        binding.receiptCard.visibility = android.view.View.GONE
        pendingReceiptWeekId = null
        pendingReceiptCopy = null
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
        binding.resumeLastWorkSwitch.isChecked = settingsStore.resumeLastWork
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
