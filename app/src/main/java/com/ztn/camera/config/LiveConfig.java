package com.ztn.camera.config;

import android.util.Log;
import android.view.Surface;

/**
 * 推流配置类，配置摄像头、音视频码率帧率等信息
 *
 * @author baidu
 */
public class LiveConfig {

    public static final String TAG = "Baidu-LiveConfig";
    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;

    public static final int AUDIO_SAMPLE_RATE_44100 = 44100;
    public static final int AUDIO_SAMPLE_RATE_22050 = 22050;

    public static final int BEAUTY_EFFECT_LEVEL_NONE = 0;
    public static final int BEAUTY_EFFECT_LEVEL_A_NATURAL = 1;
    public static final int BEAUTY_EFFECT_LEVEL_B_WHITEN = 2;
    public static final int BEAUTY_EFFECT_LEVEL_C_PINK = 3;
    public static final int BEAUTY_EFFECT_LEVEL_D_MAGIC = 4;

    public static final class Builder {
        private int cameraId = CAMERA_FACING_FRONT; // 默认开启前置摄像头
        private int cameraOrientation = 0; // 默认竖屏
        private int outputOrientation = 0; // 产出视频的方向，最终产出的视频逆时针旋转的角度
        private int activityRotation = -1; // 窗口方向，有效值0，1，2，3.getWindowManager().getDefaultDisplay().getRotation()
        private boolean videoEnabled = true; // 默认开启视频
        private boolean audioEnabled = true; // 默认开启音频
        //        private boolean energySaving = false; // 默认不开启节电(开启后清晰度下降，码率不变)
        private int videoWidth = 1280; // 默认宽度1280
        private int videoHeight = 720; // 默认高度720
        private int videoFPS = 25; // 默认视频帧率25
        private int initVideoBitrate = 1024000; // 默认视频码率1024kb(单位为bit)
        private boolean enableQos = true; // 开启动态码率设置
        private int qosSensitivity = 5;
        private int maxVideoBitrate = 1024000; // 最高码率
        private int minVideoBitrate = 200000; // 最低码率
        private int audioSampleRate = AUDIO_SAMPLE_RATE_44100; // 默认音频采样率44100
        private int audioBitrate = 64000; // 默认音频码率64k(单位为bit)
        private int gopLengthInSeconds = 2; // 默认GOP长度2秒(即：两个I帧的间隔)
        private float micGain = 1.0f; // 默认音量增益为1.0
        private float musicGain = 1.0f; // 默认音乐音量增益为1.0

        /**
         * 设置摄像头ID，默认为前置摄像头LiveConfig.CAMERA_FACING_FRONT
         *
         * @param cameraId
         * @return
         */
        public final Builder setCameraId(int cameraId) {
            if (cameraId != CAMERA_FACING_BACK && cameraId != CAMERA_FACING_FRONT) {
                Log.e(TAG, "CameraId is not set, should be LiveConfig.CAMERA_FACING_BACK"
                        + " or LiveConfig.CAMERA_FACING_FRONT");
                return this;
            }
            this.cameraId = cameraId;
            return this;
        }

        /**
         * 设置摄像头方向，该参数最终用于Camera的setDisplayOrientation接口，具体含义请参考
         * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
         *
         * @param cameraOrientation
         * @return
         */
        public final Builder setCameraOrientation(int cameraOrientation) {
            if (cameraOrientation % 90 != 0 || cameraOrientation < 0 || cameraOrientation > 270) {
                Log.e(TAG, "CameraOrientation is not set, should be one of [0, 90, 180, 270]");
                return this;
            }
            this.cameraOrientation = cameraOrientation;
            return this;
        }

        /**
         * 设置产出的视频的逆时针旋转方向，该参数最终用于VideoFilter的setEncodeSize接口
         *
         * @param outputOrientation
         * @return
         */
        public final Builder setOutputOrientation(int outputOrientation) {
            if (outputOrientation % 90 != 0 || outputOrientation < 0 || outputOrientation > 270) {
                Log.e(TAG, "OutputOrientation is not set, should be one of [0, 90, 180, 270]");
                return this;
            }
            this.outputOrientation = outputOrientation;
            return this;
        }

        /**
         * 设置界面方向，以便特殊手机上的相机朝向正常。接口已经被废弃，请使用setCameraOrientation
         * 传入的值为getWindowManager().getDefaultDisplay().getRotation()
         *
         * @param activityRotation
         * @return
         */
        @Deprecated
        public final Builder setActivityRotation(int activityRotation) {
            if (activityRotation < Surface.ROTATION_0 || activityRotation > Surface.ROTATION_270) {
                Log.e(TAG, "ActivityRotation is not set, should get rotation through "
                        + " getWindowManager().getDefaultDisplay().getRotation()");
                return this;
            }
            this.activityRotation = activityRotation;
            return this;
        }

        /**
         * 是否往服务器推视频，默认为true
         *
         * @param videoEnabled
         * @return
         */
        public final Builder setVideoEnabled(boolean videoEnabled) {
            this.videoEnabled = videoEnabled;
            return this;
        }

        /**
         * 是否往服务器推音频，默认为true
         *
         * @param audioEnabled
         * @return
         */
        public final Builder setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
            return this;
        }

//        /**
//         * 是否开启节电(开启后编码级别为baseline，清晰度下降，码率不变)，默认为关闭
//         * @param energySaving
//         * @return
//         */
//        public final Builder setEnergySaving(boolean energySaving) {
//            this.energySaving = energySaving;
//            return this;
//        }

        /**
         * 设置输出视频宽度，跟相机和Activity的方向没有关系
         *
         * @param videoWidth
         * @return
         */
        public final Builder setVideoWidth(int videoWidth) {
            if (videoWidth < 0 || videoWidth > 4096) {
                Log.e(TAG, "videoWidth is not set, should be in [1, 4096]");
                return this;
            }
            this.videoWidth = videoWidth;
            return this;
        }

        /**
         * 设置输出视频高度，跟相机和Activity的方向没有关系
         *
         * @param videoHeight
         * @return
         */
        public final Builder setVideoHeight(int videoHeight) {
            if (videoHeight < 0 || videoHeight > 4096) {
                Log.e(TAG, "videoHeight is not set, should be in [1, 4096]");
                return this;
            }
            this.videoHeight = videoHeight;
            return this;
        }

        /**
         * 设置视频帧率
         *
         * @param videoFPS
         * @return
         */
        public final Builder setVideoFPS(int videoFPS) {
            if (videoFPS < 0 || videoFPS > 30) {
                Log.e(TAG, "videoFPS is not set, should be in [1, 30]");
                return this;
            }
            this.videoFPS = videoFPS;
            return this;
        }

        /**
         * 设置视频码率
         *
         * @param initVideoBitrate
         * @return
         */
        public final Builder setInitVideoBitrate(int initVideoBitrate) {
            if (initVideoBitrate < 100000) {
                Log.e(TAG, "initVideoBitrate is not set, should be larger than 100000");
                return this;
            }
            this.initVideoBitrate = initVideoBitrate;
            return this;
        }

        /**
         * 开启或关闭动态码率自动调整
         *
         * @param qosEnabled true为开启；false为关闭
         * @return
         */
        public final Builder setQosEnabled(boolean qosEnabled) {
            this.enableQos = qosEnabled;
            return this;
        }

        /**
         * 设置动态码率灵敏度-每隔多长时间检测一次
         *
         * @param qosSensitivity 检测间隔
         * @return
         */
        public final Builder setQosSensitivity(int qosSensitivity) {
            if (qosSensitivity < 5 || qosSensitivity > 10) {
                Log.e(TAG, "qosSensitivity is not set, should be between [5, 10]");
            }
            this.qosSensitivity = qosSensitivity;
            return this;
        }

        /**
         * 动态码率设置-视频最大码率
         *
         * @param maxVideoBitrate
         * @return
         */
        public final Builder setMaxVideoBitrate(int maxVideoBitrate) {
            if (maxVideoBitrate < 100000) {
                Log.e(TAG, "maxVideoBitrate is not set, should be larger than 100000");
                return this;
            }
            this.maxVideoBitrate = maxVideoBitrate;
            return this;
        }

        /**
         * 动态码率设置-视频最小码率
         *
         * @param minVideoBitrate
         * @return
         */
        public final Builder setMinVideoBitrate(int minVideoBitrate) {
            if (minVideoBitrate < 100000) {
                Log.e(TAG, "minVideoBitrate is not set, should be larger than 100000");
                return this;
            }
            this.minVideoBitrate = minVideoBitrate;
            return this;
        }

        /**
         * 设置音频采样率
         *
         * @param audioSampleRate
         * @return
         */
        public final Builder setAudioSampleRate(int audioSampleRate) {
            if (audioSampleRate != AUDIO_SAMPLE_RATE_44100 && audioSampleRate != AUDIO_SAMPLE_RATE_22050) {
                Log.e(TAG, "audioSampleRate is not set, should be LiveConfig.AUDIO_SAMPLE_RATE_44100"
                        + " or LiveConfig.AUDIO_SAMPLE_RATE_22050 or LiveConfig.AUDIO_SAMPLE_RATE_11025");
                return this;
            }
            this.audioSampleRate = audioSampleRate;
            return this;
        }

        /**
         * 设置音频码率
         *
         * @param audioBitrate
         * @return
         */
        public final Builder setAudioBitrate(int audioBitrate) {
            if (audioBitrate < 10000) {
                Log.e(TAG, "audioBitrate is not set, should be larger than 10000");
                return this;
            }
            this.audioBitrate = audioBitrate;
            return this;
        }

        /**
         * 设置I帧间隔时长，单位为秒，默认为2秒
         *
         * @param gopLengthInSeconds
         * @return
         */
        public final Builder setGopLengthInSeconds(int gopLengthInSeconds) {
            if (gopLengthInSeconds < 0) {
                Log.e(TAG, "gopLengthInSeconds is not set, should be larger than 0");
                return this;
            }
            this.gopLengthInSeconds = gopLengthInSeconds;
            return this;
        }


        public final Builder setMicGain(float micGain) {
            micGain = Math.max(0f, Math.min(1.0f, micGain));
            this.micGain = micGain;
            return this;
        }

        public final Builder setMusicGain(float musicGain) {
            musicGain = Math.max(0f, Math.min(1.0f, musicGain));
            this.musicGain = musicGain;
            return this;
        }

        /**
         * 构造出LiveConfig类
         *
         * @return
         */
        public LiveConfig build() {
            return new LiveConfig(this);
        }

    }

    /**
     * 以下变量的初值均来自Builder
     */
    private final int cameraId;
    private final int cameraOrientation;
    private final int outputOrientation;
    private final int activityRotation;
    private final boolean videoEnabled;
    private final boolean audioEnabled;
    //    private final boolean energySaving;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoFPS;
    private final int initVideoBitrate;
    private final boolean qosEnabled;
    private final int qosSensitivity;
    private final int maxVideoBitrate;
    private final int minVideoBitrate;
    private final int audioSampleRate;
    private final int audioBitrate;
    private final int gopLengthInSeconds;
    private final float micGain;
    private final float musicGain;

    private LiveConfig(Builder builder) {
        this.cameraId = builder.cameraId;
        this.cameraOrientation = builder.cameraOrientation;
        this.outputOrientation = builder.outputOrientation;
        this.activityRotation = builder.activityRotation;
        this.videoEnabled = builder.videoEnabled;
        this.audioEnabled = builder.audioEnabled;
//        this.energySaving = builder.energySaving;
        this.videoWidth = builder.videoWidth;
        this.videoHeight = builder.videoHeight;
        this.videoFPS = builder.videoFPS;
        this.initVideoBitrate = builder.initVideoBitrate;
        this.qosEnabled = builder.enableQos;
        this.qosSensitivity = builder.qosSensitivity;
        this.maxVideoBitrate = builder.maxVideoBitrate;
        this.minVideoBitrate = builder.minVideoBitrate;
        this.audioSampleRate = builder.audioSampleRate;
        this.audioBitrate = builder.audioBitrate;
        this.gopLengthInSeconds = builder.gopLengthInSeconds;
        this.micGain = builder.micGain;
        this.musicGain = builder.musicGain;
    }

    // 以下为LiveConfig所有变量的get区域

    public int getCameraId() {
        return cameraId;
    }

    public int getCameraOrientation() {
        return cameraOrientation;
    }

    public int getOutputOrientation() {
        return outputOrientation;
    }

    public int getActivityRotation() {
        return activityRotation;
    }

    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    public boolean isAudioEnabled() {
        return audioEnabled;
    }

//    public boolean isEnergySaving() {
//        return energySaving;
//    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getVideoFPS() {
        return videoFPS;
    }

    public int getInitVideoBitrate() {
        return initVideoBitrate;
    }

    public int getMinVideoBitrate() {
        return minVideoBitrate;
    }

    public boolean isQosEnabled() {
        return qosEnabled;
    }

    public int getQosSensitivity() {
        return qosSensitivity;
    }

    public int getMaxVideoBitrate() {
        return maxVideoBitrate;
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public int getAudioBitrate() {
        return audioBitrate;
    }

    public int getGopLengthInSeconds() {
        return gopLengthInSeconds;
    }

    public float getMicGain() {
        return micGain;
    }

    public float getMusicGain() {
        return musicGain;
    }

    public String toString() {
        StringBuilder sder = new StringBuilder();
        sder.append("LiveConfig:cameraId=").append(cameraId);
        sder.append(";cameraOrientation=").append(cameraOrientation);
        sder.append(";outputOrientation=").append(outputOrientation);
        sder.append(";activityRotation=").append(activityRotation);
        sder.append(";videoEnabled=").append(videoEnabled);
        sder.append(";audioEnabled=").append(audioEnabled);
//        sder.append(";energySaving=").append(energySaving);
        sder.append(";videoWidth=").append(videoWidth);
        sder.append(";videoHeight=").append(videoHeight);
        sder.append(";videoFPS=").append(videoFPS);
        sder.append(";initVideoBitrate=").append(initVideoBitrate);
        sder.append(";qosEnabled=").append(qosEnabled);
        sder.append(";qosSensitivity=").append(qosSensitivity);
        sder.append(";maxVideoBitrate=").append(maxVideoBitrate);
        sder.append(";minVideoBitrate=").append(minVideoBitrate);
        sder.append(";audioSampleRate=").append(audioSampleRate);
        sder.append(";audioBitrate=").append(audioBitrate);
        sder.append(";gopLengthInSeconds=").append(gopLengthInSeconds);
        return sder.toString();
    }
}
