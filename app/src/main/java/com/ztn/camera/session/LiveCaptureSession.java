package com.ztn.camera.session;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.baidu.cloud.gesturedetector.FaceDetector;
import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.config.LiveConfig;
import com.ztn.camera.session.track.AudioCaptureSession;
import com.ztn.camera.session.track.VideoCaptureSession;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * 视频录制Session
 * 目前视频音频均会录制
 * <p>
 * contains AudioCaptureSession, VideoCaptureSession for input
 * contains flvmuxer,mp4muxer for output
 * <p>
 * Created by baidu on 2017/3/28.
 */

public class LiveCaptureSession extends HandlerThreadSession {
    public static final String TAG = "LiveCaptureSession";

    AudioCaptureSession mAudioCaptureSession;
    VideoCaptureSession mVideoCaptureSession;

    long epochTimeInNs = 0L;
    private int mAudioFrameDurationInUs;
    private int mVideoFrameDurationInUs;

    private volatile boolean mIsEncodeVideo = false;
    private volatile boolean mIsEncodeAudio = false;

    private volatile boolean mIsStopped = true;
    private MediaMuxer mMediaMuxer;

    public LiveCaptureSession(Context context, LiveConfig liveConfig) {

        mIsEncodeVideo = liveConfig.isVideoEnabled();
        mIsEncodeAudio = liveConfig.isAudioEnabled();

        epochTimeInNs = System.nanoTime();
        mVideoCaptureSession = new VideoCaptureSession(liveConfig.getVideoWidth(), liveConfig.getVideoHeight(),
                liveConfig.getInitVideoBitrate(), liveConfig.getVideoFPS(), liveConfig.getGopLengthInSeconds(),
                liveConfig.getCameraId(), mIsEncodeVideo,
                liveConfig.getCameraOrientation(), liveConfig.getOutputOrientation());
        mVideoCaptureSession.setEpochTimeInNs(epochTimeInNs);

        mAudioCaptureSession = new AudioCaptureSession(context, liveConfig.getAudioSampleRate(), 2,
                liveConfig.getAudioBitrate(), MediaRecorder.AudioSource.MIC, mIsEncodeAudio);
        mAudioCaptureSession.setEpochTimeInNs(epochTimeInNs);

        // set audio gain
        mAudioCaptureSession.setRecordTrackGain(liveConfig.getMicGain());
        mAudioCaptureSession.setBGMTrackGain(liveConfig.getMusicGain());

        mVideoCaptureSession.setInnerErrorListener(innerErrorListener);
        mAudioCaptureSession.setInnerErrorListener(innerErrorListener);

        mVideoFrameDurationInUs = 1000000 / liveConfig.getVideoFPS();
        mAudioFrameDurationInUs = 1000000 * 1024 / liveConfig.getAudioSampleRate();
    }

    public void setFaceDetector(FaceDetector faceDetector) {
        mVideoCaptureSession.setFaceDetector(faceDetector);
    }

    public void setupDevice() {
        super.setupHandler();
        mAudioCaptureSession.startAudioDevice(); // start audio-record now, but now
        // videoCaptureSession will auto start when surfaceview created
    }

    public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
        mVideoCaptureSession.setSurfaceHolder(surfaceHolder);
    }

    String mLocalMp4Path;

    public void configMp4Muxer(String localMp4Path) {
        mLocalMp4Path = localMp4Path;
        File file = new File(mLocalMp4Path);
        if (file.exists()) {
            file.delete();
        }
        try {
            mMediaMuxer = new MediaMuxer(mLocalMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroyMp4Muxer() {
        if (mMediaMuxer != null) {
            mMediaMuxer.release();
        }
        mMediaMuxer = null;
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
    public void start() {
        try {
            if (!mIsEncodeVideo && !mIsEncodeAudio) {
                Log.e(TAG, "not encode video and audio, LiveCaptureSession start failed!");
                innerErrorListener.onFinish(false, 0, "start failed; errorMsg=no video&audio enabled");
                return;
            }
            mIsStopped = false;

            mAudioCaptureSession.setMediaFormatChangedListener(mAudioMediaFormatChangeListener);
            mVideoCaptureSession.setMediaFormatChangedListener(mVideoMediaFormatChangeListener);

            mAudioCaptureSession.setOnEncodedFrameUpdateListener(mOnEncodedAudioFrameUpdateListener);
            mVideoCaptureSession.setOnEncodedFrameUpdateListener(mOnEncodedVideoFrameUpdateListener);

            if (!mAudioCaptureSession.startEncoder() || !mVideoCaptureSession.startEncoder()) {
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
    public void pause() {
        mAudioCaptureSession.pauseBGM();

        resetMuxerTracks();
    }

    /**
     * resumeBGM mp4
     * <p>
     * this function will not resumeBGM encoder(encoder should be started)
     */
    public void resume() {
        if (!isPaused) {
            // not paused before, so resumeBGM is invalid
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mVideoCaptureSession.requestKeyFrame();
        }
        mAudioCaptureSession.resumeBGM();
        isPaused = false;
    }

    /**
     * stop encoder and save/push
     */
    public void stop() {
        try {
            if (mIsStopped) {
                return;
            }
            mIsStopped = true;
            isKeyFrameFound = false;
            resetMuxerTracks();
            latestVideoPtsInUs = 0L;
            latestAudioPtsInUs = 0L;

            mVideoCaptureSession.stopEncoder();
            mAudioCaptureSession.stopEncoder();

            mAudioCaptureSession.setOnEncodedFrameUpdateListener(null);
            mVideoCaptureSession.setOnEncodedFrameUpdateListener(null);
            mMp4VideoTrack = -1;
            mMp4AudioTrack = -1;
            mMediaMuxer.stop();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            innerErrorListener.onFinish(false, 0, "stop failed;errorMsg=" + e.getMessage());
        }
    }

    private MediaFormatChangedListener mAudioMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int audioTrackId = mMediaMuxer.addTrack(mediaFormat);
            Log.d(TAG, "audioTrackId = " + audioTrackId);
            startMuxerIfTracksUpdated(audioTrackId, true);
        }
    };

    private MediaFormatChangedListener mVideoMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int videoTrackId = mMediaMuxer.addTrack(mediaFormat);
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
            mMediaMuxer.start();
            isPaused = false;
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
//            Log.d(TAG, "onEncodedVideoFrameUpdate bufferInfo.pts = [" + bufferInfo.presentationTimeUs + ";size=" + bufferInfo.size);

            if (mMediaMuxer != null && mMp4VideoTrack >= 0 && !isPaused) {
                if (mIsEncodeVideo && !isKeyFrameFound) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 1) {
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
                    }

                    bufferInfo.presentationTimeUs = pts - videoPtsGapInUs;
                    latestVideoPtsInUs = bufferInfo.presentationTimeUs;
                    mMediaMuxer.writeSampleData(mMp4VideoTrack, bufferData, bufferInfo);
                } catch (Exception e) {
                    Log.e(TAG, "mediamuxer write video sample failed. errorMsg=" + e.getMessage());
                }
            }
        }
    };

    private OnEncodedFrameUpdateListener mOnEncodedAudioFrameUpdateListener = new OnEncodedFrameUpdateListener() {

        @Override
        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
//            Log.d(TAG, "onEncodedAudioFrameUpdate pts=" + bufferInfo.presentationTimeUs + ";size=" + bufferInfo.size);

            if (mMediaMuxer != null && mMp4AudioTrack >= 0 && !isPaused) {
                if (mIsEncodeVideo && !isKeyFrameFound) {
                    // need to wait for video key frame
                    Log.w(TAG, "onEncodedFrameUpdate: audio frame dropped as I-frame not ready.");
                    return;
                }
                try {
                    long pts = bufferInfo.presentationTimeUs;

                    if (pts == 0 && bufferInfo.size < 10) {
                        // pts is 0 and is audio-config info; we already have this in output format
                        return;
                    }
                    if (needAdjustAudioPts) {
                        Log.d(TAG, "onEncodedFrameUpdate: Adjusting audioPts");
                        latestVideoPtsInUs += mVideoFrameDurationInUs;
                        latestAudioPtsInUs += mAudioFrameDurationInUs;
                        videoPtsGapInUs = pts - latestVideoPtsInUs;
                        audioPtsGapInUs = pts - latestAudioPtsInUs;
                        needAdjustAudioPts = false;
                        needAdjustVideoPts = false;
                    }

                    bufferInfo.presentationTimeUs = pts - audioPtsGapInUs;
                    latestAudioPtsInUs = bufferInfo.presentationTimeUs;
                    mMediaMuxer.writeSampleData(mMp4AudioTrack, bufferData, bufferInfo);
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
        return mVideoCaptureSession != null ? mVideoCaptureSession.getScreenShot() : null;
    }

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mVideoCaptureSession.setGPUImageFilters(filters);
    }

    public void release() {
        // camera will auto-release when surfaceDestroyed, but we allow reset
        super.destroyHandler();
        mVideoCaptureSession.stopVideoDevice();
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

    public void toggleFlash(boolean flag) {
        mVideoCaptureSession.toggleFlash(flag);
    }

    public boolean canSwitchCamera() {
        return mVideoCaptureSession.canSwitchCamera();
    }

    public void switchCamera(int cameraId) {
        mVideoCaptureSession.switchCamera(cameraId);
    }

    /**
     * 设置对焦焦点
     *
     * @param x
     * @param y
     */
    public void focusToPoint(int x, int y) {
        mVideoCaptureSession.focusToPoint(x, y);
    }

    /**
     * 获取相机最大的放大因子
     */
    public int getMaxZoomFactor() {
        return mVideoCaptureSession.getMaxZoomFactor();
    }

    /**
     * 设置相机放大因子
     *
     * @param factor
     */
    public boolean setZoomFactor(int factor) {
        return mVideoCaptureSession.setZoomFactor(factor);
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
