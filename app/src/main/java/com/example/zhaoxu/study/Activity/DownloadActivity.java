package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.zhaoxu.study.Activity.R;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Administrator on 2016/5/22.
 */
public class DownloadActivity extends Activity{

    private static final int THREAD_COUNT = 3;
    private static final String TAG = "DownloadActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_from_net);
        Button btnDownload = (Button) findViewById(R.id.btn_download);
    }

    private class DownLoadClickListner implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            EditText etDownload = (EditText) findViewById(R.id.et_download);
            final String path = etDownload.getText().toString().trim();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    DownloadFromNet(path);
                }
            }).start();
        }
    }

    private void DownloadFromNet(final String path) {
        try {
            URL mUrl = new URL(path);
            HttpURLConnection connect = (HttpURLConnection) mUrl.openConnection();
            connect.setRequestMethod("GET");
            connect.setConnectTimeout(5000);
            connect.setReadTimeout(5000);
            int responseCode = connect.getResponseCode();
            if (responseCode == 200) {
                long size = connect.getContentLength();
                File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                raf.setLength(size);
                long blockSize = size / THREAD_COUNT;
                for (int i = 1; i <= THREAD_COUNT; i++ ) {
                    final long startIndex = (i-1) * blockSize;
                    long endIndex = i * blockSize - 1;
                    if (i == THREAD_COUNT) {
                        endIndex = size;
                    }
                    final int finalI = i;
                    final long finalEndIndex = endIndex;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG,"thread run : " + finalI);
                            new DownloadThread(finalI,startIndex, finalEndIndex,path).run();
                        }
                    }).start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileName(String path) {
        int start = path.lastIndexOf("/") + 1;
        return path.substring(start);
    }

    private class DownloadThread implements Runnable {
        private int threadId;
        private long startIndex;
        private long endIndex;
        private String path;

        public DownloadThread(int threadId, long startIndex, long endIndex, String path) {
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.path = path;
        }

        @Override
        public void run() {
            Log.e(TAG,"第" + threadId + "个线程开始");
            InputStream inputStream = null;
            RandomAccessFile randomAccessFile = null;
            try {
                int total = 0;
                File tempFile = new File(Environment.getExternalStorageDirectory(),getFileName(path) + threadId + ".txt");
                URL mUrl = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int responseCode = conn.getResponseCode();
                conn.setRequestProperty("Range","bytes=" + startIndex + "-" + endIndex);

                if (200 == responseCode) {
                    inputStream = conn.getInputStream();
                    File downloadFile = new File(Environment.getExternalStorageDirectory(),getFileName(path));
                    randomAccessFile = new RandomAccessFile(downloadFile,"rw");
                    randomAccessFile.seek(startIndex);
                    int len = -1;
                    byte[] buffer = new byte[1024 * 1024];
                    while ((len = inputStream.read(buffer)) != -1) {
                        randomAccessFile.write(buffer,0,len);
                    }
                    inputStream.close();
                    randomAccessFile.close();
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
            }
        }
    }
}
