package com.ztn.camera.session.track;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.mediaprocess.encoder.AudioMediaEncoder;
import com.baidu.cloud.mediaprocess.filter.AudioFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFilteredFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.Constraints;
import com.ztn.camera.session.HandlerThreadSession;
import java.nio.ByteBuffer;

import static com.ztn.camera.session.Constraints.MSG_BGM_FINISHED;


/**
 * can add bgmusic and so on
 * Created by baidu on 2017/3/23.
 */

public class AudioConcatSession extends HandlerThreadSession {
    private static final String TAG = "AudioConcatSession";

    private int mSampleRateHz;
    private int mChannelCount = 2; // always two
    private int mBitrate; // used by encoder

//    private MediaDecoderDevice mBGMusicDevice;
//    private int mBGMFilterTrack = -1;
    private AudioFilter mAudioFilter;
    private volatile AudioMediaEncoder mAudioEncoder;

//    private MediaMuxer mMediaMuxer;
//    private volatile int mMp4AudioTrack = -1;

    /**
     *
     * @param sampleRateHz sample rate, not bigger that origin sample rate
     * @param channelCount only support 2 channel now
     * @param bitrate
     */
    public AudioConcatSession(int sampleRateHz, int channelCount, int bitrate) {
        mSampleRateHz = sampleRateHz;
        mChannelCount = channelCount;
        mBitrate = bitrate;

        mAudioFilter = new AudioFilter();
    }

    private OnFinishListener mEncodeOverListener;

    /**
     * should invoke after startEncoder
     * @param listener
     */
    public void setOnEncodedOverListener(OnFinishListener listener) {
        mEncodeOverListener = listener;
    }


//    private boolean mEnableBGM = false;
//    private String mBGMFilePath = null;
//    private volatile boolean mIsBGMLooping = false;
//
//    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
//        mEnableBGM = enableBGM;
//        mBGMFilePath = bgmPath;
//        mIsBGMLooping = isLooping;
//    }
//
//    /**
//     * invoke by startAudioDevice
//     */
//    private void startBGMDevice() {
//        if (mBGMusicDevice != null) {
//            mBGMusicDevice.stopDecoder();
//            mBGMusicDevice = null;
//        }
//        try {
//            mBGMusicDevice = new MediaDecoderDevice(mBGMFilePath);
//            mBGMusicDevice.configClip(mClipStartPositionInUSec, mClipDurationInUSec);
//            mBGMusicDevice.setOnDecodeStateChangeListener(mBGMDecodeStateListener);
//            mBGMusicDevice.setIsSyncWithSystemTime(false);
//            mBGMusicDevice.setExtractAudioEnabled(true);
//            mBGMusicDevice.setExtractVideoEnabled(false);
//            mBGMusicDevice.setOnAudioDeviceFrameUpdateListener(mOnBGMusicDeviceUpdateListener);
//
//            // when looping bgm, mBGMFilterTrack should not been reset
//            if (mBGMFilterTrack < 0) {
//                mBGMFilterTrack = mAudioFilter.addSubTrack();
//            }
//
//            mBGMusicDevice.startDecoder();
//        } catch (Exception e) {
//            Log.d(TAG, Log.getStackTraceString(e));
//        }
//
//    }
//
//    private long mClipStartPositionInUSec = -1;
//    private long mClipDurationInUSec = -1;
//
//    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
//        mClipStartPositionInUSec = clipStartInUSec;
//        mClipDurationInUSec = clipDurationInUSec;
//    }

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
     * invoke by stopAudioDevice
     */
//    private void stopBGMDevice() {
//        if (mBGMusicDevice != null) {
//            mBGMusicDevice.stopDecoder();
//            mBGMusicDevice = null;
//            mBGMFilterTrack = -1;
//        }
//    }

    private void startAudioDevice() {
        mIsStopped = false;
        mAudioFilter.setNeedRendering(false);
        mAudioFilter.setup(false);
//        if (mEnableBGM) {
//            startBGMDevice();
//        }
    }

    private boolean mIsStopped = false;

    private void stopAudioDevice() {
        if (mIsStopped) {
            return;
        }
        mIsStopped = true;

        mAudioFilter.release(); // will execute resetbuffer
//        stopBGMDevice();
    }

    public boolean startEncoder() {
        super.setupHandler();

        startAudioDevice();
        try {
            mAudioEncoder = new AudioMediaEncoder(MediaFormat.MIMETYPE_AUDIO_AAC);
            if (mEncodeOverListener != null) {
                mAudioEncoder.setOnProcessOverListener(mEncoderStatusListener);
            }
            mAudioEncoder.setupEncoder(mSampleRateHz, mChannelCount, mBitrate/1000);
            mAudioEncoder.setMediaFormatChangedListener(mMediaFormatChangedListener);


            mAudioEncoder.start();
            mAudioEncoder.setOnEncodedFrameUpdateListener(mOnEncodedFrameUpdateListener);
            mAudioFilter.setOnFilteredFrameUpdateListener(mOnFilteredFrameUpdateListener);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    private OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener;

    public void setOnEncodedFrameUpdateListener(OnEncodedFrameUpdateListener listener) {
        mOnEncodedFrameUpdateListener = listener;
    }

    public void stopEncoder() {
        mAudioFilter.setOnFilteredFrameUpdateListener(null);
        if (mAudioEncoder != null) {
            Log.i(TAG, "stop audio encoder");
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        stopAudioDevice();
        super.destroyHandler();
    }

    private OnFinishListener mEncoderStatusListener = new OnFinishListener() {
        @Override
        public void onFinish(boolean isSuccess, int whatReason, String extraNote) {
            if (mEncodeOverListener != null) {
                mEncodeOverListener.onFinish(isSuccess, Constraints.MSG_FAILED_ARG1_REASON_ENCODER, extraNote);
            }
        }
    };

//    public void setMediaMuxer(MediaMuxer mediaMuxer) {
//        mMediaMuxer = mediaMuxer;
//    }

    private volatile MediaFormatChangedListener mMediaFormatChangedListener = null;

    public void setMediaFormatChangedListener(MediaFormatChangedListener listener) {
        mMediaFormatChangedListener = listener;
    }

//    public void setMp4AudioTrack(int trackId) {
//        mMp4AudioTrack = trackId;
//    }

//    private OnDeviceFrameUpdateListener mOnBGMusicDeviceUpdateListener = new OnDeviceFrameUpdateListener() {
//        @Override
//        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
////            Log.d(TAG, "bgm onDeviceFrameUpdateSoon pts=" + bufferInfo.presentationTimeUs + ";size="
////                    + bufferInfo.size);
//            if (mBGMFilterTrack >= 0) {
//                if (bufferData != null && bufferInfo.size > 0) {
//                    mAudioFilter.pushDataForSubTrack(bufferData, bufferInfo, mBGMFilterTrack);
//                } else {
//                    Log.d(TAG, "bgm END OF STREAM");
//                }
//
//            }
//            return 0;
//        }
//    };


    private OnDeviceFrameUpdateListener mDeviceFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mAudioFilter != null) {

                mAudioFilter.pushDataForMasterTrack(bufferData, bufferInfo);
            }
            return 0;
        }
    };

    public OnDeviceFrameUpdateListener getOnAudioFrameUpdateListener() {
        return mDeviceFrameUpdateListener;
    }

    private OnFilteredFrameUpdateListener mOnFilteredFrameUpdateListener = new OnFilteredFrameUpdateListener() {

        @Override
        public void onFilteredFrameUpdate(byte[] data, MediaCodec.BufferInfo bufferInfo) {
            if (mAudioEncoder != null) {
//                Log.d(TAG, "onFilteredFrameUpdate pts=" + bufferInfo.presentationTimeUs + ";size=" + bufferInfo.size);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "onDeviceFrameUpdateSoon end of stream");
                    // end of stream
                    // notif end
                    mAudioEncoder.signalEndOfInputStream(bufferInfo.presentationTimeUs);
                } else {
                    mAudioEncoder.push(data, bufferInfo.size, bufferInfo.presentationTimeUs);
                }

            }
        }
    };

//    private volatile long lastPts = 0L;
//
//    private OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener = new OnEncodedFrameUpdateListener() {
//
//        @Override
//        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
//
//            if (mMediaMuxer != null && mMp4AudioTrack >= 0) {
//                try {
//                    long pts = bufferInfo.presentationTimeUs;
//
//                    if (pts == 0) { // first encode: audio config or sps, pts is 0
//                        if (bufferInfo.size < 10) {
//                            // pts is 0 and is audio-config info; we already have this in output format
//                            return;
//                        }
//                    }
//
//                    if (pts < lastPts) {
//                        bufferInfo.presentationTimeUs = lastPts + 1;
//                    }
//
//                    lastPts = bufferInfo.presentationTimeUs;
//
//                    ByteBuffer record = bufferData.duplicate();
//                    mMediaMuxer.writeSampleData(mMp4AudioTrack, record, bufferInfo);
//                } catch (Exception e) {
//                    Log.e(TAG, "mediamuxer write audio sample failed.");
//                    e.printStackTrace();
//                }
//            }
//        }
//    };

//    private MediaDecoderDevice.OnDecodeStateChangeListener mBGMDecodeStateListener
//            = new MediaDecoderDevice.OnDecodeStateChangeListener() {
//
//        @Override
//        public void onFinish(boolean isSuccess) {
//            Log.d(TAG, "BGM is over; isSuccess=" + isSuccess);
//            if (!mIsStopped) {
//                Message msg = new Message();
//                msg.what = MSG_BGM_FINISHED;
//                msg.arg1 = isSuccess? 1 : 0;
//                sendMessageToHandlerThread(msg);
//            }
//
//        }
//
//        @Override
//        public void onProgress(int progress) {
//            // don't use progress here
//        }
//
//        @Override
//        public void onDurationUpdated(int durationInMilliSec) {
//            // don't use here
//        }
//    };

    @Override
    protected void handleMessageInHandlerThread(Message msg) {
        switch (msg.what) {
            case MSG_BGM_FINISHED:
                boolean isSuccess = msg.arg1 == 1;
                if (!isSuccess) {
                    // send failed message to outer!
                } else {
                    // looping bgm? --- restart
//                    if (!mIsStopped && mIsBGMLooping) {
//                        startBGMDevice();
//                    }

                }
                break;
            default:
                break;
        }
    }

}
