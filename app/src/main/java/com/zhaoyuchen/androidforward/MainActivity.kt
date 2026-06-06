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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhaoyuchen.androidforward.bluetooth.BluetoothDeviceInfo
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.data.AppSettings
import com.zhaoyuchen.androidforward.data.AppSettingsRepository
import com.zhaoyuchen.androidforward.data.ForwardLogItem
import com.zhaoyuchen.androidforward.data.ForwardLogRepository
import com.zhaoyuchen.androidforward.forward.ForwardDispatcher
import com.zhaoyuchen.androidforward.service.KeepAliveService
import com.zhaoyuchen.androidforward.service.PhoneMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页入口。所有操作都即时写入本地配置，方便系统监听服务读取最新状态。
 */
class MainActivity : ComponentActivity() {
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

    /** 打开系统通知使用权页面，用户需要手动允许本应用。 */
    private fun openNotificationSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
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
    var filterText by remember {
        mutableStateOf(settings.filteredPackages.sorted().joinToString(separator = "\n"))
    }
    var bluetoothDevices by remember {
        mutableStateOf(BluetoothSilenceManager.listBondedDevices(context))
    }
    var bluetoothRefreshing by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(logRepository.list()) }
    var statusText by remember { mutableStateOf("等待配置") }

    /** 在后台线程完整查询蓝牙 Profile，完成后再刷新 Compose 列表。 */
    fun refreshBluetoothDevices() {
        if (bluetoothRefreshing) return
        bluetoothRefreshing = true
        statusText = "正在刷新蓝牙设备"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BluetoothSilenceManager.refreshAndListBondedDevices(context) }
            }
            result.onSuccess { devices ->
                bluetoothDevices = devices
                val connectedCount = devices.count { it.connected }
                statusText = "蓝牙设备列表已刷新，当前连接 $connectedCount 个"
            }.onFailure { error ->
                statusText = "刷新蓝牙设备失败：${error.message ?: "未知错误"}"
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
            val connectedCount = devices.count { it.connected }
            statusText = "蓝牙状态已自动更新，当前连接 $connectedCount 个"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        statusText = "权限状态已更新"
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (BluetoothSilenceManager.hasBluetoothPermission(context)) {
            refreshBluetoothDevices()
        } else {
            bluetoothDevices = emptyList()
            statusText = "未获得蓝牙设备权限"
        }
    }

    /** 保存设置并同步 Compose 状态。 */
    fun persist(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    LaunchedEffect(Unit) {
        if (BluetoothSilenceManager.hasBluetoothPermission(context)) {
            refreshBluetoothDevices()
        }
    }

    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            updateBluetoothDevicesFromCache()
        }
        BluetoothSilenceManager.addConnectionStateListener(listener)
        onDispose {
            BluetoothSilenceManager.removeConnectionStateListener(listener)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(statusText = statusText)

            SectionTitle("Bark")
            OutlinedTextField(
                value = barkKey,
                onValueChange = { barkKey = it },
                label = { Text("Bark Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.barkServerUrl,
                onValueChange = { persist(settings.copy(barkServerUrl = it)) },
                label = { Text("Bark 服务地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        settingsRepository.saveBarkKey(barkKey)
                        statusText = "Bark Key 已保存"
                    }
                ) {
                    Text("保存")
                }
                OutlinedButton(
                    onClick = {
                        settingsRepository.saveBarkKey(barkKey)
                        statusText = "正在发送测试推送"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ForwardDispatcher.forwardTest(context)
                            }
                            statusText = if (result.success) "测试推送成功" else "测试推送失败：${result.detail}"
                            logs = logRepository.list()
                        }
                    }
                ) {
                    Text("测试推送")
                }
            }

            HorizontalDivider()
            SectionTitle("转发")
            FeatureSwitch(
                title = "系统通知",
                checked = settings.notificationEnabled,
                onCheckedChange = { persist(settings.copy(notificationEnabled = it)) }
            )
            FeatureSwitch(
                title = "短信",
                checked = settings.smsEnabled,
                onCheckedChange = { persist(settings.copy(smsEnabled = it)) }
            )
            FeatureSwitch(
                title = "电话",
                checked = settings.phoneEnabled,
                onCheckedChange = {
                    persist(settings.copy(phoneEnabled = it))
                    onPhoneEnabledChanged(it)
                }
            )
            FeatureSwitch(
                title = "失败重试",
                checked = settings.retryEnabled,
                onCheckedChange = { persist(settings.copy(retryEnabled = it)) }
            )

            HorizontalDivider()
            SectionTitle("运行状态")
            FeatureSwitch(
                title = "常驻状态通知",
                checked = settings.keepAliveNotificationEnabled,
                onCheckedChange = {
                    persist(settings.copy(keepAliveNotificationEnabled = it))
                    onKeepAliveNotificationChanged(it)
                }
            )

            HorizontalDivider()
            SectionTitle("权限")
            PermissionRow(
                title = "通知使用权",
                granted = isNotificationAccessGranted(context),
                actionText = "打开",
                onClick = openNotificationSettings
            )
            PermissionRow(
                title = "短信、电话和通知权限",
                granted = hasRuntimePermissions(context),
                actionText = "申请",
                onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }
            )
            PermissionRow(
                title = "蓝牙设备权限",
                granted = hasBluetoothPermission(context),
                actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "申请" else "无需",
                onClick = {
                    val permissions = requiredBluetoothPermissions()
                    if (permissions.isEmpty()) {
                        refreshBluetoothDevices()
                    } else {
                        bluetoothPermissionLauncher.launch(permissions)
                    }
                }
            )
            PermissionRow(
                title = "省电白名单",
                granted = isIgnoringBatteryOptimizations(context),
                actionText = "打开",
                onClick = openBatterySettings
            )

            HorizontalDivider()
            BluetoothSilenceSection(
                settings = settings,
                devices = bluetoothDevices,
                permissionGranted = hasBluetoothPermission(context),
                refreshing = bluetoothRefreshing,
                onRefresh = ::refreshBluetoothDevices,
                onEnabledChange = { enabled ->
                    persist(settings.copy(bluetoothSilenceEnabled = enabled))
                },
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
            SectionTitle("应用过滤")
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("包名，一行一个") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    val packages = filterText
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .toSet()
                    persist(settings.copy(filteredPackages = packages))
                    statusText = "过滤列表已保存"
                }
            ) {
                Text("保存过滤列表")
            }

            HorizontalDivider()
            LogSection(
                logs = logs,
                onRefresh = { logs = logRepository.list() },
                onClear = {
                    logRepository.clear()
                    logs = emptyList()
                    statusText = "日志已清空"
                }
            )
        }
    }
}

@Composable
private fun Header(statusText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "通知转发",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
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
    SectionTitle("蓝牙静默")
    FeatureSwitch(
        title = "连接所选设备时静默",
        checked = settings.bluetoothSilenceEnabled,
        onCheckedChange = onEnabledChange
    )
    OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
        Text(if (refreshing) "正在刷新" else "刷新蓝牙设备")
    }
    if (!permissionGranted) {
        Text("授权后显示已配对设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (devices.isEmpty()) {
        Text("暂无已配对设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                text = if (device.connected) "已连接" else "未连接",
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
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) "已允许" else "未允许",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(actionText)
        }
    }
}

@Composable
private fun LogSection(
    logs: List<ForwardLogItem>,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    SectionTitle("最近状态")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
        TextButton(onClick = onClear) {
            Text("清空")
        }
    }
    if (logs.isEmpty()) {
        Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        logs.take(12).forEach { item ->
            LogRow(item)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LogRow(item: ForwardLogItem) {
    val time = android.text.format.DateFormat.format("MM-dd HH:mm:ss", item.time).toString()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$time  ${item.type}  ${if (item.success) "成功" else "失败"}",
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

/** 返回运行时危险权限列表，按系统版本跳过不存在的权限。 */
private fun requiredRuntimePermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
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

/** 检查短信、电话和通知展示权限是否都已允许。 */
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
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
