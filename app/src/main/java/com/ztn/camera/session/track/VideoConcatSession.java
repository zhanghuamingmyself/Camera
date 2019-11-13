package com.ztn.camera.session.track;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.encoder.VideoMediaEncoder;
import com.baidu.cloud.mediaprocess.filter.VideoFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceVideoSizeChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFilteredFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.Constraints;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * background process video
 * mention: config videoFilter to go through
 * Created by baidu on 2017/3/23.
 */

public class VideoConcatSession {
    public static final String TAG = "VideoProcessSession";
    private static final String VCODEC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private VideoFilter mVideoFilter;

    private VideoMediaEncoder mNewVideoEncoder;
//    private MediaMuxer mMediaMuxer;
//    private volatile int mMp4VideoTrack = -1;

    private int mTargetWidth;
    private int mTargetHeight;

    private int mBitrate;
    private int mFps;
    private int mGopLengthInSeconds;

    public VideoConcatSession(Context context, int targetWidth, int targetHeight,
                              int bitrate, int fps, int gopInSeconds) {
        // get portrait inner
        mTargetWidth = targetWidth;
        mTargetHeight = targetHeight;

        mBitrate = bitrate;
        mFps = fps;
        mGopLengthInSeconds = gopInSeconds;

        mVideoFilter = new VideoFilter();

    }

    private Surface mInputSurface;

    public Surface getInputSurface() {
        if (mInputSurface == null) {
            mInputSurface = new Surface(mVideoFilter.getFilterInputSurfaceTexture());
        }
        return mInputSurface;
    }

    private volatile boolean isSurfaceCreated = false;

    private OnDeviceVideoSizeChangedListener mSizeChangeListener = new OnDeviceVideoSizeChangedListener() {
        @Override
        public void onDeviceVideoSizeChanged(int videoWidth, int videoHeight, int videoOrientation) {
            if (mVideoFilter != null) {
                if (isSurfaceCreated) {
                    // destroy first
                    mVideoFilter.pause();
                }
                // set input size
                int width = videoWidth;
                int height = videoHeight;
                // if width and height with orientation , should swich them
                if (videoOrientation == 90 || videoOrientation == 270) {
                    width = videoHeight;
                    height = videoWidth;
                }
                mVideoFilter.setInputSize(width, height);
                mVideoFilter.resume();
                isSurfaceCreated = true;
            }
        }
    };

    // MediaProcessSession get this listener & set to MediaDecoderDevice
    public OnDeviceVideoSizeChangedListener getOnDeviceVideoSizeChangedListener() {
        return mSizeChangeListener;
    }

    private OnDeviceFrameUpdateListener mDeviceFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            if (mVideoFilter != null) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "onDeviceFrameUpdateSoon end of stream;mNewVideoEncoder=" + mNewVideoEncoder);
                    // end of stream; notif end
                    if (mNewVideoEncoder != null) {
                        mNewVideoEncoder.signalEndOfInputStream();
                    }
                } else {
                    mVideoFilter.setCurrentPresentationTimeInUs(bufferInfo.presentationTimeUs);
                    int waitTime = computeWaitTime();
                    return waitTime;
                }
            }
            return 0;
        }
    };

    /**
     *
     * @return
     */
    public int computeWaitTime() {
        int maxFilterConsumingTime = 0;

        for (int i = 0; i < arrayLastFewConsumeTime.length; ++i) {
            if (maxFilterConsumingTime < arrayLastFewConsumeTime[i]) {
                maxFilterConsumingTime = arrayLastFewConsumeTime[i];
            }
        }
//        Log.d(TAG, "maxFilterConsumingTime =" + maxFilterConsumingTime);
        return maxFilterConsumingTime; // we now use maxFilterConsumingTime; may be you can multiply it by 1.2f;
    }

    public OnDeviceFrameUpdateListener getOnVideoFrameUpdateListener() {
        return mDeviceFrameUpdateListener;
    }


    private OnFinishListener mEncodeOverListener;

    /**
     * should invoke after startEncoder
     * @param listener
     */
    public void setOnEncodedOverListener(OnFinishListener listener) {
        mEncodeOverListener = listener;
    }

//    private volatile long lastPts = 0L;
//
//    private OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener = new OnEncodedFrameUpdateListener() {
//
//        @Override
//        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
//            if (mMediaMuxer != null && mMp4VideoTrack >= 0) {
//                try {
////                    Log.d(TAG, "mediamuxer write video sample size=" + bufferInfo.size + ";pts=" + bufferInfo.presentationTimeUs);
//                    long pts = bufferInfo.presentationTimeUs;
//
//                    if (pts == 0) { // first encode: audio config or sps, pts is 0
//                        if (bufferInfo.size < 100) {
//                            // pts is 0 and is sps,pps; we already have this in output format
//                            return;
//                        }
//                    }
//                    if (pts < lastPts) {
//                        bufferInfo.presentationTimeUs = lastPts + 1;
//                    }
//
//                    lastPts = bufferInfo.presentationTimeUs;
//                    ByteBuffer record = bufferData.duplicate();
//                    mMediaMuxer.writeSampleData(mMp4VideoTrack, record, bufferInfo);
//                } catch (Exception e) {
//                    Log.e(TAG, "mediamuxer write video sample failed.");
//                    e.printStackTrace();
//                }
//            }
//        }
//    };

    private final static int MAX_CONSUME_TIME_COUNT = 5;
    private volatile int[] arrayLastFewConsumeTime = new int[MAX_CONSUME_TIME_COUNT];
    private volatile int currentIndexOfLastFewConsumeTime = 0;


    private OnFilteredFrameUpdateListener mOnFilteredFrameUpdateListener = new OnFilteredFrameUpdateListener() {

        @Override
        public void onFilteredFrameUpdate(byte[] data, MediaCodec.BufferInfo info) {
//            Log.d(TAG, "onFilteredFrameUpdate ptsInUs = " + ptsInUs);
            // hardware
            if (mNewVideoEncoder != null) {
                mNewVideoEncoder.frameAvailableSoon();
            }

            if (info != null) {
                int consumeTime = info.offset;
//                Log.d(TAG, "consume time=" + consumeTime);
                arrayLastFewConsumeTime[currentIndexOfLastFewConsumeTime] = consumeTime;
                currentIndexOfLastFewConsumeTime++;
                currentIndexOfLastFewConsumeTime %= MAX_CONSUME_TIME_COUNT;
            }

            //TODO software encode do sth

        }
    };

    /**
     * setup when start push!
     * @return
     */
    public boolean startEncoder() {
        try {
            mVideoFilter.setup(); // config thread
            mVideoFilter.setEncodeSize(mTargetWidth, mTargetHeight);
            if (mGPUImageFilters != null) {
                mVideoFilter.setGPUImageFilters(mGPUImageFilters);
            }

            mNewVideoEncoder = new VideoMediaEncoder(VCODEC);
            mNewVideoEncoder.setMediaFormatChangedListener(mMediaFormatChangedListener);
            if (mEncodeOverListener != null) {
                mNewVideoEncoder.setOnProcessOverListener(mEncoderStatusListener);
            }
            mNewVideoEncoder.setupEncoder(mTargetWidth, mTargetHeight, mBitrate / 1000, mFps, mGopLengthInSeconds);
            mNewVideoEncoder.start();

            mNewVideoEncoder.setOnEncodedFrameUpdateListener(mOnEncodedFrameUpdateListener);

            mVideoFilter.setEncodeSurface(mNewVideoEncoder.getInputSurface());
            mVideoFilter.setOnFilteredFrameUpdateListener(mOnFilteredFrameUpdateListener);

            return true;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener;

    public void setOnEncodedFrameUpdateListener(OnEncodedFrameUpdateListener listener) {
        mOnEncodedFrameUpdateListener = listener;
    }

    public void stopEncoder() {
        mVideoFilter.setEncodeSurface(null);
        mVideoFilter.setOnFilteredFrameUpdateListener(null); // may nullexception in VideoFilter
        isSurfaceCreated = false;
        mVideoFilter.release();

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }

        if (mNewVideoEncoder != null) {
            Log.i(TAG, "stop video encoder");
            mNewVideoEncoder.stop();
            mNewVideoEncoder.release();
            mNewVideoEncoder.setOnEncodedFrameUpdateListener(null);
            mNewVideoEncoder = null;
        }
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

//    public void setMp4VideoTrack(int trackId) {
//        mMp4VideoTrack = trackId;
//    }

    List<GPUImageFilter> mGPUImageFilters = null;
    /**
     * 设置滤镜列表
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mGPUImageFilters = filters;
    }
}
