package com.ztn.camera.session.track;

import android.media.MediaCodec;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.mediaprocess.device.MediaDecoderDevice;
import com.baidu.cloud.mediaprocess.filter.AudioFilter;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.HandlerThreadSession;
import java.nio.ByteBuffer;

import static com.ztn.camera.session.Constraints.MSG_BGM_FINISHED;


/**
 * can add bgmusic and so on
 * Created by baidu on 2017/3/23.
 */

public class AudioPreviewSession extends HandlerThreadSession {
    private static final String TAG = "AudioPreviewSession";

    private MediaDecoderDevice mBGMusicDevice;
    private int mBGMFilterTrack = -1;
    private AudioFilter mAudioFilter;

    public AudioPreviewSession() {
        mAudioFilter = new AudioFilter();
        super.setupHandler();
        mAudioFilter.setNeedRendering(true);
        mAudioFilter.setup(true);
    }

    private OnFinishListener mPreviewOverListener;

    /**
     * should invoke after startEncoder
     *
     * @param listener
     */
    public void setOnPreviewOverListener(OnFinishListener listener) {
        mPreviewOverListener = listener;
    }

    private boolean mEnableBGM = false;
    private String mBGMFilePath = null;
    private volatile boolean mIsBGMLooping = false;


    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
        mEnableBGM = enableBGM;
        mBGMFilePath = bgmPath;
        mIsBGMLooping = isLooping;
    }

    private long mClipStartPositionInUSec = -1;
    private long mClipDurationInUSec = -1;

    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
        mClipStartPositionInUSec = clipStartInUSec;
        mClipDurationInUSec = clipDurationInUSec;
    }

    public void setMasterTrackGain(float mainGain) {
        if (mAudioFilter != null) {
            mAudioFilter.setMasterTrackGain(mainGain);
        }
    }

    public void setBGMTrackGain(float subGain) {
        if (mAudioFilter != null) {
            mAudioFilter.setSubTrackGain(subGain);
        }
    }

    /**
     * invoke by startAudioDevice
     */
    private void startBGMDevice() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.stopDecoder();
            mBGMusicDevice.release();
            mBGMusicDevice = null;
        }
        try {
            mBGMusicDevice = new MediaDecoderDevice(mBGMFilePath);
            mBGMusicDevice.setup();
            mBGMusicDevice.configClip(mClipStartPositionInUSec, mClipDurationInUSec);
            mBGMusicDevice.setOnDecodeStateChangeListener(mBGMDecodeStateListener);
            mBGMusicDevice.setExtractAudioEnabled(true);
            mBGMusicDevice.setExtractVideoEnabled(false);
            mBGMusicDevice.setOnAudioDeviceFrameUpdateListener(mOnBGMusicDeviceUpdateListener);
            if (mBGMFilterTrack < 0) { // default is -1
                mBGMFilterTrack = mAudioFilter.addSubTrack();
            }
            mBGMusicDevice.startDecoder();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

    }

    /**
     * invoke by stopAudioDevice
     */
    private void stopBGMDevice() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.stopDecoder();
            mBGMusicDevice.release();
            mBGMusicDevice = null;
            mBGMFilterTrack = -1;
        }
    }

    /**
     * only pauseBGM bgm
     * audio of videofile is paused by MediaPreivewSession
     */
    public void pause() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.pause();
        }
    }

    /**
     * only resumeBGM bgm
     * audio of videofile is paused by MediaPreivewSession
     */
    public void resume() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.resume();
        }
    }

    public boolean startPreview() {
        mIsStopped = false;
        if (mEnableBGM) {
            startBGMDevice();
        }
        return true;

    }

    private boolean mIsStopped = false;

    public void stopPreview() {
        if (mIsStopped) {
            return;
        }
        mIsStopped = true;
        mAudioFilter.resetBuffer(); // clear old data
        stopBGMDevice();

    }

    public void release() {
        mAudioFilter.release();
        super.destroyHandler();
    }

    private OnDeviceFrameUpdateListener mOnBGMusicDeviceUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mBGMFilterTrack >= 0) {
                if (bufferData != null && bufferInfo.size > 0) {
                    mAudioFilter.pushDataForSubTrack(bufferData, bufferInfo, mBGMFilterTrack);
                } else {
                    Log.d(TAG, "bgm END OF STREAM");
                }

            }
            return 0;
        }
    };


    private OnDeviceFrameUpdateListener mDeviceFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mAudioFilter != null) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "onDeviceFrameUpdateSoon end of stream");
                    // end of stream
                    if (mPreviewOverListener != null) {
                        // need wait last?
                        mPreviewOverListener.onFinish(true, 0, null);
                    }
                } else {
                    mAudioFilter.pushDataForMasterTrack(bufferData, bufferInfo);
                }
            }
            return 0;
        }
    };

    public OnDeviceFrameUpdateListener getOnAudioFrameUpdateListener() {
        return mDeviceFrameUpdateListener;
    }


    private MediaDecoderDevice.OnDecodeStateChangeListener mBGMDecodeStateListener
            = new MediaDecoderDevice.OnDecodeStateChangeListener() {

        @Override
        public void onFinish(boolean isSuccess) {
            Log.d(TAG, "BGM is over; isSuccess=" + isSuccess);
            if (!mIsStopped) {
                Message msg = new Message();
                msg.what = MSG_BGM_FINISHED;
                msg.arg1 = isSuccess ? 1 : 0;
                sendMessageToHandlerThread(msg);
            }

        }

        @Override
        public void onProgress(int progress, long currentPTSInUs) {
            // don't use progress here
        }

        @Override
        public void onDurationUpdated(int durationInMilliSec) {
            // don't use here
        }
    };

    @Override
    protected void handleMessageInHandlerThread(Message msg) {
        switch (msg.what) {
            case MSG_BGM_FINISHED:
                boolean isSuccess = msg.arg1 == 1;
                if (!isSuccess) {
                    // send failed message to outer!
                } else {
                    // looping bgm? --- restart
                    if (!mIsStopped && mIsBGMLooping) {
                        startBGMDevice();
                    }
                }
                break;
            default:
                break;
        }
    }
}
