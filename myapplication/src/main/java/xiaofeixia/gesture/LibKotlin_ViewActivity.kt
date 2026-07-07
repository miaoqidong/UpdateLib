package xiaofeixia.gesture

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.mqd.updatelib.ui.UpdateDialogHelper

/**
 * 非 Compose 项目接入示例（Kotlin）。
 *
 * 演示如何用传统 View 体系以最少代码接入 update-lib。
 * 使用时复制此文件即可，不需要 update-compose 模块。
 */
class LibKotlin_ViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val btnCheckUpdate = Button(this).apply {
            text = "检查更新"
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((24 * density).toInt(), (12 * density).toInt(), (24 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(0xFF6750A4.toInt()) // Material3 primary
            }
            stateListAnimator = null
            setOnClickListener {
                UpdateDialogHelper.checkAndShowUpdateDialog(this@LibKotlin_ViewActivity)
            }
        }

        val root = android.widget.FrameLayout(this).apply {
            addView(btnCheckUpdate, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER })
        }
        setContentView(root)

        // 进入页面自动检查更新（与 Compose 版行为一致）
        UpdateDialogHelper.checkAndShowUpdateDialog(this)
    }
}
