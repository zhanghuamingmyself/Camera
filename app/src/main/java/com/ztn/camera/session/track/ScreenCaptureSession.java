package com.ztn.camera.session.track;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.encoder.VideoMediaEncoder;
import com.baidu.cloud.mediaprocess.filter.VideoFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFilteredFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.Constraints;

import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * connect: videodevice, videoFilter,videoEncoder,and othersaver?
 * <p>
 * Created by baidu on 2017/3/27.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenCaptureSession {
    private static final String TAG = "ScreenCaptureSession";
    private static final String VCODEC = MediaFormat.MIMETYPE_VIDEO_AVC;

    private VideoFilter mVideoFilter;
    private VideoMediaEncoder mVideoEncoder;

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private int mScreenDensity;
    private int mResultCode;
    private Intent mResultData;

    private int mTargetWidth;
    private int mTargetHeight;
    private boolean mIsOrientationPortrait = false;
    private int mBitrate;
    private int mFps;
    private int mGopLengthInSeconds;

    public ScreenCaptureSession(Context context, int targetWidth, int targetHeight, boolean isOrientationPortrait,
                                int bitrate, int fps, int gopInSeconds,
                                boolean isVideoEnabled) {
        // get portrait inner
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mMediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mTargetWidth = targetWidth;
        mTargetHeight = targetHeight;
        mIsOrientationPortrait = isOrientationPortrait;
        mBitrate = bitrate;
        mFps = fps;
        mGopLengthInSeconds = gopInSeconds;

        mVideoFilter = new VideoFilter();
        mVideoFilter.setEncodingEnabled(isVideoEnabled);
        mVideoFilter.setup();

        if (mIsOrientationPortrait) {
            // rotate size of target
            mVideoFilter.setInputSize(mTargetHeight, mTargetWidth);
            mVideoFilter.setEncodeSize(mTargetHeight, mTargetWidth);
        } else {
            mVideoFilter.setInputSize(mTargetWidth, mTargetHeight);
            mVideoFilter.setEncodeSize(mTargetWidth, mTargetHeight);
        }
    }

    public void setEpochTimeInNs(long epochTimeInNs) {
        if (mVideoFilter != null) {
            mVideoFilter.setEpochTimeInNs(epochTimeInNs);
        }
    }

    public void startMediaProjection(int resultCode, Intent resultData) {
        mResultCode = resultCode;
        mResultData = resultData;
        if (mVirtualDisplay == null) {
            if (mMediaProjection == null && mResultCode != 0 && mResultData != null) {
                mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
            }
            setUpVirtualDisplay();
        }
    }

    private void setUpVirtualDisplay() {
        Log.i(TAG, "Setting up a VirtualDisplay: "
                + mTargetWidth + "x" + mTargetHeight
                + " (" + mScreenDensity + ")");
        SurfaceTexture surfaceTexture = mVideoFilter.getFilterInputSurfaceTexture();
        if (mIsOrientationPortrait) {
            surfaceTexture.setDefaultBufferSize(mTargetHeight, mTargetWidth);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                    mTargetHeight, mTargetWidth, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    new Surface(surfaceTexture), null, null);
        } else {
            surfaceTexture.setDefaultBufferSize(mTargetWidth, mTargetHeight);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                    mTargetWidth, mTargetHeight, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    new Surface(surfaceTexture), null, null);
        }
    }

    private OnFilteredFrameUpdateListener mOnFilteredFrameUpdateListener = new OnFilteredFrameUpdateListener() {

        @Override
        public void onFilteredFrameUpdate(byte[] data, MediaCodec.BufferInfo info) {
            // hardware
            if (mVideoEncoder != null) {
                mVideoEncoder.frameAvailableSoon();
            }
        }
    };

    OnEncodedFrameUpdateListener mOnEncodedFrameUpdateListener;

    public void setOnEncodedFrameUpdateListener(OnEncodedFrameUpdateListener listener) {
        mOnEncodedFrameUpdateListener = listener;
    }

    /**
     * setup when start push!
     *
     * @return
     */
    public boolean startEncoder() {
        try {
            mVideoEncoder = new VideoMediaEncoder(VCODEC);
            mVideoEncoder.setOnProcessOverListener(mEncoderStatusListener);
            mVideoEncoder.setMediaFormatChangedListener(mMediaFormatChangedListener);
            if (mIsOrientationPortrait) {
                mVideoEncoder.setupEncoder(mTargetHeight, mTargetWidth, mBitrate / 1000, mFps, mGopLengthInSeconds);
            } else {
                mVideoEncoder.setupEncoder(mTargetWidth, mTargetHeight, mBitrate / 1000, mFps, mGopLengthInSeconds);
            }

            mVideoEncoder.setOnEncodedFrameUpdateListener(mOnEncodedFrameUpdateListener);

            mVideoFilter.setEncodeSurface(mVideoEncoder.getInputSurface());
            mVideoFilter.setOnFilteredFrameUpdateListener(mOnFilteredFrameUpdateListener);

            mVideoEncoder.start();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < (mFps << 1); i++) {
                        mVideoFilter.refreshVideoFrame();
                        mVideoEncoder.requestKeyFrame();
                        try {
                            Thread.sleep(1000 / mFps);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            return true;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    public boolean requestKeyFrame() {
        return mVideoEncoder == null ? false : mVideoEncoder.requestKeyFrame();
    }

    public void stopEncoder() {
        mVideoFilter.setEncodeSurface(null);
        mVideoFilter.setOnFilteredFrameUpdateListener(null); // may nullexception in VideoFilter
        if (mVideoEncoder != null) {
            Log.i(TAG, "stop video encoder");
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder.setOnEncodedFrameUpdateListener(null);
            mVideoEncoder = null;
        }
    }


    private OnFinishListener mInnerErrorListener;

    public void setInnerErrorListener(OnFinishListener errorListener) {
        mInnerErrorListener = errorListener;
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

    /**
     * close video device. can stop device manually, not rely on surfaceDestroyed
     */
    public void stopVideoDevice() {
        if (mVideoFilter != null) {
            // FIXME mVideoFilter not use surfaceholder actually
            mVideoFilter.setPreviewSurface(null);
            mVideoFilter.release();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }

    /**
     * dynamic change bitrate
     *
     * @param bitrateInKbps
     */
    public void changeBitrate(int bitrateInKbps) {
        if (mVideoEncoder != null) {
            mVideoEncoder.changeBitrate(bitrateInKbps);
        }
    }

    public Bitmap getScreenShot() {
        if (mVideoFilter != null) {
            return mVideoFilter.getScreenShot();
        }
        return null;
    }

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mVideoFilter.setGPUImageFilters(filters);
    }

}
