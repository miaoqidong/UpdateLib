package xiaofeixia.gesture;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.ComponentActivity;

import com.mqd.updatelib.ui.UpdateDialogHelper;

/**
 * Java 项目接入示例。
 * <p>
 * 演示如何用纯 Java + 传统 View 体系以最少代码接入 update-lib。
 * 不需要 Kotlin 协程，全部使用回调式 API。
 * 使用时复制此文件即可，不需要 update-compose 模块。
 */
public class SampleJavaActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;
        int hPad = (int) (24 * density);
        int vPad = (int) (12 * density);

        Button btnCheckUpdate = new Button(this);
        btnCheckUpdate.setText("检查更新 (Java)");
        btnCheckUpdate.setTextColor(0xFFFFFFFF);
        btnCheckUpdate.setPadding(hPad, vPad, hPad, vPad);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f * density);
        bg.setColor(0xFF6750A4); // Material3 primary
        btnCheckUpdate.setBackground(bg);
        btnCheckUpdate.setStateListAnimator(null);

        btnCheckUpdate.setOnClickListener(v ->
                UpdateDialogHelper.checkAndShowUpdateDialog(this, null)
        );

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.CENTER;
        root.addView(btnCheckUpdate, params);
        setContentView(root);

        // 进入页面自动检查更新（与 Compose 版行为一致）
        UpdateDialogHelper.checkAndShowUpdateDialog(this, null);
    }
}
