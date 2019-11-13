package com.ztn.camera.session;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.bdrtmpsession.BDRtmpSessionBasic;
import com.baidu.cloud.bdrtmpsession.OnSessionEventListener;
import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.baidu.cloud.mediaprocess.muxer.FlvMuxer;
import com.ztn.camera.config.LiveConfig;
import com.ztn.camera.session.track.AudioCaptureSession;
import com.ztn.camera.session.track.ScreenCaptureSession;

import java.nio.ByteBuffer;
import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * 直播推流Session
 * 目前视频音频均会推流
 * <p>
 * contains AudioCaptureSession, VideoCaptureSession for input
 * contains flvmuxer for output
 * <p>
 * Created by Andy on 2017/5/31.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LiveScreenStreamSession extends HandlerThreadSession {
    public static final String TAG = "LiveScreenStreamSession";

    private AudioCaptureSession mAudioCaptureSession;
    private ScreenCaptureSession mScreenCaptureSession;

    private long epochTimeInNs = 0L;
    private int mAudioFrameDurationInUs;
    private int mVideoFrameDurationInUs;

    private volatile boolean mIsEncodeVideo = false;
    private volatile boolean mIsEncodeAudio = false;

    private Context mContext;
    private FlvMuxer mFlvMuxer;
    private BDRtmpSessionBasic mRtmpSession;

    private OnSessionEventListener mEventListener;

    private volatile boolean mIsStopped = true;
    private int mVideoFps = 0;

    private String mRtmpServerUrl;

    public LiveScreenStreamSession(Context context, LiveConfig liveConfig) {

        mContext = context;
        mIsEncodeVideo = liveConfig.isVideoEnabled();
        mIsEncodeAudio = liveConfig.isAudioEnabled();

        epochTimeInNs = System.nanoTime();
        mScreenCaptureSession = new ScreenCaptureSession(context, liveConfig.getVideoWidth(),
                liveConfig.getVideoHeight(),
                liveConfig.getCameraOrientation() % 180 != 0,
                liveConfig.getInitVideoBitrate(),
                liveConfig.getVideoFPS(),
                liveConfig.getGopLengthInSeconds(),
                mIsEncodeVideo);
        mScreenCaptureSession.setEpochTimeInNs(epochTimeInNs);

        mAudioCaptureSession = new AudioCaptureSession(context, liveConfig.getAudioSampleRate(), 2,
                liveConfig.getAudioBitrate(), MediaRecorder.AudioSource.DEFAULT, mIsEncodeAudio);
        mAudioCaptureSession.setEpochTimeInNs(epochTimeInNs);

        // set audio gain
        mAudioCaptureSession.setRecordTrackGain(liveConfig.getMicGain());
        mAudioCaptureSession.setBGMTrackGain(liveConfig.getMusicGain());

        mScreenCaptureSession.setInnerErrorListener(innerErrorListener);
        mAudioCaptureSession.setInnerErrorListener(innerErrorListener);

        mVideoFps = liveConfig.getVideoFPS();
        mVideoFrameDurationInUs = 1000000 / liveConfig.getVideoFPS();
        mAudioFrameDurationInUs = 1000000 * 1024 / liveConfig.getAudioSampleRate();
    }

    public void setRtmpEventListener(OnSessionEventListener listener) {
        mEventListener = listener;
    }

    public void setupDevice() {
        super.setupHandler();
        mAudioCaptureSession.startAudioDevice(); // start audio-record now, but now
        // videoCaptureSession will auto start when surfaceview created
    }

    public void configRtmpSession(String pushUrl, BDRtmpSessionBasic.UserRole role) {
        mRtmpServerUrl = pushUrl;
        mRtmpSession = new BDRtmpSessionBasic(role);
        mRtmpSession.setStreamingURL(mRtmpServerUrl);
        mRtmpSession.setUserId(mRtmpServerUrl.substring(mRtmpServerUrl.lastIndexOf('/') + 1));
        mRtmpSession.setEventListener(this.mEventListener);
        mRtmpSession.createStream();

        mFlvMuxer = new FlvMuxer(mRtmpSession.getRtmpSocket());
        mFlvMuxer.setFPS(mVideoFps);
        resetEpoch(epochTimeInNs);
    }

    public void destroyRtmpSession() {
        if (mFlvMuxer != null) {
            mFlvMuxer.setRtmpSocket(null);
        }
        mFlvMuxer = null;
        if (mRtmpSession != null) {
            mRtmpSession.destroyStream();
        }
        mRtmpSession = null;
    }

    public void startMediaProjection(int resultCode, Intent resultData) {
        mScreenCaptureSession.startMediaProjection(resultCode, resultData);
    }

    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
        mAudioCaptureSession.configBackgroundMusic(enableBGM, bgmPath, isLooping);
    }

    /**
     * 选择背景音乐区间
     *
     * @param clipStartInUSec    微秒
     * @param clipDurationInUSec 微秒
     */
    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
        mAudioCaptureSession.configBackgroundMusicClip(clipStartInUSec, clipDurationInUSec);
    }

    /**
     * start encoder and save/push
     * sync mode
     */
    public void startStreaming() {
        try {
            if (!mIsEncodeVideo && !mIsEncodeAudio) {
                Log.e(TAG, "not encode video and audio, LiveCaptureSession start failed!");
                innerErrorListener.onFinish(false, 0, "start failed;errorMsg=no video&audio enabled");
                return;
            }
            mIsStopped = false;

            mAudioCaptureSession.setMediaFormatChangedListener(mAudioMediaFormatChangeListener);
            mScreenCaptureSession.setMediaFormatChangedListener(mVideoMediaFormatChangeListener);

            mAudioCaptureSession.setOnEncodedFrameUpdateListener(mOnEncodedAudioFrameUpdateListener);
            mScreenCaptureSession.setOnEncodedFrameUpdateListener(mOnEncodedVideoFrameUpdateListener);

            if (!mAudioCaptureSession.startEncoder() || !mScreenCaptureSession.startEncoder()) {
                innerErrorListener.onFinish(false, 0, "Start encoder failed!");
                throw new RuntimeException("Start encoder failed! Please check your configuration!");
            }

        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            innerErrorListener.onFinish(false, 0, "start session failed; errorMsg=" + e.getMessage());
        }

    }

    /**
     * pauseBGM mp4 muxer
     * <p>
     * this function will not pauseBGM encoder
     */
    public void pauseStreaming() {
        mAudioCaptureSession.pauseBGM();

        resetMuxerTracks();
    }

    /**
     * resumeBGM mp4
     * <p>
     * this function will not resumeBGM encoder(encoder should be started)
     */
    public void resumeStreaming() {
        if (!isPaused) {
            // not paused before, so resumeBGM is invalid
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mScreenCaptureSession.requestKeyFrame();
        }
        mAudioCaptureSession.resumeBGM();
        isPaused = false;
    }

    /**
     * stop encoder and save/push
     */
    public void stopStreaming() {
        try {
            if (mIsStopped) {
                return;
            }
            mIsStopped = true;
            resetMuxerTracks();

            mScreenCaptureSession.stopEncoder();
            mAudioCaptureSession.stopEncoder();

            mAudioCaptureSession.setOnEncodedFrameUpdateListener(null);
            mScreenCaptureSession.setOnEncodedFrameUpdateListener(null);
            mMp4VideoTrack = -1;
            mMp4AudioTrack = -1;
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            innerErrorListener.onFinish(false, 0, "stop failed;errorMsg=" + e.getMessage());
        }
    }

    private MediaFormatChangedListener mAudioMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int audioTrackId = mFlvMuxer.addTrack(mediaFormat);
            Log.d(TAG, "audioTrackId = " + audioTrackId);
            startMuxerIfTracksUpdated(audioTrackId, true);
        }
    };

    private MediaFormatChangedListener mVideoMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int videoTrackId = mFlvMuxer.addTrack(mediaFormat);
            Log.d(TAG, "videoTrackId = " + videoTrackId);
            startMuxerIfTracksUpdated(videoTrackId, false);
        }
    };

    // TODO:: need more test as we may lost first I-frame if AudioMediaFormatChangeListener notified later
    private void startMuxerIfTracksUpdated(int trackId, boolean isAudio) {
        if (isAudio) {
            mMp4AudioTrack = trackId;
        } else {
            mMp4VideoTrack = trackId;
        }
        if ((!mIsEncodeAudio || mMp4AudioTrack != -1)
                && (!mIsEncodeVideo || mMp4VideoTrack != -1)) {
            isPaused = false;
        }
    }

    private void resetEpoch(long epochInNs) {
        if (mFlvMuxer != null) {
            mFlvMuxer.setEpoch(epochInNs);
        }
    }

    private void resetMuxerTracks() {
        isPaused = true;
        isKeyFrameFound = false;

        needAdjustAudioPts = true;
        needAdjustVideoPts = true;
    }

    private volatile boolean isKeyFrameFound = false;
    private volatile boolean isPaused = true;
    private volatile long audioPtsGapInUs = 0L;
    private volatile long videoPtsGapInUs = 0L;
    private volatile boolean needAdjustAudioPts = true;
    private volatile boolean needAdjustVideoPts = true;
    private volatile long latestVideoPtsInUs = 0L;
    private volatile long latestAudioPtsInUs = 0L;

    private volatile int mMp4VideoTrack = -1;
    private volatile int mMp4AudioTrack = -1;
    public static final int NALU_TYPE_IDR = 5;

    private OnEncodedFrameUpdateListener mOnEncodedVideoFrameUpdateListener = new OnEncodedFrameUpdateListener() {

        @Override
        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mFlvMuxer != null && mMp4VideoTrack >= 0 && !isPaused) {
                if (mIsEncodeVideo && !isKeyFrameFound) {
                    if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                        isKeyFrameFound = true;
                    } else {
                        // need to wait for key frame(IDR)
                        Log.w(TAG, "onEncodedFrameUpdate: video frame dropped as I-frame not ready.");
                        return;
                    }
                }
                try {
                    long pts = bufferInfo.presentationTimeUs;

                    if (pts == 0 && bufferInfo.size < 100) {
                        // pts is 0 and is sps,pps; we already have this in output format
                        return;
                    }
                    if (needAdjustVideoPts) {
                        latestVideoPtsInUs += mVideoFrameDurationInUs;
                        latestAudioPtsInUs += mAudioFrameDurationInUs;
                        videoPtsGapInUs = pts - latestVideoPtsInUs;
                        audioPtsGapInUs = pts - latestAudioPtsInUs;
                        needAdjustAudioPts = false;
                        needAdjustVideoPts = false;
                        resetEpoch(epochTimeInNs + 1000 * videoPtsGapInUs);
                    }

                    bufferInfo.presentationTimeUs = pts - videoPtsGapInUs;
                    latestVideoPtsInUs = bufferInfo.presentationTimeUs;
                    mFlvMuxer.writeSampleData(mMp4VideoTrack, bufferData, bufferInfo);
                } catch (Exception e) {
                    Log.e(TAG, "mediamuxer write video sample failed. errorMsg=" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    };

    private OnEncodedFrameUpdateListener mOnEncodedAudioFrameUpdateListener = new OnEncodedFrameUpdateListener() {

        @Override
        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mFlvMuxer != null && mMp4AudioTrack >= 0 && !isPaused) {
                if (mIsEncodeVideo && !isKeyFrameFound) {
                    // need to wait for video key frame
                    return;
                }
                try {
                    long pts = bufferInfo.presentationTimeUs;

                    if (pts == 0 && bufferInfo.size < 10) {
                        // pts is 0 and is audio-config info; we already have this in output format
                        return;
                    }
                    if (needAdjustAudioPts) {
                        latestVideoPtsInUs += mVideoFrameDurationInUs;
                        latestAudioPtsInUs += mAudioFrameDurationInUs;
                        videoPtsGapInUs = pts - latestVideoPtsInUs;
                        audioPtsGapInUs = pts - latestAudioPtsInUs;
                        needAdjustAudioPts = false;
                        needAdjustVideoPts = false;
                        resetEpoch(epochTimeInNs + 1000 * videoPtsGapInUs);
                    }

                    bufferInfo.presentationTimeUs = pts - audioPtsGapInUs;
                    latestAudioPtsInUs = bufferInfo.presentationTimeUs;
                    mFlvMuxer.writeSampleData(mMp4AudioTrack, bufferData, bufferInfo);
                } catch (Exception e) {
                    Log.e(TAG, "mediamuxer write audio sample failed.");
                    e.printStackTrace();
                }
            }
        }
    };

    private OnFinishListener innerErrorListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            if (!isSuccess) {
                Message message = new Message();
                message.what = Constraints.MSG_FAILED;
                message.arg1 = whatReason;
                message.obj = extraNote;
                sendMessageToHandlerThread(message);
            }
        }
    };

    public Bitmap getScreenShot() {
        if (mScreenCaptureSession != null) {
            return mScreenCaptureSession.getScreenShot();
        }
        return null;
    }

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mScreenCaptureSession.setGPUImageFilters(filters);
    }

    public void releaseDevice() {
        // camera will auto-release when surfaceDestroyed, but we allow reset
        super.destroyHandler();
        mScreenCaptureSession.stopVideoDevice();
        mAudioCaptureSession.stopAudioDevice();
    }


    /**
     * 设置录制音量的大小，默认是 1f
     *
     * @param gain 范围在 0f 到 1f 之间
     */
    public void setRecordTrackGain(float gain) {
        mAudioCaptureSession.setRecordTrackGain(gain);
    }

    /**
     * 设置背景音乐音量的大小，默认是 1f
     *
     * @param gain 范围在 0f 到 1f 之间
     */
    public void setBGMTrackGain(float gain) {
        mAudioCaptureSession.setBGMTrackGain(gain);
    }

    @Override
    protected void handleMessageInHandlerThread(Message msg) {
        switch (msg.what) {
            case Constraints.MSG_FAILED:
                // deal with failed
                if (mCaptureErrorListener != null) {
                    mCaptureErrorListener.onError(msg.arg1, msg.obj != null ? (String) (msg.obj) : "");
                }
                break;
            default:
                break;
        }
    }

    private volatile CaptureErrorListener mCaptureErrorListener;

    public void setCaptureErrorListener(CaptureErrorListener errorListener) {
        mCaptureErrorListener = errorListener;
    }

    public interface CaptureErrorListener {

        /**
         * notif when finish
         */
        void onError(int error, String desc);

    }
}
