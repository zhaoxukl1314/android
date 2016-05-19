package com.example.zhaoxu.study.netUtils;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Created by Administrator on 2016/5/18.
 */
public class HtmlNetUtils {

    private static final String TAG = "HtmlNetUtils";
    private static HttpURLConnection httpURLConnection;

    public static String getStringFromNet(String userName, String password) {
        String url = "http://10.0.2.2:8080/ServerItheima28/servlet/LoginServlet?username=" + URLEncoder.encode(userName) + "&password=" + URLEncoder.encode(password);
        try {
            URL murl = new URL(url);
            httpURLConnection = (HttpURLConnection) murl.openConnection();

            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.connect();
            int requestCode = httpURLConnection.getResponseCode();
            if (requestCode == 200){
                InputStream inputStream = httpURLConnection.getInputStream();
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                int len = -1;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteArray.write(buffer,0,len);
                }
                String html = byteArray.toString();
                inputStream.close();
                byteArray.close();
                Log.e(TAG,html);
                return html;
            } else {
                return null;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
        return null;
    }

    public static String postStringFromNet(String userName, String password) {
        String url = "http://10.0.2.2:8080/ServerItheima28/servlet/LoginServlet";
        String data = "username=" + userName + "&password=" + password;
        try {
            URL murl = new URL(url);
            httpURLConnection = (HttpURLConnection) murl.openConnection();

            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setDoOutput(true);
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(data.getBytes());
            outputStream.flush();
            outputStream.close();

            int requestCode = httpURLConnection.getResponseCode();
            if (requestCode == 200){
                InputStream inputStream = httpURLConnection.getInputStream();
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                int len = -1;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteArray.write(buffer,0,len);
                }
                String html = byteArray.toString();
                inputStream.close();
                byteArray.close();
                Log.e(TAG,html);
                return html;
            } else {
                return null;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
        return null;
    }
}
