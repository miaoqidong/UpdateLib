package com.mqd.updatelib.core

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备用更新源 JSON 数据模型。
 *
 * 对应远程 JSON 格式示例：
 * ```json
 * {
 *   "versionName": "8.0.73",
 *   "versionCode": 579,
 *   "downloadUrl": "http://xiazai.miaoqidong.com/...",
 *   "des": "https://520821.cn/rule/wxzhuli.html",
 *   "desUrl": "https://520821.cn/rule/wxzhuli.html"
 * }
 * ```
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/30
 */
@Serializable
@Keep
data class FallbackRelease(
    @SerialName("versionName") val versionName: String = "",
    @SerialName("versionCode") val versionCode: Long = 0L,
    @SerialName("downloadUrl") val downloadUrl: String = "",
    /** 更新说明内容的抓取地址（HTML 或纯文本）。 */
    @SerialName("des") val desContent: String = "",
    /** 升级弹窗右上角按钮的跳转链接。 */
    @SerialName("desUrl") val desUrl: String = ""
)
