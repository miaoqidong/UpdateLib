package com.mqd.updatejava.core;

import android.content.Context;

/**
 * 全局 Context 持有器。
 */
public class UpdateLibContext {
    private static Context context;
    private static boolean initialized = false;

    public static void init(Context ctx) {
        if (!initialized) {
            context = ctx.getApplicationContext();
            initialized = true;
        }
    }

    public static Context getContext() {
        if (!initialized) {
            throw new IllegalStateException("UpdateLibContext not initialized. Call UpdateLibContext.init(context) first.");
        }
        return context;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
