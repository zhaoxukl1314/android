package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/**
 * Created by Administrator on 2016/6/16.
 */
public class DialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog);
    }

    public void openDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("对话框");
        builder.setMessage("打开了对话框");
        builder.setPositiveButton("确定",new PositiveClickListener());
        builder.setNegativeButton("取消",new NegativeClickListener());
        builder.create().show();
    }

    private class PositiveClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            Toast.makeText(DialogActivity.this,"点击确定",Toast.LENGTH_LONG).show();
        }
    }

    private class NegativeClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {

        }
    }

    public void openSelectedDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("单选对话框");
        final String[] items = {"男", "女", "未知"};
        builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Toast.makeText(DialogActivity.this,"性别为"  + items[i],Toast.LENGTH_LONG).show();
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }

    public void openMultipleDialog(View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择喜欢的水果");
        final String[] items = {"西瓜", "荔枝", "樱桃", "橘子", "香瓜"};
        final boolean[] checkedItems = {true,true,true,false,false};
        builder.setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                checkedItems[i] = b;
            }
        });
        builder.setPositiveButton("提交", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                StringBuffer stringBuffer = new StringBuffer();
                for (int index = 0; index < checkedItems.length; index++) {
                    if (checkedItems[index]) {
                        stringBuffer.append(items[index] + ",");
                    }
                }
                Toast.makeText(DialogActivity.this,"喜欢的水果是" + stringBuffer,Toast.LENGTH_LONG).show();
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }
}
