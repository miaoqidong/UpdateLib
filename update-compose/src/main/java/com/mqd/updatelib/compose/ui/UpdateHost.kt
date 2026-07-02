package com.mqd.updatelib.compose.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.R

/**
 * 更新弹窗宿主（Compose 版本）。
 *
 * 根据 ViewModel 状态自动显示/隐藏更新弹窗。
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
@Composable
fun UpdateHost(
    vm: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val localVersion = UpdateManager.getCurrentVersion()

    // 通知权限请求器：仅弹提示，不阻塞下载
    val requestNotificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // 被永久拒绝 → 跳设置页兜底提示
            val activity = context.findActivity()
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                context.gotoAppNotificationSettings()
            }
        }
        vm.dismissNotificationPermissionDialog()
    }

    LaunchedEffect(Unit) {
        vm.checkOnLaunch()
    }

    if (state.showDialog) {
        UpdateDialog(
            localVersion = localVersion,
            state = state,
            onDismissRequest = { vm.dismiss() },
            onIgnore = { vm.dismiss() },
            onConfirm = { vm.onConfirmUpdate(context) },
            onInstall = { vm.onInstall(context) },
            onMoveToBackground = { vm.onMoveToBackground() },
            onOpenRelease = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.getReleasesPageUrl()))
                context.startActivity(intent)
            }
        )
    }

    // 通知权限说明弹窗
    if (state.showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { vm.dismissNotificationPermissionDialog() },
            title = { Text(text = stringResource(id = R.string.updatelib_notification_permission_title)) },
            text = { Text(text = stringResource(id = R.string.updatelib_notification_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text(text = stringResource(id = R.string.updatelib_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissNotificationPermissionDialog() }) {
                    Text(text = stringResource(id = R.string.updatelib_deny))
                }
            }
        )
    }
}
