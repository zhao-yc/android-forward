package com.zhaoyuchen.androidforward

import android.app.Application
import com.zhaoyuchen.androidforward.bluetooth.BluetoothSilenceManager
import com.zhaoyuchen.androidforward.forward.RetryQueue
import com.zhaoyuchen.androidforward.service.KeepAliveService
import com.zhaoyuchen.androidforward.service.PhoneMonitorService

/**
 * 应用启动入口。这里只做轻量恢复工作，不阻塞界面启动。
 */
class ForwardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BluetoothSilenceManager.refreshConnectedDeviceCacheAsync(this)
        KeepAliveService.startIfNeeded(this)
        PhoneMonitorService.startIfNeeded(this)
        RetryQueue.flushAsync(this)
    }
}
