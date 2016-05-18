package com.example.zhaoxu.study;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class GetStringFromNet extends Activity {

    private static final String TAG = "GetStringFromNet";
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
                    TextView tv = (TextView) findViewById(R.id.text_get_string);
                    tv.setText((CharSequence) msg.obj);
                default:
                    //do nothing
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.string_from_net);
        Button btn = (Button) findViewById(R.id.btn_get_string);
        EditText edt = (EditText) findViewById(R.id.edt_get_string);
        mUrl = edt.getText().toString();
        btn.setOnClickListener(new getImageListener());
    }

    private class getImageListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String html = getStringFromNet();
                    Message msg = new Message();
                    msg.what = 0;
                    msg.obj = html;
                    mHandler.sendMessage(msg);
                }
            }).start();
        }
    }

    private String getStringFromNet() {
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
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len = -1;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer,0,len);
                    }
                    inputStream.close();
                    String text = byteArrayOutputStream.toString();
                    byteArrayOutputStream.close();

                    String charset = "utf-8";
                    if (text.contains("gbk") || text.contains("gb2312") || text.contains("GBK") || text.contains("GB2312")) {
                        charset = "gbk";
                    }
                    String html = new String(byteArrayOutputStream.toByteArray(),charset);

                    return html;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
