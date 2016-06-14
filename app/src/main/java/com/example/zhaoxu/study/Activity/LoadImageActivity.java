package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

/**
 * Created by Administrator on 2016/6/6.
 */
public class LoadImageActivity extends Activity {

    private static final String TAG = "LoadImageActivity";
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.load_image);
        Button button = (Button) findViewById(R.id.btn_load_image);
        imageView = (ImageView) findViewById(R.id.image_load_image);
        button.setOnClickListener(new LoadImageListener());
    }

    private class LoadImageListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            String path = "storage/emulated/0/2.jpg";
            WindowManager wm = getWindowManager();
            int ScreenWidth = wm.getDefaultDisplay().getWidth();
            int ScreenHeight = wm.getDefaultDisplay().getHeight();
            Log.e(TAG,"zhaoxu ScreenWidth : " + ScreenWidth );
            Log.e(TAG,"zhaoxu ScreenHeight : " + ScreenHeight );

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            int BitmapWidth =options.outWidth;
            int BitmapHeight = options.outHeight;
            Log.e(TAG,"zhaoxu BitmapWidth : " + BitmapWidth );
            Log.e(TAG,"zhaoxu BitmapHeight : " + BitmapHeight );

            int dx = BitmapWidth / ScreenWidth;
            int dy = BitmapHeight / ScreenHeight;
            Log.e(TAG,"zhaoxu dx : " + dx );
            Log.e(TAG,"zhaoxu dy : " + dy );
            int scale;

            if (dx > dy) {
                scale = dx;
            } else {
                scale = dy;
            }
            options.inSampleSize = scale;
            options.inJustDecodeBounds = false;
            Bitmap loadImage = BitmapFactory.decodeFile(path, options);
            imageView.setImageBitmap(loadImage);
        }
    }
}
