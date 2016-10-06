package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

/**
 * Created by Administrator on 2016/6/16.
 */
public class NotificationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);
    }

    public void notify(View view) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.mipmap.comma_face_01,"通知来了", System.currentTimeMillis());
        notificationManager.notify(0,notification);
    }
}
