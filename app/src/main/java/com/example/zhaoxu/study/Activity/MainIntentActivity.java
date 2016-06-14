package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * Created by Administrator on 2016/5/25.
 */
public class MainIntentActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_intent_activity);
        Button button = (Button) findViewById(R.id.main_intent_btn);
        button.setOnClickListener(new IntentListener());
    }

    private class IntentListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(),"com.example.zhaoxu.study.Activity.CaculatorActivity");
            startActivity(intent);
        }
    }
}
