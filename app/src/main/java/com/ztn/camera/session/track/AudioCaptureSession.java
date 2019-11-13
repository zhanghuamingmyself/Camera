package com.ztn.camera.session.track;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.mediaprocess.device.AudioRecorderDevice;
import com.baidu.cloud.mediaprocess.device.MediaDecoderDevice;
import com.baidu.cloud.mediaprocess.encoder.AudioMediaEncoder;
import com.baidu.cloud.mediaprocess.filter.AudioFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFilteredFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.Constraints;
import com.ztn.camera.session.HandlerThreadSession;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import static com.ztn.camera.session.Constraints.MSG_BGM_FINISHED;


/**
 * from audio-capture to audiofilter(mix with playback) to videoencoder
 * Created by baidu on 2017/3/27.
 */

public class AudioCaptureSession extends HandlerThreadSession {
    private static final String TAG = "AudioCaptureSession";

    private int mSampleRateHz;
    private int mChannelCount = 2; // always two
    private int mBitrate; // used by encoder

    private AudioRecorderDevice mAudioRecorderDevice;
    private MediaDecoderDevice mBGMusicDevice;
    private int mBGMFilterTrack = -1;
    private volatile AudioFilter mAudioFilter;
    private volatile AudioMediaEncoder mAudioEncoder;

    private WeakReference<Context> contextWeakRef;

    public AudioCaptureSession(Context context, int sampleRateHz, int channelCount, int bitrate,
                               int audioSource, boolean isAudioEnabled) {

        contextWeakRef = new WeakReference<Context>(context);

        mSampleRateHz = sampleRateHz;
        mChannelCount = channelCount;
        mBitrate = bitrate;

        mAudioRecorderDevice = new AudioRecorderDevice(mSampleRateHz, audioSource);
        mAudioRecorderDevice.setOnDeviceFrameUpdateListener(mOnDeviceFrameUpdateListener);
        mAudioRecorderDevice.setNeedFixCaptureSize(true); // size is limited to 4096
        mAudioRecorderDevice.setAudioEnabled(isAudioEnabled);
        mAudioFilter = new AudioFilter();

    }

    public void setRecordTrackGain(float mainGain) {
        if (mAudioFilter != null) {
            mAudioFilter.setMasterTrackGain(mainGain);
        }
    }

    public void setBGMTrackGain(float subGain) {
        if (mAudioFilter != null) {
            mAudioFilter.setSubTrackGain(subGain);
        }
    }


    private boolean mEnableBGM = false;
    private String mBGMFilePath = null;
    private volatile boolean mIsBGMLooping = false;

    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
        mEnableBGM = enableBGM;
        mBGMFilePath = bgmPath;
        mIsBGMLooping = isLooping;
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
            if (mBGMFilterTrack < 0) {
                mBGMFilterTrack = mAudioFilter.addSubTrack();
            }

            mBGMusicDevice.startDecoder();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

    }

    /**
     * Mute audio recording or not
     * @param isMute true for mute, false not mute
     */
    public void setMuteAudio(boolean isMute) {
        mAudioRecorderDevice.setAudioEnabled(!isMute);
    }

    private long mClipStartPositionInUSec = -1;
    private long mClipDurationInUSec = -1;

    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
        mClipStartPositionInUSec = clipStartInUSec;
        mClipDurationInUSec = clipDurationInUSec;
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

    public void setEpochTimeInNs(long epochTimeInNs) {
        if (mAudioRecorderDevice != null) {
            mAudioRecorderDevice.setEpochTimeInNs(epochTimeInNs);
        }
    }

    /**
     * only pauseBGM bgm
     */
    public void pauseBGM() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.pause();
        }
    }

    /**
     * only resumeBGM bgm
     */
    public void resumeBGM() {
        if (mBGMusicDevice != null) {
            mBGMusicDevice.resume();
        }
    }

    public void startAudioDevice() {
        mIsStopped = false;
        super.setupHandler();

        boolean isOpenSuccess = mAudioRecorderDevice.openAudioRecorder();
        if (!isOpenSuccess) {
            if (mInnerErrorListener != null) {
                mInnerErrorListener.onFinish(false, Constraints.MSG_FAILED_ARG1_REASON_AUDIO_MIC, "Mic open failed");
            }
        }
    }

    private OnFinishListener mInnerErrorListener;

    public void setInnerErrorListener(OnFinishListener errorListener) {
        mInnerErrorListener = errorListener;
    }

    private boolean mIsStopped = false;

    public void stopAudioDevice() {
        if (mIsStopped) {
            return;
        }
        mIsStopped = true;

        mAudioRecorderDevice.closeAudioRecorder();
        super.destroyHandler();
    }

    private OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener;

    public void setOnEncodedFrameUpdateListener(OnEncodedFrameUpdateListener listener) {
        mOnEncodedFrameUpdateListener = listener;
    }


    public boolean startEncoder() {

        try {
            boolean isUseWiredOn = false;
            if (contextWeakRef.get() != null) {
                AudioManager audioManager = (AudioManager) contextWeakRef.get().getSystemService(Context.AUDIO_SERVICE);
                isUseWiredOn = audioManager.isWiredHeadsetOn();
            }
            if (isUseWiredOn) {
                Log.d(TAG, "isUseWiredOn true");
                // use Music because PreviewSession also use STREAM_MUSIC
                mAudioFilter.setup(false);
            } else {
                Log.d(TAG, "isUseWiredOn false");
                // Tricky: use STREAM_VOICE_CALL to avoid bgm recap when capturing
                // STREAM_VOICE_CALL is not loud, so mic will not record in
                mAudioFilter.setup(false, AudioManager.STREAM_VOICE_CALL,
                        mAudioRecorderDevice.getRecorderAudioSessionId());
            }

            if (mEnableBGM) {
                startBGMDevice();
            }
            // incase master track too long
            // most time, master track size is 1, about 50ms later
            mAudioFilter.clearMasterTrackQueue(); // not needed since startDevice just now.

            mAudioEncoder = new AudioMediaEncoder(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioEncoder.setOnProcessOverListener(mEncoderStatusListener);
            mAudioEncoder.setupEncoder(mSampleRateHz, mChannelCount, mBitrate / 1000);
            mAudioEncoder.setMediaFormatChangedListener(mMediaFormatChangedListener);
            // assume startFlv first, then startEncoder
            mAudioEncoder.setOnEncodedFrameUpdateListener(mOnEncodedFrameUpdateListener);
            mAudioFilter.setOnFilteredFrameUpdateListener(mOnFilteredFrameUpdateListener);

            mAudioEncoder.start();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    public void stopEncoder() {

//        mAudioFilter.resetBuffer(); // clear old data
        mAudioFilter.release();
        stopBGMDevice();
        mAudioFilter.setOnFilteredFrameUpdateListener(null);
        if (mAudioEncoder != null) {
            Log.i(TAG, "stop audio encoder");
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

    }

    private OnFinishListener mEncoderStatusListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            if (mInnerErrorListener != null) {
                mInnerErrorListener.onFinish(isSuccess, Constraints.MSG_FAILED_ARG1_REASON_ENCODER, extraNote);
            }
        }
    };


    private volatile MediaFormatChangedListener mMediaFormatChangedListener = null;

    public void setMediaFormatChangedListener(MediaFormatChangedListener listener) {
        mMediaFormatChangedListener = listener;
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

    private OnDeviceFrameUpdateListener mOnDeviceFrameUpdateListener = new OnDeviceFrameUpdateListener() {

        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (bufferData != null && bufferInfo.size > 0) {
                mAudioFilter.pushDataForMasterTrack(bufferData, bufferInfo);
            }
            return 0;
        }
    };

    private OnFilteredFrameUpdateListener mOnFilteredFrameUpdateListener = new OnFilteredFrameUpdateListener() {

        @Override
        public void onFilteredFrameUpdate(byte[] data, MediaCodec.BufferInfo bufferInfo) {
            if (mAudioEncoder != null) {
                mAudioEncoder.push(data, bufferInfo.size, bufferInfo.presentationTimeUs);
            }
        }
    };

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
