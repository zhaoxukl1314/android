package com.example.zhaoxu.study.Broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class DailReceiver extends BroadcastReceiver {
    private static final String TAG = "DailReceiver";

    public DailReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        Log.e(TAG,"有电话打出去");
        String number = getResultData();
        Log.e(TAG,"number : " + number);
        SharedPreferences sharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String ipNumber = sharedPreferences.getString("ipnumber", "");
        setResultData(ipNumber + number);
        //throw new UnsupportedOperationException("Not yet implemented");
    }
}
