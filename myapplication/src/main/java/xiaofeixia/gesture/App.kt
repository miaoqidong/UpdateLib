package xiaofeixia.gesture

import android.app.Application
import android.content.Context
import com.mqd.updatelib.UpdateManager


/**
 * @author quzhuligpt@gmail.com
 * @since 2024/11/17
 */
class App : Application() {


    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化更新库（版本信息自动从 PackageInfo 读取）
        UpdateManager.init(
            context = this,
            fallbackUrl = "https://520821.cn/rule/wxzhuli.json",
            fallbackOnly = true
        )
    }
}