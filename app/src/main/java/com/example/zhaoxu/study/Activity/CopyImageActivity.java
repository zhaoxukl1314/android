package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

/**
 * Created by Administrator on 2016/6/7.
 */
public class CopyImageActivity extends Activity {

    private static final String TAG = "CopyImageActivity";
    private ImageView imageViewDst;
    private Bitmap dstBitmap;
    private Bitmap loadImage;
    private ImageView imageViewSrc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.copy_image);
        Button button = (Button) findViewById(R.id.btn_copy_image);

        button.setOnClickListener(new CopyImageListener());
    }

    private class CopyImageListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            imageViewSrc = (ImageView) findViewById(R.id.image_copy_image_src);
            imageViewDst = (ImageView) findViewById(R.id.image_copy_image_dst);
            String path = "storage/emulated/0/2.jpg";

//            WindowManager wm = getWindowManager();
//            int ScreenWidth = wm.getDefaultDisplay().getWidth() / 2;
//            int ScreenHeight = wm.getDefaultDisplay().getHeight() / 2;
            int ScreenWidth = imageViewSrc.getWidth();
            int ScreenHeight = imageViewSrc.getHeight();
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
            loadImage = BitmapFactory.decodeFile(path, options);
            imageViewSrc.setImageBitmap(loadImage);

            dstBitmap = loadImage.createBitmap(loadImage.getWidth(), loadImage.getHeight(), loadImage.getConfig());


            Canvas canvas = new Canvas(dstBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);

            Matrix matrix = new Matrix();
//            matrix.setRotate(30,dstBitmap.getWidth()/2, dstBitmap.getHeight()/2);
            matrix.setScale(-1.0f,1.0f);
            matrix.postTranslate(dstBitmap.getWidth(),0);
            canvas.drawBitmap(loadImage,matrix,paint);
            imageViewDst.setImageBitmap(dstBitmap);
        }
    }
}
