package com.ztn.camera;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.baidu.cloud.media.player.IMediaPlayer;
import com.ztn.camera.widget.BDCloudVideoView;

public class PlayActivity extends AppCompatActivity {

    private static final String TAG = PlayActivity.class.getSimpleName();
    private String playUrl = "rtmp://120.78.131.33/rtmplive/ztn0000000001";
    private BDCloudVideoView bdVideoView ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        win.requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_play);
        bdVideoView = findViewById(R.id.id_play);
        /**
         * 注册listener
         */
        bdVideoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener(){

            @Override
            public void onCompletion(IMediaPlayer iMediaPlayer) {
                Log.e(TAG,"准备好");
            }
        });
        bdVideoView.setOnErrorListener(new IMediaPlayer.OnErrorListener(){

            @Override
            public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
                Log.e(TAG,"错误"+i+"---"+i1);
                return false;
            }
        });

        bdVideoView.setVideoScalingMode(BDCloudVideoView.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        bdVideoView.setVideoPath(playUrl);
        bdVideoView.reSetRender();
        bdVideoView.start();

    }


    @Override
    protected void onDestroy() {
        if (bdVideoView.isPlaying()) {
            bdVideoView.stopPlayback();

        }
        if (null!=bdVideoView){
            bdVideoView.reSetRender(); // 清除上一个播放源的最后遗留的一帧
            bdVideoView.release();
        }
        super.onDestroy();
    }

}
