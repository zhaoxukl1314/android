package com.example.zhaoxu.study;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.zhaoxu.study.netUtils.HtmlNetUtils;

/**
 * Created by Administrator on 2016/5/18.
 */
public class HtmlFromNetActivity extends Activity {

    private Button btn_get;
    private Button btn_post;
    private TextView tv_username;
    private TextView tv_password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.html_from_net);
        btn_get = (Button) findViewById(R.id.html_get);
        btn_post = (Button) findViewById(R.id.html_post);
        tv_username = (TextView) findViewById(R.id.html_username);
        tv_password = (TextView) findViewById(R.id.html_password);
        btn_get.setOnClickListener(new getOnclickListner());
    }

    private class getOnclickListner implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            final String userName = tv_username.getText().toString();
            final String password = tv_password.getText().toString();
            if (TextUtils.isEmpty(userName) && TextUtils.isEmpty(password)) {
                Toast.makeText(HtmlFromNetActivity.this,"账号密码不能为空",Toast.LENGTH_LONG);
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String html = HtmlNetUtils.getStringFromNet(userName,password);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (TextUtils.isEmpty(html)) {
                                    Toast.makeText(HtmlFromNetActivity.this,"连接失败",Toast.LENGTH_LONG);
                                } else {
                                    Toast.makeText(HtmlFromNetActivity.this,html,Toast.LENGTH_LONG);
                                }
                            }
                        });
                    }
                }).start();
            }
        }
    }
}
