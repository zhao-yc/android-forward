package com.zhaoyuchen.androidforward

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import com.zhaoyuchen.androidforward.appfilter.AppCandidate
import com.zhaoyuchen.androidforward.appfilter.AppPickerCatalog
import com.zhaoyuchen.androidforward.appfilter.AppPickerSections
import com.zhaoyuchen.androidforward.appfilter.InstalledAppRepository
import com.zhaoyuchen.androidforward.bluetooth.BluetoothDeviceInfo
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.data.AppSettings
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.data.ForwardLogItem
import com.zhaoyuchen.androidforward.data.ForwardLogRepository
import com.zhaoyuchen.androidforward.forward.ForwardDispatcher
import com.zhaoyuchen.androidforward.receiver.BluetoothConnectionReceiver
import com.zhaoyuchen.androidforward.service.KeepAliveService
import com.zhaoyuchen.androidforward.service.PhoneMonitorService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页入口。所有操作都即时写入本地配置，方便系统监听服务读取最新状态。
 */
class MainActivity : AppCompatActivity() {
    private val foregroundBluetoothReceiver = BluetoothConnectionReceiver()
    private var foregroundBluetoothReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AndroidForwardScreen(
                    openNotificationSettings = ::openNotificationSettings,
                    openBatterySettings = ::openBatterySettings,
                    onPhoneEnabledChanged = { enabled ->
                        if (enabled) PhoneMonitorService.startIfNeeded(this) else PhoneMonitorService.stop(this)
                    },
                    onKeepAliveNotificationChanged = { enabled ->
                        if (enabled) KeepAliveService.startIfNeeded(this) else KeepAliveService.stop(this)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerForegroundBluetoothReceiver()
    }

    override fun onResume() {
        super.onResume()
        // 厂商系统可能漏发后台广播，返回设置页时主动校准一次连接快照。
        BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(this)
    }

    override fun onStop() {
        unregisterForegroundBluetoothReceiver()
        super.onStop()
    }

    /** 页面可见时动态监听蓝牙广播，提升厂商系统上的断开事件到达率。 */
    private fun registerForegroundBluetoothReceiver() {
        if (foregroundBluetoothReceiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                this,
                foregroundBluetoothReceiver,
                BluetoothConnectionReceiver.intentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            )
            foregroundBluetoothReceiverRegistered = true
        }
    }

    /** 页面不可见时注销动态接收器，避免重复接收和持有 Activity。 */
    private fun unregisterForegroundBluetoothReceiver() {
        if (!foregroundBluetoothReceiverRegistered) return
        runCatching { unregisterReceiver(foregroundBluetoothReceiver) }
        foregroundBluetoothReceiverRegistered = false
    }

    /** 打开系统通知使用权页面，用户需要手动允许本应用。 */
    private fun openNotificationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    /** 请求忽略电池优化；不同厂商可能会跳到不同系统页面。 */
    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(PowerManager::class.java)
        val packageUri = Uri.parse("package:$packageName")
        val intent = if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        runCatching { startActivity(intent) }
    }
}

@Composable
private fun AndroidForwardScreen(
    openNotificationSettings: () -> Unit,
    openBatterySettings: () -> Unit,
    onPhoneEnabledChanged: (Boolean) -> Unit,
    onKeepAliveNotificationChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { AppSettingsRepository(context) }
    val logRepository = remember { ForwardLogRepository(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var barkKey by remember { mutableStateOf(settingsRepository.getBarkKey()) }
    var bluetoothDevices by remember {
        mutableStateOf(BluetoothSilenceManager.listBondedDevices(context))
    }
    var bluetoothRefreshing by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(logRepository.list()) }
    var statusText by remember { mutableStateOf(context.getString(R.string.status_waiting_configuration)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    /** 在后台线程完整查询蓝牙 Profile，完成后再刷新 Compose 列表。 */
    fun refreshBluetoothDevices() {
        if (bluetoothRefreshing) return
        bluetoothRefreshing = true
        statusText = context.getString(R.string.status_refreshing_bluetooth)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BluetoothSilenceManager.refreshAndListBondedDevices(context) }
            }
            result.onSuccess { devices ->
                bluetoothDevices = devices
                val connectedCount = devices.count(BluetoothDeviceInfo::connected)
                statusText = context.resources.getQuantityString(
                    R.plurals.status_bluetooth_refreshed,
                    connectedCount,
                    connectedCount
                )
            }.onFailure { error ->
                statusText = context.getString(
                    R.string.status_bluetooth_refresh_failed,
                    error.message ?: context.getString(R.string.unknown_error)
                )
            }
            bluetoothRefreshing = false
        }
    }

    /** 后台连接缓存变化后读取最新快照，并自动更新界面显示。 */
    fun updateBluetoothDevicesFromCache() {
        scope.launch {
            val devices = withContext(Dispatchers.IO) {
                BluetoothSilenceManager.listBondedDevices(context)
            }
            bluetoothDevices = devices
            val connectedCount = devices.count(BluetoothDeviceInfo::connected)
            statusText = context.resources.getQuantityString(
                R.plurals.status_bluetooth_auto_updated,
                connectedCount,
                connectedCount
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        statusText = context.getString(R.string.status_permissions_updated)
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (BluetoothSilenceManager.hasBluetoothPermission(context)) {
            refreshBluetoothDevices()
        } else {
            bluetoothDevices = emptyList()
            statusText = context.getString(R.string.status_bluetooth_permission_denied)
        }
    }

    /** 保存设置并同步 Compose 状态。 */
    fun persist(next: AppSettings) {
        val normalized = next.copy(
            filteredPackages = next.filteredPackages + AppSettingsRepository.BUILTIN_FILTERED_PACKAGES
        )
        settings = normalized
        settingsRepository.save(normalized)
    }

    /** 即时取消指定应用的过滤状态，内置防循环过滤项不会被移除。 */
    fun removePackageFilter(packageName: String) {
        if (packageName.isBlank() || packageName in AppSettingsRepository.BUILTIN_FILTERED_PACKAGES) return
        persist(settings.copy(filteredPackages = settings.filteredPackages - packageName))
        statusText = context.getString(
            R.string.status_forwarding_restored,
            InstalledAppRepository.resolveApplicationName(context, packageName)
        )
    }

    /** 批量新增应用过滤，完成后立即写入配置并生效。 */
    fun addPackageFilters(packageNames: Set<String>) {
        val validPackages = packageNames
            .filterNot { it in AppSettingsRepository.BUILTIN_FILTERED_PACKAGES }
            .toSet()
        if (validPackages.isEmpty()) return
        persist(settings.copy(filteredPackages = settings.filteredPackages + validPackages))
        statusText = context.resources.getQuantityString(
            R.plurals.status_filters_added,
            validPackages.size,
            validPackages.size
        )
    }

    LaunchedEffect(Unit) {
        if (BluetoothSilenceManager.hasBluetoothPermission(context)) refreshBluetoothDevices()
    }

    DisposableEffect(Unit) {
        val listener: () -> Unit = { updateBluetoothDevicesFromCache() }
        BluetoothSilenceManager.addConnectionStateListener(listener)
        onDispose { BluetoothSilenceManager.removeConnectionStateListener(listener) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(statusText = statusText, onLanguageClick = { showLanguageDialog = true })

            SectionTitle(
                text = stringResource(R.string.section_bark),
                helpBody = stringResource(R.string.help_bark)
            )
            OutlinedTextField(
                value = barkKey,
                onValueChange = { barkKey = it },
                label = { Text(stringResource(R.string.bark_key)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.barkServerUrl,
                onValueChange = { persist(settings.copy(barkServerUrl = it)) },
                label = { Text(stringResource(R.string.bark_server_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.deviceName,
                onValueChange = { persist(settings.copy(deviceName = it)) },
                label = { Text(stringResource(R.string.device_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    settingsRepository.saveBarkKey(barkKey)
                    statusText = context.getString(R.string.status_bark_key_saved)
                }) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = {
                    settingsRepository.saveBarkKey(barkKey)
                    statusText = context.getString(R.string.status_sending_test)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ForwardDispatcher.forwardTest(context)
                        }
                        statusText = if (result.success) {
                            context.getString(R.string.status_test_success)
                        } else {
                            context.getString(R.string.status_test_failed, result.detail)
                        }
                        logs = logRepository.list()
                    }
                }) {
                    Text(stringResource(R.string.action_test_push))
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.section_forwarding))
            FeatureSwitch(
                title = stringResource(R.string.feature_system_notifications),
                checked = settings.notificationEnabled,
                onCheckedChange = { persist(settings.copy(notificationEnabled = it)) }
            )
            FeatureSwitch(
                title = stringResource(R.string.feature_phone),
                checked = settings.phoneEnabled,
                onCheckedChange = {
                    persist(settings.copy(phoneEnabled = it))
                    onPhoneEnabledChanged(it)
                }
            )
            FeatureSwitch(
                title = stringResource(R.string.feature_retry),
                checked = settings.retryEnabled,
                onCheckedChange = { persist(settings.copy(retryEnabled = it)) }
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.section_runtime_status))
            FeatureSwitch(
                title = stringResource(R.string.feature_keep_alive_notification),
                checked = settings.keepAliveNotificationEnabled,
                onCheckedChange = {
                    persist(settings.copy(keepAliveNotificationEnabled = it))
                    onKeepAliveNotificationChanged(it)
                }
            )

            HorizontalDivider()
            SectionTitle(
                text = stringResource(R.string.section_permissions),
                helpBody = stringResource(R.string.help_permissions)
            )
            PermissionRow(
                title = stringResource(R.string.permission_notification_access),
                granted = isNotificationAccessGranted(context),
                actionText = stringResource(R.string.action_open),
                onClick = openNotificationSettings
            )
            PermissionRow(
                title = stringResource(R.string.permission_runtime),
                granted = hasRuntimePermissions(context),
                actionText = stringResource(R.string.action_request),
                onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }
            )
            PermissionRow(
                title = stringResource(R.string.permission_bluetooth),
                granted = hasBluetoothPermission(context),
                actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    stringResource(R.string.action_request)
                } else {
                    stringResource(R.string.action_not_required)
                },
                onClick = {
                    val permissions = requiredBluetoothPermissions()
                    if (permissions.isEmpty()) refreshBluetoothDevices()
                    else bluetoothPermissionLauncher.launch(permissions)
                }
            )
            PermissionRow(
                title = stringResource(R.string.permission_battery),
                granted = isIgnoringBatteryOptimizations(context),
                actionText = stringResource(R.string.action_open),
                onClick = openBatterySettings
            )

            HorizontalDivider()
            BluetoothSilenceSection(
                settings = settings,
                devices = bluetoothDevices,
                permissionGranted = hasBluetoothPermission(context),
                refreshing = bluetoothRefreshing,
                onRefresh = ::refreshBluetoothDevices,
                onEnabledChange = { persist(settings.copy(bluetoothSilenceEnabled = it)) },
                onDeviceCheckedChange = { address, checked ->
                    val nextAddresses = if (checked) {
                        settings.mutedBluetoothAddresses + address
                    } else {
                        settings.mutedBluetoothAddresses - address
                    }
                    persist(settings.copy(mutedBluetoothAddresses = nextAddresses))
                }
            )

            HorizontalDivider()
            FilteredAppsSection(
                context = context,
                filteredPackages = settings.filteredPackages,
                onAdd = {
                    // 打开选择器前读取最新日志，确保“最近通知过”分组及时更新。
                    logs = logRepository.list()
                    showAppPicker = true
                },
                onRemove = ::removePackageFilter
            )

            HorizontalDivider()
            LogSection(
                logs = logs,
                onRefresh = { logs = logRepository.list() },
                onClear = {
                    logRepository.clear()
                    logs = emptyList()
                    statusText = context.getString(R.string.status_logs_cleared)
                }
            )
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { languageTag ->
                showLanguageDialog = false
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                KeepAliveService.refresh(context)
            }
        )
    }
    if (showAppPicker) {
        AppPickerSheet(
            context = context,
            logs = logs,
            excludedPackages = settings.filteredPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { packages ->
                addPackageFilters(packages)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun Header(statusText: String, onLanguageClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onLanguageClick) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = stringResource(R.string.action_language)
            )
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    helpBody: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    var showHelp by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (helpBody != null) {
            IconButton(onClick = { showHelp = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.action_help)
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text(actionText)
            }
        }
    }
    if (showHelp && helpBody != null) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(text) },
            text = { Text(helpBody) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun FeatureSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BluetoothSilenceSection(
    settings: AppSettings,
    devices: List<BluetoothDeviceInfo>,
    permissionGranted: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDeviceCheckedChange: (String, Boolean) -> Unit
) {
    SectionTitle(
        text = stringResource(R.string.section_bluetooth_silence),
        helpBody = stringResource(R.string.help_bluetooth_silence)
    )
    FeatureSwitch(
        title = stringResource(R.string.feature_silence_selected_bluetooth),
        checked = settings.bluetoothSilenceEnabled,
        onCheckedChange = onEnabledChange
    )
    OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
        Text(
            if (refreshing) stringResource(R.string.action_refreshing)
            else stringResource(R.string.action_refresh)
        )
    }
    if (!permissionGranted) {
        Text(
            stringResource(R.string.bluetooth_permission_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    if (devices.isEmpty()) {
        Text(stringResource(R.string.bluetooth_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    devices.forEach { device ->
        BluetoothDeviceRow(
            device = device,
            checked = settings.mutedBluetoothAddresses.contains(device.address),
            onCheckedChange = { checked -> onDeviceCheckedChange(device.address, checked) }
        )
    }
}

@Composable
private fun BluetoothDeviceRow(
    device: BluetoothDeviceInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (device.connected) {
                    stringResource(R.string.bluetooth_connected)
                } else {
                    stringResource(R.string.bluetooth_disconnected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (device.connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) {
                    stringResource(R.string.permission_granted)
                } else {
                    stringResource(R.string.permission_not_granted)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(onClick = onClick) { Text(actionText) }
    }
}

@Composable
private fun FilteredAppsSection(
    context: Context,
    filteredPackages: Set<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val customPackages = filteredPackages - AppSettingsRepository.BUILTIN_FILTERED_PACKAGES
    val apps = remember(customPackages) {
        customPackages.map { packageName ->
            AppCandidate(
                name = InstalledAppRepository.resolveApplicationName(context, packageName),
                packageName = packageName
            )
        }.sortedWith(compareBy<AppCandidate> { it.name }.thenBy(AppCandidate::packageName))
    }

    SectionTitle(
        text = stringResource(R.string.section_app_filter),
        helpBody = stringResource(R.string.help_app_filter),
        actionText = stringResource(R.string.action_add),
        onAction = onAdd
    )
    if (apps.isEmpty()) {
        Text(stringResource(R.string.app_filter_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    apps.forEach { app ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onRemove(app.packageName) }) {
                Text(stringResource(R.string.action_remove_filter))
            }
        }
    }
}

@Composable
private fun LogSection(
    logs: List<ForwardLogItem>,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    SectionTitle(stringResource(R.string.section_recent_status))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
        TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
    }
    if (logs.isEmpty()) {
        Text(stringResource(R.string.logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        logs.take(12).forEach { item ->
            LogRow(item)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LogRow(item: ForwardLogItem) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val pattern = stringResource(R.string.date_time_pattern)
    val time = remember(item.time, locale, pattern) {
        SimpleDateFormat(pattern, locale).format(Date(item.time))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.log_row_format,
                time,
                item.type,
                if (item.success) stringResource(R.string.result_success)
                else stringResource(R.string.result_failure)
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${item.source} · ${item.detail}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit, onLanguageSelected: (String) -> Unit) {
    val selectedTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val options = listOf(
        "" to stringResource(R.string.language_follow_system),
        "zh-Hans" to stringResource(R.string.language_chinese_simplified),
        "en" to stringResource(R.string.language_english)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    val selected = when (tag) {
                        "" -> selectedTag.isBlank()
                        "zh-Hans" -> selectedTag.startsWith("zh")
                        "en" -> selectedTag.startsWith("en")
                        else -> selectedTag == tag
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(tag) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    context: Context,
    logs: List<ForwardLogItem>,
    excludedPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var sections by remember { mutableStateOf(AppPickerSections(emptyList(), emptyList())) }
    var query by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var showAllApps by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(logs, excludedPackages) {
        loading = true
        loadFailed = false
        showAllApps = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                AppPickerCatalog.build(
                    recentApps = InstalledAppRepository.recentFromLogs(context, logs),
                    launcherApps = InstalledAppRepository.listLauncherApps(context),
                    excludedPackages = excludedPackages + AppSettingsRepository.BUILTIN_FILTERED_PACKAGES
                )
            }
        }
        result.onSuccess { sections = it }.onFailure { loadFailed = true }
        loading = false
    }

    val searchedSections = remember(sections, query) { AppPickerCatalog.search(sections, query) }
    val visibleSections = remember(searchedSections, showAllApps) {
        AppPickerCatalog.visibleSections(searchedSections, showAllApps)
    }
    val hasHiddenOtherApps = remember(searchedSections, showAllApps) {
        AppPickerCatalog.hasHiddenOtherApps(searchedSections, showAllApps)
    }
    val hasVisibleApps = visibleSections.recent.isNotEmpty() || visibleSections.other.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.picker_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    loading -> {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(stringResource(R.string.picker_loading))
                        }
                    }
                    loadFailed -> Text(
                        stringResource(R.string.picker_load_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                    !hasVisibleApps -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.picker_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasHiddenOtherApps) {
                                OutlinedButton(
                                    onClick = { showAllApps = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.picker_show_more_apps))
                                }
                            }
                        }
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (visibleSections.recent.isNotEmpty()) {
                            item {
                                PickerGroupTitle(stringResource(R.string.picker_recent_apps))
                            }
                            items(visibleSections.recent, key = AppCandidate::packageName) { app ->
                                AppPickerRow(
                                    context = context,
                                    app = app,
                                    selected = app.packageName in selectedPackages,
                                    onSelectedChange = { selected ->
                                        selectedPackages = if (selected) {
                                            selectedPackages + app.packageName
                                        } else {
                                            selectedPackages - app.packageName
                                        }
                                    }
                                )
                            }
                        }
                        if (visibleSections.other.isNotEmpty()) {
                            item {
                                PickerGroupTitle(stringResource(R.string.picker_other_apps))
                            }
                            items(visibleSections.other, key = AppCandidate::packageName) { app ->
                                AppPickerRow(
                                    context = context,
                                    app = app,
                                    selected = app.packageName in selectedPackages,
                                    onSelectedChange = { selected ->
                                        selectedPackages = if (selected) {
                                            selectedPackages + app.packageName
                                        } else {
                                            selectedPackages - app.packageName
                                        }
                                    }
                                )
                            }
                        }
                        if (hasHiddenOtherApps) {
                            item {
                                OutlinedButton(
                                    onClick = { showAllApps = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.picker_show_more_apps))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.picker_selected_count,
                        selectedPackages.size,
                        selectedPackages.size
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { onConfirm(selectedPackages) },
                        enabled = selectedPackages.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun AppPickerRow(
    context: Context,
    app: AppCandidate,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val inspectionMode = LocalInspectionMode.current
    val iconBitmap = remember(app.packageName, inspectionMode) {
        if (inspectionMode) null
        else runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)
    }
}

/** 返回运行时危险权限列表，按系统版本跳过不存在的权限。 */
private fun requiredRuntimePermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}

/** Android 12 以后读取已配对蓝牙设备和连接状态需要 BLUETOOTH_CONNECT。 */
private fun requiredBluetoothPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }
}

/** 检查电话和通知展示权限是否都已允许。 */
private fun hasRuntimePermissions(context: Context): Boolean {
    return requiredRuntimePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/** 检查蓝牙设备读取权限。 */
private fun hasBluetoothPermission(context: Context): Boolean {
    return BluetoothSilenceManager.hasBluetoothPermission(context)
}

/** 检查通知监听权限。 */
private fun isNotificationAccessGranted(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

/** 检查是否已忽略电池优化。 */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
}
