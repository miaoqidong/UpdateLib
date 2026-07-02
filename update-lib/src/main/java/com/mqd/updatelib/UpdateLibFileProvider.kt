package com.mqd.updatelib

import androidx.core.content.FileProvider

/**
 * Company    : 
 * Author     : Lucas     联系WX:780203920
 * Date       : 2026/7/2  11:44
 * Description:This is UpdateLibFileProvider
 */
class UpdateLibFileProvider: FileProvider() {
    // 可以添加自定义逻辑
    override fun onCreate(): Boolean {
        // 自定义初始化逻辑
        return super.onCreate()
    }
}