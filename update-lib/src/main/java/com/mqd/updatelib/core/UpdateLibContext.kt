package com.mqd.updatelib.core

import android.annotation.SuppressLint
import android.content.Context

/**
 * 全局 Context 持有器
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
object UpdateLibContext {
    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context

    val isInitialized: Boolean
        get() = ::context.isInitialized

    @JvmStatic
    fun init(ctx: Context) {
        if (!isInitialized) {
            context = ctx.applicationContext
        }
    }

    @JvmStatic
    fun getContext(): Context {
        check(isInitialized) {
            "UpdateLibContext is not initialized. Call UpdateLibContext.init(context) first."
        }
        return context
    }
}
