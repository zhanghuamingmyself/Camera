package com.ztn.camera.config;

import android.util.Log;

/**
 * 推流配置类，配置摄像头、音视频码率帧率等信息
 *
 * @author baidu
 */
public class ProcessConfig {

    private static final String TAG = "Baidu-ProcessConfig";

    public static final int AUDIO_SAMPLE_RATE_44100 = 44100;
    public static final int AUDIO_SAMPLE_RATE_22050 = 22050;

    public static final int BEAUTY_EFFECT_LEVEL_NONE = 0;
    public static final int BEAUTY_EFFECT_LEVEL_A_NATURAL = 1;
    public static final int BEAUTY_EFFECT_LEVEL_B_WHITEN = 2;
    public static final int BEAUTY_EFFECT_LEVEL_C_PINK = 3;
    public static final int BEAUTY_EFFECT_LEVEL_D_MAGIC = 4;

    public static final class Builder {
        private boolean videoEnabled = true; // 默认开启视频
        private boolean audioEnabled = true; // 默认开启音频
        //        private boolean energySaving = false; // 默认不开启节电(开启后清晰度下降，码率不变)
        private int videoWidth = 1280; // 默认宽度1280
        private int videoHeight = 720; // 默认高度720
        private int videoFPS = 25; // 默认视频帧率25
        private int initVideoBitrate = 1024000; // 默认视频码率1024kb(单位为bit)
        private int playbackRate = 1; // 默认的播放速度(范围在[1,9])

        private int audioSampleRate = AUDIO_SAMPLE_RATE_44100; // 默认音频采样率44100
        private int audioChannelCount = 2; // 默认声道数2
        private int audioBitrate = 64000; // 默认音频码率64k(单位为bit)
        private int gopLengthInSeconds = 2; // 默认GOP长度2秒(即：两个I帧的间隔)
        private float micGain = 1.0f; // 默认音量增益为1.0
        private float musicGain = 1.0f; // 默认音乐音量增益为1.0

        /**
         * 解码编码视频，默认为true
         *
         * @param videoEnabled
         * @return
         */
        public final Builder setVideoEnabled(boolean videoEnabled) {
            this.videoEnabled = videoEnabled;
            return this;
        }

        /**
         * 解码编码音频，默认为true
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
         * 设置生成目标的视频宽度
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
         * 设置生成目标的视频高度
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
         * 设置生成目标的视频帧率
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
         * 设置生成目标的视频码率
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

//        /**
//         * 设置生成目标的音频采样率
//         * @param audioSampleRate
//         * @return
//         */
//        public final Builder setAudioSampleRate(int audioSampleRate) {
//            this.audioSampleRate = audioSampleRate;
//            return this;
//        }
//
//        /**
//         * 设置生成目标的音频声道数，默认为2
//         * @param channelCount
//         * @return
//         */
//        public final Builder setAudioChannelCount(int channelCount) {
//            if (audioChannelCount < 1 || audioChannelCount > 2) {
//                Log.e(TAG, "audioChannelCount is not set, should be 1 or 2");
//                return this;
//            }
//            this.audioChannelCount = channelCount;
//            return this;
//        }

        /**
         * 设置生成目标的音频码率
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

        public final Builder setPlaybackRate(int playbackRate) {
            if (playbackRate < 1 || playbackRate > 9) {
                Log.e(TAG, "playbackRate must in [1,9]");
                return this;
            }
            this.playbackRate = playbackRate;
            return this;
        }

        /**
         * 构造出LiveConfig类
         *
         * @return
         */
        public ProcessConfig build() {
            return new ProcessConfig(this);
        }

    }

    /**
     * 以下变量的初值均来自Builder
     */
    private final boolean videoEnabled;
    private final boolean audioEnabled;
    //    private final boolean energySaving;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoFPS;
    private final int initVideoBitrate;
    private final int playbackRate;

    private final int audioSampleRate;
    private final int audioChannelCount;
    private final int audioBitrate;
    private final int gopLengthInSeconds;
    private final float micGain;
    private final float musicGain;

    private ProcessConfig(Builder builder) {
        this.videoEnabled = builder.videoEnabled;
        this.audioEnabled = builder.audioEnabled;
//        this.energySaving = builder.energySaving;
        this.videoWidth = builder.videoWidth;
        this.videoHeight = builder.videoHeight;
        this.videoFPS = builder.videoFPS;
        this.initVideoBitrate = builder.initVideoBitrate;
        this.playbackRate = builder.playbackRate;
        this.audioSampleRate = builder.audioSampleRate;
        this.audioChannelCount = builder.audioChannelCount;
        this.audioBitrate = builder.audioBitrate;
        this.gopLengthInSeconds = builder.gopLengthInSeconds;
        this.micGain = builder.micGain;
        this.musicGain = builder.musicGain;
    }

    // 以下为LiveConfig所有变量的get区域

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

    public int getPlaybackRate() {
        return playbackRate;
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public int getAudioChannelCount() {
        return audioChannelCount;
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
        sder.append("LiveConfig:videoEnabled=").append(videoEnabled);
        sder.append(";audioEnabled=").append(audioEnabled);
//        sder.append(";energySaving=").append(energySaving);
        sder.append(";videoWidth=").append(videoWidth);
        sder.append(";videoHeight=").append(videoHeight);
        sder.append(";videoFPS=").append(videoFPS);
        sder.append(";initVideoBitrate=").append(initVideoBitrate);
        sder.append(";playbackRate=").append(playbackRate);
        sder.append(";audioSampleRate=").append(audioSampleRate);
        sder.append(";audioChannelCount=").append(audioChannelCount);
        sder.append(";audioBitrate=").append(audioBitrate);
        sder.append(";gopLengthInSeconds=").append(gopLengthInSeconds);
        return sder.toString();
    }
}
