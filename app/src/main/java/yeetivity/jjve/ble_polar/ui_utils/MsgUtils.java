package yeetivity.jjve.ble_polar.ui_utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class MsgUtils {

    // short message
    public static void showToast(String msg, Context context) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    // alert message
    public static Dialog createDialog(String title, String msg, Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(" Ok", (dialog, id) -> {
            // do nothing, just close the alert
        });
        return builder.create();
    }
}

