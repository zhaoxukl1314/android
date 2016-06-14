package com.example.zhaoxu.study.Activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class IpBroadcastActivity extends AppCompatActivity {

    private EditText editText;
    private Button button;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ip_broadcast);
        editText = (EditText) findViewById(R.id.et_broad);
        button = (Button) findViewById(R.id.btn_broad);
        sharedPreferences = getSharedPreferences("config", Context.MODE_PRIVATE);
        button.setOnClickListener(new IpChangeListener());
    }

    private class IpChangeListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            String ipNumber = editText.getText().toString().trim();
            if (TextUtils.isEmpty(ipNumber)) {
                Toast.makeText(IpBroadcastActivity.this, "清除ip号码", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(IpBroadcastActivity.this, "修改ip号码", Toast.LENGTH_LONG).show();
            }
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString("ipnumber", ipNumber);
            edit.commit();
        }
    }
}
