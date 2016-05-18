package com.example.zhaoxu.study.netUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Created by Administrator on 2016/5/18.
 */
public class HtmlNetUtils {

    public static String getStringFromNet(String userName, String password) {
        String url = "http://10.0.0.2:8080/ServerItheima28/servlet/LoginServlet?username=" + userName + "&password=" + password;
        try {
            URL murl = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection) murl.openConnection();

            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setReadTimeout(10000);
            httpURLConnection.setConnectTimeout(5000);
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
                return html;
            } else {
                return null;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
