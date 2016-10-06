package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;

import com.example.zhaoxu.study.View.Preview;

/**
 * Created by Administrator on 2016/6/15.
 */
public class CameraActivity extends Activity {

    private static final String TAG = "CameraActivity";
    private Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);
        Button button = (Button) findViewById(R.id.camera_btn);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.camera_frame);
        camera = getCameraInstance();
        Log.e(TAG,"zhaoxu camera : " + camera);
        Preview preview = new Preview(this, camera);
        frameLayout.addView(preview);
    }

    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }
}
