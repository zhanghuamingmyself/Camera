package com.ztn.camera.session;

import android.content.Context;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.device.MediaDecoderDevice;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.config.ProcessConfig;
import com.ztn.camera.listener.ProcessStateListener;
import com.ztn.camera.session.track.AudioProcessSession;
import com.ztn.camera.session.track.VideoProcessSession;

import java.io.File;
import java.util.List;


/**
 * start: start process
 * stop: stop process
 * <p>
 * contain AudioProcessSession && VideoProcessSession
 * <p>
 * diff from LiveCaptureSession: start is invoked by user, but stop when task is over
 * Created by baidu on 2017/3/23.
 */

public class MediaProcessSession {
    public static final String TAG = "MediaProcessSession";

    private AudioProcessSession mAudioProcessSession;
    private VideoProcessSession mVideoProcessSession;
    private MediaDecoderDevice mMediaDecoderDevice;

    private MediaMuxer mMediaMuxer;

    private String mFilePath = "";
    private int playbackRate = 1;

    private volatile boolean mIsDecodeVideo = false;
    private volatile boolean mIsDecodeAudio = false;

    private boolean mIsEncodeVideoDone = false;
    private boolean mIsEncodeAudioDone = false;

    private HandlerThread mHandlerThread = null;

    /**
     * use mHandlerThread incase MediaProcessSession has no looper
     */
    private ProcessHandler mOwnThreadHandler = null;

    private ProcessStateListener mProcessStateListener = null;

    /**
     * 构造函数，如果想复用session，需要
     *
     * @param context
     * @param config  should contains only video/audio, contain file-path and so on!!!
     */
    public MediaProcessSession(Context context, ProcessConfig config) {
        mVideoProcessSession = new VideoProcessSession(context, config.getVideoWidth(),
                config.getVideoHeight(),
                config.getInitVideoBitrate(), config.getVideoFPS(), config.getGopLengthInSeconds());
        mAudioProcessSession = new AudioProcessSession(config.getAudioSampleRate(),
                config.getAudioChannelCount(), config.getAudioBitrate());
        playbackRate = config.getPlaybackRate();

        mVideoProcessSession.setOnEncodedOverListener(videoEncodedOverListener);
        mAudioProcessSession.setOnEncodedOverListener(audioEncodedOverListener);

        mIsDecodeVideo = config.isVideoEnabled(); // use setVideoAudioEnabled to set value
        mIsDecodeAudio = config.isAudioEnabled(); // use setVideoAudioEnabled to set value
    }

    public void setProcessStateListener(ProcessStateListener listener) {
        mProcessStateListener = listener;
    }

//    public void setVideoAudioEnabled(boolean isVideoEnable, boolean isAudioEnable) {
//        mIsDecodeVideo = isVideoEnable;
//        mIsDecodeAudio = isAudioEnable;
//    }

    private OnFinishListener audioEncodedOverListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            long timeConsuming = System.currentTimeMillis() - startTimeInMilliSeconds;
            if (isSuccess) {
                Log.d(TAG, "Audio Encode Success@ timeConsuming=" + timeConsuming);
                mIsEncodeAudioDone = true;
                judgeEncodeStatus();
            } else {
                Log.d(TAG, "Audio Encode Failed@ timeConsuming=" + timeConsuming);
                mOwnThreadHandler.sendEmptyMessage(Constraints.MSG_FAILED);
            }
        }
    };

    private OnFinishListener videoEncodedOverListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            long timeConsuming = System.currentTimeMillis() - startTimeInMilliSeconds;
            if (isSuccess) {
                Log.d(TAG, "Video Encode Success@ timeConsuming=" + timeConsuming);
                mIsEncodeVideoDone = true;
                judgeEncodeStatus();
            } else {
                Log.d(TAG, "Video Encode Failed@ timeConsuming=" + timeConsuming);
                mOwnThreadHandler.sendEmptyMessage(Constraints.MSG_FAILED);
            }
        }
    };

    private void judgeEncodeStatus() {
        if ((!mIsDecodeAudio || mIsEncodeAudioDone) // don't decode audio or done
                && (!mIsDecodeVideo || mIsEncodeVideoDone) // don't decode video or done
                ) {
            mOwnThreadHandler.sendEmptyMessage(Constraints.MSG_SUCCESS);
        }
    }

    public void setMediaFilePath(String mediaFilePath) {
        mFilePath = mediaFilePath;
    }

    private long startTimeInMilliSeconds = 0L;

    public void start() {
        try {
            if (!mIsDecodeAudio && !mIsDecodeVideo) {
                Log.e(TAG, "start failed! not decode audio and video, nothing to be done");
                return;
            }

            mIsEncodeAudioDone = false;
            mIsEncodeVideoDone = false;

            mHandlerThread = new HandlerThread("MediaProcessSession");
            mHandlerThread.start();
            mOwnThreadHandler = new ProcessHandler(mHandlerThread.getLooper());

            startTimeInMilliSeconds = System.currentTimeMillis();
            isStopped = false;

            muxerAudioTrack = -1;
            muxerVideoTrack = -1;

            if (mSaveMp4) {
                File file = new File(mLocalMp4Path);
                if (file.exists()) {
                    file.delete();
                }
                mMediaMuxer = new MediaMuxer(mLocalMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mVideoProcessSession.setMediaMuxer(mMediaMuxer);
                mAudioProcessSession.setMediaMuxer(mMediaMuxer);
            }
            mAudioProcessSession.setMediaFormatChangedListener(mAudioMediaFormatChangeListener);
            mVideoProcessSession.setMediaFormatChangedListener(mVideoMediaFormatChangeListener);

            //
            mVideoProcessSession.startEncoder();
            mAudioProcessSession.startEncoder();

            mMediaDecoderDevice = new MediaDecoderDevice(mFilePath);
            mMediaDecoderDevice.setup();
            // 处理原视频旋转信息
            // TODO: 29/03/2018 fix when sdk >= 21 
            if (mSaveMp4 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                mMediaMuxer.setOrientationHint(mMediaDecoderDevice.getRotation());
            }
            mMediaDecoderDevice.configClip(mClipStartPositionInUSec, mClipDurationInUSec);
            mMediaDecoderDevice.setPlaybackRate(playbackRate);
            mMediaDecoderDevice.setOnDecodeStateChangeListener(mDecodeDeviceStateListener);
            // if not sync, video frame will lose a lot (maybe from 20 to 6)
            // --> we have deal it by wait filter-consume time in VideoProcessSession
            mMediaDecoderDevice.setIsSyncWithSystemTime(false);

            mMediaDecoderDevice.setExtractAudioEnabled(mIsDecodeAudio);
            mMediaDecoderDevice.setExtractVideoEnabled(mIsDecodeVideo);

            // audio output listener
            mMediaDecoderDevice.setOnAudioDeviceFrameUpdateListener(mAudioProcessSession
                    .getOnAudioFrameUpdateListener());

            // video output listener
            mMediaDecoderDevice.setOnDeviceVideoSizeChangedListener(mVideoProcessSession
                    .getOnDeviceVideoSizeChangedListener());
            mMediaDecoderDevice.setVideoOutputSurface(mVideoProcessSession.getInputSurface());
            mMediaDecoderDevice.setOnVideoDeviceFrameUpdateListener(mVideoProcessSession
                    .getOnVideoFrameUpdateListener());
            mMediaDecoderDevice.startDecoder();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            if (mProcessStateListener != null) {
                mProcessStateListener.onFinish(false, 0);
            }
        }

    }

    private volatile boolean isStopped = false;

    /**
     * 中途结束或者processOverListener调用结束
     */
    public void stop() {
        try {
            if (isStopped) {
                Log.d(TAG, "has been stopped before; maybe processover auto stop");
                return;
            }
            isStopped = true;
            if (mMediaDecoderDevice != null) {
                mMediaDecoderDevice.stopDecoder();
                mMediaDecoderDevice.release();
            }

            mVideoProcessSession.stopEncoder();
            mAudioProcessSession.stopEncoder();

            muxerAudioTrack = -1;
            muxerVideoTrack = -1;

            if (mMediaMuxer != null) {
                mMediaMuxer.stop();
                mMediaMuxer.release();
            }
            mHandlerThread.quit();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));

        }

    }

    private MediaDecoderDevice.OnDecodeStateChangeListener mDecodeDeviceStateListener
            = new MediaDecoderDevice.OnDecodeStateChangeListener() {

        @Override
        public void onFinish(boolean isSuccess) {
            Log.d(TAG, "decoder is over; isSuccess=" + isSuccess);
            if (!isSuccess) {
                // decode failed, notif to stop
                Message msg = new Message();
                msg.what = Constraints.MSG_FAILED;
                msg.arg1 = Constraints.MSG_FAILED_ARG1_REASON_DEVICE;
                mOwnThreadHandler.sendMessage(msg);
            }
        }

        @Override
        public void onProgress(int progress, long currentPTSInUs) {
            if (mProcessStateListener != null) {
//                Log.d(TAG, "onProgress progress = " + progress);
                int notifProgress = progress;

                // don't notif 100% except encode over
                if (notifProgress >= 99) {
                    notifProgress = 99;
                }
                mProcessStateListener.onProgress(notifProgress);
            }

        }

        @Override
        public void onDurationUpdated(int durationInMilliSec) {
            Log.d(TAG, "duration=" + durationInMilliSec);
        }
    };

    private long mLastCurrentPTSInUs = -1;

    private Object mFormatUpdateLock = new Object();
    private Object mFormatAllLock = new Object();

    private volatile int muxerAudioTrack = -1;
    private volatile int muxerVideoTrack = -1;

    // 怎么保证不丢主要信息？ --- 需要两方同时hung住的同步！如果不hung住，可能会丢失关键信息，比如视频或者音频相关信息
    // 音频hang住AudioFilter的mixloop
    private MediaFormatChangedListener mAudioMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int audioTrackId = mMediaMuxer.addTrack(mediaFormat);
//            int audioTrackId = mMp4Muxer.addTrack(mediaFormat);
            Log.d(TAG, "audioTrackId = " + audioTrackId);
            // MediaMuxer need start first, then writeSampleData
            checkMuxerTrackUpdated(audioTrackId, true);
            // Mp4Parser not limit

        }
    };

    // 视频hang住VideoEncoder的loop
    private MediaFormatChangedListener mVideoMediaFormatChangeListener = new MediaFormatChangedListener() {
        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            int videoTrackId = mMediaMuxer.addTrack(mediaFormat);
            Log.d(TAG, "videoTrackId = " + videoTrackId);
            checkMuxerTrackUpdated(videoTrackId, false);
        }
    };

    private void checkMuxerTrackUpdated(int trackId, boolean isAudio) {
        if (isAudio) {
            muxerAudioTrack = trackId;
            if (mIsDecodeVideo) {
                // may wait
                checkAnotherTrack(muxerVideoTrack);
            } else {
                updateAllTrack();
            }

        } else {
            muxerVideoTrack = trackId;
            if (mIsDecodeAudio) {
                // may wait
                checkAnotherTrack(muxerAudioTrack);
            } else {
                updateAllTrack();
            }
        }
    }

    private volatile boolean mFormatUpdateLockNotified = false;
    private volatile boolean mFormatAllLockNotified = false;

    private void checkAnotherTrack(int anotherTrack) {
//        boolean shouldWaitAllDone = false;
        if (anotherTrack < 0) {
            synchronized (mFormatUpdateLock) {
                try {
                    if (!mFormatUpdateLockNotified) {
                        mFormatUpdateLock.wait();
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "checkMuxerTrackUpdated wait video is interrupted");
                }
            }
            updateAllTrack();
        } else {
            synchronized (mFormatUpdateLock) {
                mFormatUpdateLockNotified = true;
                mFormatUpdateLock.notify();
            }
            synchronized (mFormatAllLock) {
                try {
                    while (!mFormatAllLockNotified) {
                        mFormatAllLock.wait();
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "checkMuxerTrackUpdated  mFormatAllLock is interrupted");
                }
            }
        }
    }

    private void updateAllTrack() {
        // muxer start first
        mMediaMuxer.start();
        if (mIsDecodeAudio) {
            mAudioProcessSession.setMp4AudioTrack(muxerAudioTrack);
        }
        if (mIsDecodeVideo) {
            mVideoProcessSession.setMp4VideoTrack(muxerVideoTrack);
        }

        synchronized (mFormatAllLock) {
            mFormatAllLockNotified = true;
            mFormatAllLock.notify();
        }

    }

    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
        mAudioProcessSession.configBackgroundMusic(enableBGM, bgmPath, isLooping);
    }

    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
        mAudioProcessSession.configBackgroundMusicClip(clipStartInUSec, clipDurationInUSec);
    }

    /**
     * 设置背景音乐音量的大小，默认是 1f
     *
     * @param gain 范围在 0f 到 1f 之间
     */
    public void setBGMTrackGain(float gain) {
        mAudioProcessSession.setBGMTrackGain(gain);
    }

    public void setMasterTrackGain(float mainGain) {
        if (mAudioProcessSession != null) {
            mAudioProcessSession.setMasterTrackGain(mainGain);
        }
    }

    public void setPlaybackRate(int playbackRate) {
        this.playbackRate = playbackRate;
        if (mMediaDecoderDevice != null) {
            mMediaDecoderDevice.setPlaybackRate(playbackRate);
        }
    }

    private long mClipStartPositionInUSec = -1;
    private long mClipDurationInUSec = -1;

    public void configMediaFileClip(long clipStartInUSec, long clipDurationInUSec) {
        mClipStartPositionInUSec = clipStartInUSec;
        mClipDurationInUSec = clipDurationInUSec;
    }


    private String mLocalMp4Path;
    private boolean mSaveMp4 = false;

    public void configMp4Saver(boolean saveMp4, String localMp4Path) {
        mLocalMp4Path = localMp4Path;
        mSaveMp4 = saveMp4;
    }

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mVideoProcessSession.setGPUImageFilters(filters);
    }


    class ProcessHandler extends Handler {

        ProcessHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
            if (isStopped) {
                Log.d(TAG, "stopped already, msg.what=" + msg.what + " is not delivered");
                return;
            }
            switch (msg.what) {
                case Constraints.MSG_SUCCESS:
                    if (mProcessStateListener != null) {
                        mProcessStateListener.onProgress(100);
                        mProcessStateListener.onFinish(true, 0);
                    }
                    stop();
                    break;
                case Constraints.MSG_FAILED:
                    stop();
                    if (mProcessStateListener != null) {
                        mProcessStateListener.onFinish(false, msg.arg1);
                    }
                    break;
                default:
                    break;
            }
        }
    }

}
