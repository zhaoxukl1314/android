package com.example.zhaoxu.study.Activity;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

/**
 * Created by Administrator on 2016/6/14.
 */
public class VideoPlayerActivity extends Activity {

    private EditText editText;
    private MediaPlayer mediaPlayer;
    private Button musicPlay;
    private Button musicPause;
    private SurfaceView surfaceView;
    private SurfaceHolder holder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_player);
        editText = (EditText) findViewById(R.id.music_edt);
        musicPlay = (Button) findViewById(R.id.music_play);
        musicPause = (Button) findViewById(R.id.music_pause);
        surfaceView = (SurfaceView) findViewById(R.id.video_surface);
        holder = surfaceView.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void play(View view) {
        String path = editText.getText().toString().trim();
        File file = new File(path);
        if (file.exists()) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(path);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDisplay(holder);
                mediaPlayer.prepare();
                mediaPlayer.start();
                musicPlay.setEnabled(false);
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        musicPlay.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this,"播放错误",Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this,"文件不存在",Toast.LENGTH_LONG).show();
        }
    }

    public void pause(View view) {
        if ("继续".equals(musicPause.getText())) {
            mediaPlayer.start();
            musicPause.setText("暂停");
            return;
        }

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            musicPause.setText("继续");
        }
    }

    public void stop(View view) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
            musicPlay.setEnabled(true);
            musicPause.setText("暂停");
        }
    }

    public void replay(View view) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(0);
        } else {
            play(view);
        }
    }
}
