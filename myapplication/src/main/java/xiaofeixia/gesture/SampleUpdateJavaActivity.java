package xiaofeixia.gesture;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.ComponentActivity;

import com.mqd.updatejava.UpdateManager;
import com.mqd.updatejava.ui.UpdateDialogHelper;

/**
 * update-java（纯 Java 零依赖）接入示例。
 * <p>
 * 演示如何用 update-java 模块接入，该模块不引入任何额外依赖（无 Kotlin、无协程、无 DataStore）。
 * 使用时复制此文件即可。
 */
public class SampleUpdateJavaActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;
        int hPad = (int) (24 * density);
        int vPad = (int) (12 * density);

        Button btnCheckUpdate = new Button(this);
        btnCheckUpdate.setText("检查更新 (update-java)");
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

        // 进入页面自动检查更新
        UpdateDialogHelper.checkAndShowUpdateDialog(this, null);
    }
}
