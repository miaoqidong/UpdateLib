package com.mqd.updatesimple.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 使用 SharedPreferences 存储更新状态。
 */
public class StateStore {

    private static final String PREFS_NAME = "updatelib_simple_state";
    private static final String KEY_STATE = "update_state";

    public static UpdateState load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_STATE, "{}");
        return JsonHelper.updateStateFromJson(json);
    }

    public static void save(Context context, UpdateState state) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_STATE, JsonHelper.updateStateToJson(state)).apply();
    }
}
