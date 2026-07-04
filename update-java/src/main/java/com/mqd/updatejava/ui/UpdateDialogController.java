package com.mqd.updatejava.ui;

import android.app.AlertDialog;

/**
 * 更新对话框控制器。
 */
public class UpdateDialogController {
    private final AlertDialog dialog;

    public UpdateDialogController(AlertDialog dialog) {
        this.dialog = dialog;
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
