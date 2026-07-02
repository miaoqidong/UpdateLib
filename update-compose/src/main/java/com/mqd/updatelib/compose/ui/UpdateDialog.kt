package com.mqd.updatelib.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.mqd.updatelib.compose.R
import com.mqd.updatelib.core.FallbackChecker
import com.mqd.updatelib.core.UpdateChecker
import android.webkit.WebView
import androidx.compose.ui.window.Dialog

/**
 * 更新弹窗（Compose 版本）。状态机五态：
 *
 * - 下载中：进度条 + [转后台]
 * - 已下完：[点击安装]
 * - 下载失败：[重试]
 * - 有新版：[忽略此版本] [立即更新]
 * - 已最新：仅标题
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
@Composable
fun UpdateDialog(
    localVersion: String,
    state: UpdateViewModel.UiState,
    onDismissRequest: () -> Unit,
    onIgnore: () -> Unit,
    onConfirm: () -> Unit,
    onInstall: () -> Unit,
    onMoveToBackground: () -> Unit,
    onOpenRelease: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            val isUpToDate = state.phase == UpdateViewModel.UpdatePhase.UpToDate
            val titleRes = when (state.phase) {
                UpdateViewModel.UpdatePhase.UpToDate -> R.string.updatelib_compose_update_already_latest_title
                UpdateViewModel.UpdatePhase.Failed -> R.string.updatelib_compose_update_download_failed_title
                else -> R.string.updatelib_compose_update_available_title
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DialogTitlePadding,
                        end = ItemPadding / 2,
                        top = ItemPadding,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = titleRes),
                    fontSize = DialogTitleFontSize,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clickable { onOpenRelease() }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = OpenInNewIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.height(18.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.updatelib_compose_update_view_details),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Text(
                modifier = Modifier.padding(
                    start = DialogTitlePadding,
                    end = DialogTitlePadding,
                    bottom = ItemPadding
                ),
                text = if (isUpToDate) {
                    UpdateChecker.displayVersion(state.version.ifBlank { localVersion })
                } else {
                    stringResource(
                        id = R.string.updatelib_compose_update_version_compare,
                        UpdateChecker.displayVersion(localVersion),
                        UpdateChecker.displayVersion(state.version)
                    )
                },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )

            val maxNotesHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
            val notesModifier = Modifier
                .padding(horizontal = ItemPadding)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                )
                .heightIn(max = maxNotesHeight)

            if (FallbackChecker.isHtmlContent(state.notes)) {
                AndroidView(
                    modifier = notesModifier,
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, state.notes, "text/html", "UTF-8", null)
                    }
                )
            } else {
                Text(
                    modifier = notesModifier
                        .verticalScroll(rememberScrollState())
                        .padding(ItemPadding),
                    text = state.notes.ifBlank { "—" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            when (state.phase) {
                UpdateViewModel.UpdatePhase.Downloading -> {
                    DownloadingContent(progress = state.progress)
                    PrimaryButtonRow(
                        text = stringResource(id = R.string.updatelib_compose_update_move_to_background),
                        onClick = onMoveToBackground
                    )
                }
                UpdateViewModel.UpdatePhase.Downloaded -> {
                    PrimaryButtonRow(
                        text = stringResource(id = R.string.updatelib_compose_update_install_now),
                        onClick = onInstall
                    )
                }
                UpdateViewModel.UpdatePhase.Failed -> {
                    PrimaryButtonRow(
                        text = stringResource(id = R.string.updatelib_compose_update_retry),
                        onClick = onConfirm
                    )
                }
                UpdateViewModel.UpdatePhase.NewVersion -> {
                    ActionRow(onIgnore = onIgnore, onConfirm = onConfirm)
                }
                UpdateViewModel.UpdatePhase.UpToDate -> {
                    Spacer(modifier = Modifier.padding(ItemPadding))
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onIgnore: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemPadding, vertical = ItemPadding),
        horizontalArrangement = Arrangement.spacedBy(ItemPadding)
    ) {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = onIgnore,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(text = stringResource(id = R.string.updatelib_compose_update_ignore_version))
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConfirm
        ) {
            Text(text = stringResource(id = R.string.updatelib_compose_update_now))
        }
    }
}

@Composable
private fun PrimaryButtonRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemPadding, vertical = ItemPadding)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Text(text = text)
        }
    }
}

@Composable
private fun DownloadingContent(progress: Int) {
    val animatedFraction by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        label = "downloadProgress"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DialogTitlePadding, vertical = ItemPadding / 2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.updatelib_compose_update_downloading_label),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "$progress%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(ItemPadding / 2))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** OpenInNew 图标的自定义 ImageVector，避免依赖 material-icons-extended。 */
private val OpenInNewIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "OpenInNew",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(14.0f, 3.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(3.59f)
            lineToRelative(-9.83f, 9.83f)
            lineToRelative(1.41f, 1.41f)
            lineTo(19.0f, 6.41f)
            verticalLineTo(10.0f)
            horizontalLineToRelative(2.0f)
            verticalLineTo(3.0f)
            horizontalLineToRelative(-7.0f)
            close()
            moveTo(5.0f, 5.0f)
            verticalLineToRelative(14.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-7.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(5.0f)
            horizontalLineTo(7.0f)
            verticalLineTo(7.0f)
            horizontalLineToRelative(5.0f)
            verticalLineTo(5.0f)
            horizontalLineTo(5.0f)
            close()
        }
    }.build()
}
