package xiaofeixia.gesture

import android.app.Application
import android.content.Context
import com.mqd.updatelib.UpdateManager
import com.mqd.updatejava.UpdateManager as UpdateManagerJava


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

        // 初始化 update-java（纯 Java 版）
        UpdateManagerJava.init(this,
            "https://520821.cn/rule/wxzhuli.json", true)

        // 初始化 update-simple（极简版，单文件，仅检查+跳转网站）
        // 方式一：GitHub 优先 + JSON 兜底（推荐）
        com.mqd.updatesimple.UpdateManager.init(this, "aaronzzx", "gulugulu", "https://520821.cn/rule/wxzhuli.json")
        // 方式二：自定义 JSON
        // UpdateHelper.init(this, "https://520821.cn/rule/wxzhuli.json")
        // 方式三：纯 GitHub Releases
        // UpdateHelper.init(this, "aaronzzx", "gulugulu")
    }
}