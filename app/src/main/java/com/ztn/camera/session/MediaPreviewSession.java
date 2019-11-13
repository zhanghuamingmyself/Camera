package com.ztn.camera.session;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.device.MediaDecoderDevice;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceVideoSizeChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.listener.PreviewStateListener;
import com.ztn.camera.session.track.AudioPreviewSession;
import com.ztn.camera.session.track.VideoPreviewSession;

import java.nio.ByteBuffer;
import java.util.List;

import static com.ztn.camera.session.Constraints.MSG_FAILED;


/**
 * contain AudioProcessSession && VideoProcessSession
 * <p>
 * diff from LiveCaptureSession: start is invoked by user, but stop when task is over
 * Created by baidu on 2017/3/23.
 */

public class MediaPreviewSession extends HandlerThreadSession {
    public static final String TAG = "MediaPreviewSession";

    volatile AudioPreviewSession mAudioPreviewSession;
    volatile VideoPreviewSession mVideoPreviewSession;
    volatile MediaDecoderDevice mMediaDecoderDevice;

    private String mFilePath = "";
    private int playbackRate = 1;

    private volatile boolean mIsDecodeVideo = true;
    private volatile boolean mIsDecodeAudio = true;

    private volatile boolean mIsPreviewVideoDone = false;
    private volatile boolean mIsPreviewAudioDone = false;

    private PreviewStateListener mPreviewStateListener = null;

    /**
     * 构造函数
     *
     * @param context
     */
    public MediaPreviewSession(Context context) {
        super.setupHandler();
        mVideoPreviewSession = new VideoPreviewSession(context);
        mAudioPreviewSession = new AudioPreviewSession();

        mAudioPreviewSession.setOnPreviewOverListener(audioPreviewOverListener);

        mIsDecodeVideo = true;
        mIsDecodeAudio = true;
    }

    public void setVideoAudioEnabled(boolean isVideoEnabled, boolean isAudioEnabled) {
        mIsDecodeVideo = isVideoEnabled;
        mIsDecodeAudio = isAudioEnabled;
    }

    private OnFinishListener audioPreviewOverListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            long timeConsuming = System.currentTimeMillis() - startTimeInMilliSeconds;
            if (isSuccess) {
                Log.d(TAG, "Audio Preview Success@ timeConsuming=" + timeConsuming);
            } else {
                Log.d(TAG, "Audio Preview Failed@ timeConsuming=" + timeConsuming);
            }
            judgeEncodeStatus(true, isSuccess);
        }
    };

    private OnDeviceFrameUpdateListener mVideoDeviceFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            // TODO update pts now
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "onDeviceFrameUpdateSoon end of stream");
                long timeConsuming = System.currentTimeMillis() - startTimeInMilliSeconds;
                Log.d(TAG, "Video Preview Success@ timeConsuming=" + timeConsuming);
                judgeEncodeStatus(false, true);
            }
            return 0;
        }
    };

    private OnDeviceVideoSizeChangedListener mSizeChangeListener = new OnDeviceVideoSizeChangedListener() {
        @Override
        public void onDeviceVideoSizeChanged(int videoWidth, int videoHeight, int videoOrientation) {
            if (mPreviewStateListener != null) {
                mPreviewStateListener.onSizeChanged(videoWidth, videoHeight, videoOrientation);
            }
            if (mVideoPreviewSession.getOnDeviceVideoSizeChangedListener() != null) {
                mVideoPreviewSession
                        .getOnDeviceVideoSizeChangedListener()
                        .onDeviceVideoSizeChanged(videoWidth, videoHeight, videoOrientation);
            }
        }
    };

    public void setSurfaceHolder(SurfaceHolder surfaceHolder) {
        mVideoPreviewSession.setSurfaceHolder(surfaceHolder);
    }

    private synchronized void judgeEncodeStatus(boolean isAudio, boolean isSuccess) {
        if (isSuccess) {
            if (isAudio) {
                mIsPreviewAudioDone = true;
            } else {
                mIsPreviewVideoDone = true;
            }
            if ((!mIsDecodeAudio || mIsPreviewAudioDone) // don't decode audio or done
                    && (!mIsDecodeVideo || mIsPreviewVideoDone) // don't decode video or done
                    ) {
                try {
                    Message msg = new Message();
                    msg.what = Constraints.MSG_SUCCESS;
                    sendMessageToHandlerThread(msg);
                } catch (Exception e) {
                    Log.d(TAG, Log.getStackTraceString(e));
                }

            }
        } else {
            try {
                Message msg = new Message();
                msg.what = MSG_FAILED;
                msg.what = Constraints.MSG_FAILED_ARG1_REASON_FILTER;
                sendMessageToHandlerThread(msg);
            } catch (Exception e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }

    }

    public void setMediaFilePath(String mediaFilePath) {
        mFilePath = mediaFilePath;
    }

    private volatile long startTimeInMilliSeconds = 0L;

    public void start() {
        if (!mIsDecodeAudio && !mIsDecodeVideo) {
            Log.e(TAG, "start failed! not decode audio and video, nothing to be done");
            return;
        }
        Message msg = super.obtainMessage(Constraints.MSG_TO_START);
        super.sendMessageToHandlerThread(msg);
    }

    private void startInHandlerThread() {

        mIsPreviewVideoDone = false;
        mIsPreviewAudioDone = false;

        startTimeInMilliSeconds = System.currentTimeMillis();
        isStopped = false;

        mVideoPreviewSession.startPreview();
        mAudioPreviewSession.startPreview();

        startNewDevice();
    }

    private void startNewDevice() {
        try {
            mMediaDecoderDevice = new MediaDecoderDevice(mFilePath);
            mMediaDecoderDevice.setup();
            mMediaDecoderDevice.configClip(mClipStartPositionInUSec, mClipDurationInUSec);
            mMediaDecoderDevice.setPlaybackRate(playbackRate);
            mMediaDecoderDevice.setExtractAudioEnabled(mIsDecodeAudio);
            mMediaDecoderDevice.setExtractVideoEnabled(mIsDecodeVideo);
            mMediaDecoderDevice.setOnDecodeStateChangeListener(mDecodeDeviceStateListener);
            // audio output listener
            mMediaDecoderDevice.setOnAudioDeviceFrameUpdateListener(mAudioPreviewSession
                    .getOnAudioFrameUpdateListener());

            // video output listener
            mMediaDecoderDevice.setOnDeviceVideoSizeChangedListener(mSizeChangeListener);
            mMediaDecoderDevice.setVideoOutputSurface(mVideoPreviewSession.getInputSurface());
            mMediaDecoderDevice.setOnVideoDeviceFrameUpdateListener(mVideoDeviceFrameUpdateListener);
            mMediaDecoderDevice.startDecoder();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }
    }

    public void pause() {
        Message msg = super.obtainMessage(Constraints.MSG_TO_PAUSE);
        super.sendMessageToHandlerThread(msg);
    }

    private void pauseInHanlderThread() {
        if (mMediaDecoderDevice != null) {
            mMediaDecoderDevice.pause();
        }
        if (mAudioPreviewSession != null) {
            mAudioPreviewSession.pause();
        }
    }

    public void resume() {
        Message msg = super.obtainMessage(Constraints.MSG_TO_RESUME);
        super.sendMessageToHandlerThread(msg);
    }

    private void resumeInHandlerThread() {
        if (mMediaDecoderDevice != null) {
            mMediaDecoderDevice.resume();
        }
        if (mAudioPreviewSession != null) {
            mAudioPreviewSession.resume();
        }
    }

    private volatile boolean isStopped = false;

    /**
     * 中途结束或者processOverListener调用结束
     */
    public void stop() {
        if (super.hasMessages(Constraints.MSG_TO_START)) {
            super.removeMessages(Constraints.MSG_TO_START);
        } else {
            Message msg = super.obtainMessage(Constraints.MSG_TO_STOP);
            super.sendMessageToHandlerThread(msg);
        }
    }

    private void stopInHanlderThread() {
        try {
            if (isStopped) {
                Log.d(TAG, "has been stopped before; maybe processover auto stop");
                return;
            }
            isStopped = true;

            mMediaDecoderDevice.stopDecoder();
            mMediaDecoderDevice.release();
            mVideoPreviewSession.stopPreview();
            mAudioPreviewSession.stopPreview();

        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

    }

    /**
     * release when not use this session anymore
     */
    public void release() {
        Message msg = super.obtainMessage(Constraints.MSG_TO_RELEASE);
        super.sendMessageToHandlerThread(msg);
    }

    private void releaseInHandlerThread() {
        mVideoPreviewSession.release();
        mAudioPreviewSession.release();
        super.destroyHandler();
    }

    public void setPreviewStateListener(PreviewStateListener listener) {
        mPreviewStateListener = listener;
    }

    private boolean mIsLooping = false;

    public void setLooping(boolean isLooping) {
        mIsLooping = isLooping;
    }

    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
        mAudioPreviewSession.configBackgroundMusic(enableBGM, bgmPath, isLooping);
    }

    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
        mAudioPreviewSession.configBackgroundMusicClip(clipStartInUSec, clipDurationInUSec);
    }

    public void setMasterTrackGain(float mainGain) {
        if (mAudioPreviewSession != null) {
            mAudioPreviewSession.setMasterTrackGain(mainGain);
        }
    }

    public void setBGMTrackGain(float subGain) {
        if (mAudioPreviewSession != null) {
            mAudioPreviewSession.setBGMTrackGain(subGain);
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

    private MediaDecoderDevice.OnDecodeStateChangeListener mDecodeDeviceStateListener
            = new MediaDecoderDevice.OnDecodeStateChangeListener() {

        @Override
        public void onFinish(boolean isSuccess) {
            Log.d(TAG, "decoder is over; isSuccess=" + isSuccess);
            if (!isSuccess && !isStopped) {
                // decode failed, notif to stop
                Message msg = new Message();
                msg.what = MSG_FAILED;
                msg.arg1 = Constraints.MSG_FAILED_ARG1_REASON_DEVICE;
                sendMessageToHandlerThread(msg);
            }
        }

        @Override
        public void onProgress(int progress, long currentPTSInUs) {
            // don't use progress here
            if (mPreviewStateListener != null) {
//                Log.d(TAG, "onProgress progress = " + progress);
                int notifProgress = progress;

                // don't notif 100% except totally over
                if (notifProgress >= 99) {
                    notifProgress = 99;
                }
                mLastCurrentPTSInUs = currentPTSInUs;
                mPreviewStateListener.onProgress(notifProgress, currentPTSInUs);
            }
        }

        @Override
        public void onDurationUpdated(int durationInMilliSec) {
            if (mPreviewStateListener != null) {
                mPreviewStateListener.onDuration(durationInMilliSec);
            }
        }
    };

    private long mLastCurrentPTSInUs = -1;

    @Override
    protected void handleMessageInHandlerThread(Message msg) {
        switch (msg.what) {
            case Constraints.MSG_SUCCESS:
                if (isStopped) {
                    Log.d(TAG, "stopped already, msg.what=" + msg.what + " is not delivered");
                    return;
                }
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onProgress(100, mLastCurrentPTSInUs);
                }

                if (mIsLooping) {
                    // reconfig decoder-device
                    stop();
                    start(); // restart automatically
                } else {
                    mPreviewStateListener.onFinish(true, 0);
                    stopInHanlderThread();
                }
                break;
            case MSG_FAILED:
                if (isStopped) {
                    Log.d(TAG, "stopped already, msg.what=" + msg.what + " is not delivered");
                    return;
                }
                stopInHanlderThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onFinish(false, msg.arg1);
                }
                break;

            case Constraints.MSG_TO_START:
                startInHandlerThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onStarted();
                }
                break;
            case Constraints.MSG_TO_PAUSE:
                pauseInHanlderThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onPaused();
                }
                break;
            case Constraints.MSG_TO_RESUME:
                resumeInHandlerThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onResumed();
                }
                break;
            case Constraints.MSG_TO_STOP:
                stopInHanlderThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onStopped();
                }
                break;
            case Constraints.MSG_TO_RELEASE:
                releaseInHandlerThread();
                if (mPreviewStateListener != null) {
                    mPreviewStateListener.onReleased();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mVideoPreviewSession.setGPUImageFilters(filters);
    }
}
