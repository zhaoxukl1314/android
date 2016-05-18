package com.example.zhaoxu.study;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity" ;
    private EditText edt;
    private String mUrl;
    private ImageView imageView;
    private final static int SUCCEED = 0;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SUCCEED:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    imageView.setImageBitmap(bitmap);

                default:
                    //do nothing
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_from_net);
        Button btn = (Button) findViewById(R.id.btn_internet);
        EditText edt = (EditText) findViewById(R.id.ed_internet);
        mUrl = edt.getText().toString();
        imageView =  (ImageView) findViewById(R.id.image_internet);
        btn.setOnClickListener(new getImageListener());
    }

    private class getImageListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bitmap = getImageFromNet();
                    Message msg = new Message();
                    msg.what = 0;
                    msg.obj = bitmap;
                    mHandler.sendMessage(msg);
                }
            }).start();
        }
    }

    private Bitmap getImageFromNet() {
        URL url = null;
        try {
            url = new URL(mUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            if (url != null) {
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                urlConnection.connect();
                int responseCode = urlConnection.getResponseCode();
                Log.e(TAG,"responseCode : " + responseCode);
                if (responseCode == 200) {
                    InputStream inputStream = urlConnection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    return bitmap;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
