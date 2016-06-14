package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

/**
 * Created by Administrator on 2016/6/8.
 */
public class BrokeClothesActivity extends Activity {

    private static final String TAG = "BrokeClothesActivity";
    private ImageView imageViewPre;
    private ImageView imageViewAfter;
    private Bitmap bitmapAfter;
    private Bitmap mutableBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.broke_clothes);
        Button button = (Button) findViewById(R.id.broke_clothes_btn);
        imageViewPre = (ImageView) findViewById(R.id.broke_clothes_pre);
        imageViewAfter = (ImageView) findViewById(R.id.broke_clothes_after);
        button.setOnClickListener(new LoadImageListener());
        Button buttonListener = (Button) findViewById(R.id.broke_clothes_listener_btn);
        buttonListener.setOnClickListener(new setListener());
    }

    private class LoadImageListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            String path = "storage/emulated/0/2.jpg";
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            int imageWidth = imageViewPre.getWidth();
            int imageHeight = imageViewPre.getHeight();
            Log.e(TAG, "zhaoxu imageWidth : " + imageWidth);
            Log.e(TAG, "zhaoxu imageHeight : " + imageHeight);
            BitmapFactory.decodeFile(path, options);
            int bitmapWidth = options.outWidth;
            int bitmapHeight = options.outHeight;
            Log.e(TAG, "zhaoxu bitmapWidth : " + bitmapWidth);
            Log.e(TAG, "zhaoxu bitmapHeight : " + bitmapHeight);
            int dx = bitmapWidth / imageWidth;
            int dy = bitmapHeight / imageHeight;
            Log.e(TAG, "zhaoxu dx : " + dx);
            Log.e(TAG, "zhaoxu dy : " + dy);
            int scale = 1;
            if ((dx > dy) && (dx > 1)) {
                scale = dx;
            } else if ((dy > dx) && (dy > 1)) {
                scale = dy;
            } else if ((dx == dy) && dx > 1) {
                scale = dx;
            }
            Log.e(TAG, "zhaoxu scale : " + scale);
            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;
            Bitmap preBitmap = BitmapFactory.decodeFile(path, options);
            imageViewPre.setImageBitmap(preBitmap);
            String pathAfter = "storage/emulated/0/1.jpg";
            bitmapAfter = BitmapFactory.decodeFile(pathAfter, options);
            mutableBitmap = bitmapAfter.createBitmap(bitmapAfter.getWidth(),bitmapAfter.getHeight(),bitmapAfter.getConfig());
            Canvas canvas = new Canvas(mutableBitmap);
            canvas.drawBitmap(bitmapAfter,new Matrix(),new Paint());
            imageViewAfter.setImageBitmap(mutableBitmap);
            final int finalScale = scale;
            imageViewAfter.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            Log.e(TAG, "zhaoxu ACTION_DOWN : " );
                            break;

                        case MotionEvent.ACTION_MOVE:
                            int x = (int) motionEvent.getX() / finalScale;
                            int y = (int) motionEvent.getY() / finalScale;
                            Log.e(TAG, "zhaoxu ACTION_MOVE : " );
                            Log.e(TAG, "zhaoxu ACTION_MOVE x : " + x );
                            Log.e(TAG, "zhaoxu ACTION_MOVE y : " + y );
                            try {
                                for(int i = -20; i < 21; i++) {
                                    for (int j = -20; j < 21; j++) {
                                        mutableBitmap.setPixel(x + i, y + j, Color.TRANSPARENT);
                                    }
                                }
                                imageViewAfter.setImageBitmap(mutableBitmap);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            break;
                    }
                    return true;
                }
            });
        }
    }

    private class setListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {

        }
    }
}
