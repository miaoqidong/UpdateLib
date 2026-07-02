package com.mqd.updatelib.core

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Releases API 数据模型（仅取检查更新需要的字段）。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/18
 */
@Serializable
@Keep
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<Asset> = emptyList()
) {

    @Serializable
    @Keep
    data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0
    )
}
