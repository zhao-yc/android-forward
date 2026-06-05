package com.zhaoyuchen.androidforward

import android.app.Application
import com.zhaoyuchen.androidforward.forward.RetryQueue
import com.zhaoyuchen.androidforward.service.PhoneMonitorService

/**
 * 应用启动入口。这里只做轻量恢复工作，不阻塞界面启动。
 */
class ForwardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneMonitorService.startIfNeeded(this)
        RetryQueue.flushAsync(this)
    }
}
