package com.example.zhaoxu.study.Service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class MyService extends Service {
    private static final String TAG = "MyService";

    public MyService() {
    }

    @Override
    public void onCreate() {
        Log.e(TAG,"服务创建");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG,"服务退出");
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e(TAG,"服务unbind");
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG,"服务bind");
        // TODO: Return the communication channel to the service.
        return new MiddlePerson();
    }

    public void MethodInService() {
        Toast.makeText(this, "调用成功", Toast.LENGTH_LONG).show();
    }

    public class MiddlePerson extends Binder {

        public void callMethodInService() {
            MethodInService();
        }
    }
}
