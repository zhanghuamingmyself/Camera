package com.ztn.camera;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.SurfaceView;

import com.baidu.cloud.bdrtmpsession.BDRtmpSessionBasic;
import com.baidu.cloud.bdrtmpsession.OnSessionEventListener;
import com.ztn.camera.config.LiveConfig;
import com.ztn.camera.session.LiveStreamSession;

public class PushActivity extends AppCompatActivity {

    private LiveStreamSession mSession;
    private String pushUrl = "rtmp://120.78.131.33/rtmplive/ztn0000000005";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push);



// 配置录制参数
        LiveConfig.Builder builder = new LiveConfig.Builder();
        builder.setCameraOrientation(90) // 设置相机旋转参数，通常横屏传0度，竖屏传90度
                .setCameraId(LiveConfig.CAMERA_FACING_BACK) // 设置所使用的相机
                .setVideoWidth(720) // 设置输出视频的宽（像素个数）
                .setVideoHeight(1280) // 设置输出视频的高（像素个数）
                .setVideoFPS(15) // 设置帧率
                .setInitVideoBitrate(400000) // 设置视频编码初始码率
                .setMinVideoBitrate(100000) // 设置视频编码最低码率（用于动态码率控制）
                .setMaxVideoBitrate(800000) // 设置视频编码最高码率（用于动态码率控制）
                .setVideoEnabled(true)
                .setAudioSampleRate(44100)
                .setAudioBitrate(64000)
                .setAudioEnabled(true);
// 初始化录制Session
        mSession = new LiveStreamSession(this, builder.build());
        mSession.setRtmpEventListener(new OnSessionEventListener() {

            @Override
            public void onSessionConnected() {

            }

            @Override
            public void onError(int i) {

            }

            @Override
            public void onConversationRequest(String s, String s1) {

            }

            @Override
            public void onConversationStarted(String s) {

            }

            @Override
            public void onConversationFailed(String s, FailureReason failureReason) {

            }

            @Override
            public void onConversationEnded(String s) {

            }
        });
// 绑定SurfaceHolder
        SurfaceView surfaceView = findViewById(R.id.local_preview);
        mSession.setSurfaceHolder(surfaceView.getHolder());
// 初始化录音与捕获视频的device. 在创建Session时仅调用一次；
        mSession.setupDevice();
// 配置推流地址以及房间角色（默认为大主播）
        mSession.configRtmpSession(pushUrl, BDRtmpSessionBasic.UserRole.Host);

        // 开始推流
        mSession.startStreaming();


    }

    @Override
    protected void onDestroy() {
        // 销毁，不再使用时调用
        mSession.destroyRtmpSession();
        mSession.releaseDevice();
        super.onDestroy();
    }
}
