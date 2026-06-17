package com.zhaoyuchen.androidforward.forward

import android.content.Context
import androidx.annotation.StringRes
import com.zhaoyuchen.androidforward.R
import com.zhaoyuchen.androidforward.localization.localizedString

/**
 * 支持的转发类型。枚举名称用于重试队列序列化，显示名称按当前应用语言解析。
 */
enum class ForwardType(@StringRes private val titleResId: Int) {
    TEST(R.string.forward_type_test),
    NOTIFICATION(R.string.forward_type_notification),
    INCOMING_CALL(R.string.forward_type_incoming_call),
    MISSED_CALL(R.string.forward_type_missed_call);

    /** 解析当前应用语言下的转发类型名称。 */
    fun title(context: Context): String = context.localizedString(titleResId)
}
