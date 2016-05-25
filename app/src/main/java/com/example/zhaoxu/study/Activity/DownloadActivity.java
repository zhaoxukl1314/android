package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.zhaoxu.study.Activity.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2016/5/22.
 */
public class DownloadActivity extends Activity{

    private static final String TAG = "DownloadActivity";
    private static final int THREAD_ERROR = 1;
    private static final int DOWNLOAD_ERROR = 2;
    private static final int SUCCEED = 0;
    private int runningThread = 0;
    private int mThreadCount = 0;
    private List<ProgressBar> mList;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SUCCEED:
                    Toast.makeText(DownloadActivity.this,"下载完成",Toast.LENGTH_LONG).show();
                    break;

                case THREAD_ERROR:
                    Toast.makeText(DownloadActivity.this, "线程错误", Toast.LENGTH_LONG).show();
                    break;

                case DOWNLOAD_ERROR:
                    Toast.makeText(DownloadActivity.this, "下载错误", Toast.LENGTH_LONG).show();
                    break;

                default:
                    Toast.makeText(DownloadActivity.this, "Unknown Error", Toast.LENGTH_LONG).show();
            }
        }
    };
    private LinearLayout lv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_from_net);
        Button btnDownload = (Button) findViewById(R.id.btn_download);
        btnDownload.setOnClickListener(new DownLoadClickListner());
        lv = (LinearLayout) findViewById(R.id.ll_download);
    }

    private class DownLoadClickListner implements View.OnClickListener {

        @Override
        public void onClick(View view) {

            EditText etDownload = (EditText) findViewById(R.id.et_download);
            final String path = etDownload.getText().toString().trim();
            EditText etThreadCount = (EditText) findViewById(R.id.et_threadcount);
            String sThreadCount = etThreadCount.getText().toString().trim();
            final int threadCount = Integer.parseInt(sThreadCount);
            mThreadCount = threadCount;
            mList = new ArrayList<ProgressBar>();
            for (int i = 0; i < mThreadCount; i++) {
                ProgressBar pb = (ProgressBar) View.inflate(DownloadActivity.this,R.layout.progress_bar_from_net,null);
                lv.addView(pb);
                mList.add(pb);
            }
            Toast.makeText(DownloadActivity.this, "下载开始", Toast.LENGTH_LONG).show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    DownloadFromNet(path, threadCount);
                }
            }).start();
        }
    }

    private void DownloadFromNet(final String path, int threadCount) {
        try {

            URL mUrl = new URL(path);
            HttpURLConnection connect = (HttpURLConnection) mUrl.openConnection();
            connect.setRequestMethod("GET");
            connect.setConnectTimeout(5000);
            int responseCode = connect.getResponseCode();
            if (responseCode == 200) {
                long size = connect.getContentLength();
                Log.e(TAG,"服务器文件大小 : " + size);
//                final String downloadPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "DCIM";
                final String downloadPath = "data/data/com.example.zhaoxu.study";
                File directory = new File(downloadPath);
                Log.e(TAG,"directory exist : " + directory.exists());
                Log.e(TAG,"Environment.getExternalStorageDirectory().getAbsolutePath() : " + downloadPath);
                File file = new File(downloadPath,getFileName(path));
                if (!file.exists()) {
                    file.createNewFile();
                    Log.e(TAG, "file exist : " + file.exists());
                }
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                raf.setLength(size);
                long blockSize = size / mThreadCount;
                runningThread = mThreadCount;
                for (int i = 1; i <= mThreadCount; i++ ) {
                    long startIndex = (i-1) * blockSize;
                    long endIndex = i * blockSize - 1;
                    if (i == mThreadCount) {
                        endIndex = size - 1;
                    }
                    int maxSize = (int) (endIndex - startIndex);
                    mList.get(i-1).setMax(maxSize);
                    Log.e(TAG,"thread run : " + i);
                    new DownloadThread(i,startIndex, endIndex,path, downloadPath).start();
                }
                connect.disconnect();
            }
        } catch (Exception e) {
            Message msg = Message.obtain();
            msg.what = THREAD_ERROR;
            mHandler.sendMessage(msg);
            e.printStackTrace();
        }
    }

    private String getFileName(String path) {
        int start = path.lastIndexOf("/") + 1;
        return path.substring(start);
    }

    private class DownloadThread extends Thread {
        private int threadId;
        private long startIndex;
        private long endIndex;
        private String path;
        private String downloadPath;

        public DownloadThread(int threadId, long startIndex, long endIndex, String path, String downloadPath) {
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.path = path;
            this.downloadPath = downloadPath;
        }

        @Override
        public void run() {
            Log.e(TAG,"第" + threadId + "个线程开始" + "开始位置 : " + startIndex + "结束位置 ： " + endIndex);
            InputStream inputStream = null;
            RandomAccessFile randomAccessFile = null;
            try {
                int total = 0;
                File tempFile = new File(downloadPath, getFileName(path) +threadId + ".txt");
                if (tempFile.exists() && tempFile.length() > 0) {
                    FileInputStream fileInputStream = new FileInputStream(tempFile);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
                    String lastIndex = br.readLine();
                    int lastIndexInt = Integer.valueOf(lastIndex);
                    startIndex += lastIndexInt;
                    total += lastIndexInt;
                }
                URL mUrl = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range","bytes=" + startIndex + "-" + endIndex);
                conn.setConnectTimeout(5000);
                int responseCode = conn.getResponseCode();
                Log.e(TAG,"responseCode : " + responseCode);
                inputStream = conn.getInputStream();
                File downloadFile = new File(downloadPath, getFileName(path));
                randomAccessFile = new RandomAccessFile(downloadFile, "rw");
                randomAccessFile.seek(startIndex);
                int len = -1;
                byte[] buffer = new byte[1024 * 1024];
                while ((len = inputStream.read(buffer)) != -1) {
                    randomAccessFile.write(buffer, 0, len);
                    RandomAccessFile rf = new RandomAccessFile(tempFile,"rwd");
                    total += len;
                    mList.get(threadId - 1).setProgress(total);
                    rf.write(String.valueOf(total).getBytes());
                    rf.close();
                }
                inputStream.close();
                randomAccessFile.close();
                conn.disconnect();
            } catch (Exception e) {
                Message msg = Message.obtain();
                msg.what = DOWNLOAD_ERROR;
                mHandler.sendMessage(msg);
                e.printStackTrace();
            } finally {
                synchronized (DownloadActivity.class) {
                    runningThread--;
                    if (runningThread < 1) {
                        for (int i = 1; i <= mThreadCount; i++) {
                            File f = new File(downloadPath, getFileName(path) + i + ".txt");
                            f.delete();
                            Log.e(TAG, "删除文件 ：" + i + "txt");
                            Message msg = Message.obtain();
                            msg.what = SUCCEED;
                            mHandler.sendMessage(msg);
                        }
                    }
                }
            }
            Log.e(TAG,"第" + threadId + "线程结束");
        }
    }
}
