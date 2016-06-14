package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.zhaoxu.study.Service.MyService;

/**
 * Created by Administrator on 2016/6/2.
 */
public class MyBindServiceActivity extends Activity {

    private static final String TAG = "MyBindServiceActivity";
    private Button callButton;
    private Button destroyButton;
    private Button createButton;
    private myConnection conn;
    private MyService.MiddlePerson mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_binder_service);
        createButton = (Button) findViewById(R.id.btn_binder_service_create);
        destroyButton = (Button) findViewById(R.id.btn_binder_service_destroy);
        callButton = (Button) findViewById(R.id.btn_binder_service_call);
        createButton.setOnClickListener(new onCreateListener());
        destroyButton.setOnClickListener(new onDestroyListener());
        callButton.setOnClickListener(new onCallListener());
    }

    private class onCreateListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            Intent intent = new Intent(MyBindServiceActivity.this, MyService.class);
            Log.e(TAG,"点击onCreate");
            conn = new myConnection();
            bindService(intent,conn,BIND_AUTO_CREATE);
        }
    }

    private class onDestroyListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            unbindService(conn);
        }
    }

    private class onCallListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            mp.callMethodInService();
        }
    }

    private class myConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mp = (MyService.MiddlePerson) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    }
}
