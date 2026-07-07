package xiaofeixia.gesture;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;

import com.mqd.updatesimple.UpdateManager;

/**
 * update-simple 模块示例——纯检查更新 + 跳转网站。
 */
public class Simple_ViewActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;

        Button btnCheckUpdate = new Button(this);
        btnCheckUpdate.setText("检查更新");
        btnCheckUpdate.setTextColor(0xFFFFFFFF);
        btnCheckUpdate.setPadding(
                (int) (24 * density), (int) (12 * density),
                (int) (24 * density), (int) (12 * density));
        btnCheckUpdate.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            setCornerRadius(24f * density);
            setColor(0xFF6750A4);
        }});
        btnCheckUpdate.setStateListAnimator(null);
        btnCheckUpdate.setOnClickListener(v -> UpdateManager.check(this));

        FrameLayout root = new FrameLayout(this);
        root.addView(btnCheckUpdate,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT) {{
                    gravity = Gravity.CENTER;
                }});
        setContentView(root);

        UpdateManager.check(this);
    }
}
