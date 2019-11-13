package com.ztn.camera.session.track;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;

import com.baidu.cloud.gesturedetector.FaceDetector;
import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.device.CameraCaptureDevice;
import com.baidu.cloud.mediaprocess.encoder.VideoMediaEncoder;
import com.baidu.cloud.mediaprocess.filter.VideoFilter;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFilteredFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.session.Constraints;

import java.util.List;

/**
 * connect: videodevice, videoFilter,videoEncoder,and othersaver?
 * <p>
 * Created by baidu on 2017/3/27.
 */

public class VideoCaptureSession implements SurfaceHolder.Callback {
    private static final String TAG = "VideoCaptureSession";
    private static final String VCODEC = MediaFormat.MIMETYPE_VIDEO_AVC;

    private VideoFilter mVideoFilter;
    private CameraCaptureDevice mCameraCaptureDevice;
    private int mCameraRotation;
    private VideoMediaEncoder mVideoEncoder;

    private int mTargetWidth;
    private int mTargetHeight;
    private int mBitrate;
    private int mFps;
    private int mGopLengthInSeconds;
    private int mDefaultCameraId;
    private boolean isFrontCameraEncodeMirror = true;

    private int previewWidth = -1;
    private int previewHeight = -1;

//    private boolean mIsEnergySaving = false;

    public VideoCaptureSession(int targetWidth, int targetHeight,
                               int bitrate, int fps, int gopInSeconds, int defaultCameraId,
                               boolean isVideoEnabled, int cameraRotation, int outputOrientation) {
        // get portrait inner
        mTargetWidth = targetWidth;
        mTargetHeight = targetHeight;
        mCameraRotation = cameraRotation;
        mDefaultCameraId = defaultCameraId;
//        mIsEnergySaving = isEnergySaving;
        mBitrate = bitrate;
        mFps = fps;
        mGopLengthInSeconds = gopInSeconds;

        mVideoFilter = new VideoFilter();
        mVideoFilter.setEncodingEnabled(isVideoEnabled);
        mVideoFilter.setup();

        mCameraCaptureDevice = new CameraCaptureDevice(mTargetWidth, mTargetHeight, mFps,
                mDefaultCameraId, cameraRotation);

        mVideoFilter.setEncodeSize(mTargetWidth, mTargetHeight, outputOrientation);
    }

    public void setEpochTimeInNs(long epochTimeInNs) {
        if (mVideoFilter != null) {
            mVideoFilter.setEpochTimeInNs(epochTimeInNs);
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

    public void setFaceDetector(FaceDetector faceDetector) {
        mVideoFilter.setFaceDetector(faceDetector);
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
            mVideoEncoder.setupEncoder(mTargetWidth, mTargetHeight, mBitrate / 1000, mFps, mGopLengthInSeconds);

            mVideoEncoder.setOnEncodedFrameUpdateListener(mOnEncodedFrameUpdateListener);

            mVideoFilter.setEncodeSurface(mVideoEncoder.getInputSurface());
            mVideoFilter.setOnFilteredFrameUpdateListener(mOnFilteredFrameUpdateListener);

            mVideoEncoder.start();

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

    public void setSurfaceHolder(SurfaceHolder holder) {
        if (holder == null) {
            Log.d(TAG, "setSurfaceHolder is null");
        }
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        if (!mCameraCaptureDevice.openCamera(mTargetWidth, mTargetHeight, mFps, mDefaultCameraId)) {
            if (mInnerErrorListener != null) {
                mInnerErrorListener.onFinish(false, Constraints.MSG_FAILED_ARG1_REASON_CAMERA, "camera open failed");
            }
            return;
        }

        if (mVideoFilter != null) {
            mVideoFilter.setPreviewSurface(holder.getSurface());
            Camera.Size cameraSize = mCameraCaptureDevice.getCameraSize();
            // set camera size
            mVideoFilter.setInputSize(cameraSize.width, cameraSize.height);
            // reset when switch camera
            mVideoFilter.setOutputHorizonFlip(isFrontCameraEncodeMirror ? false :
                    mCameraCaptureDevice.getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT);

            mCameraCaptureDevice.startCameraPreview(mVideoFilter.getFilterInputSurfaceTexture());
            mVideoFilter.resume();
        }

    }

    /**
     * @param holder
     * @param format
     * @param width
     * @param height
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged");
        if (mVideoFilter != null) {
            // it will change the preview size
            mVideoFilter.setPreviewSurfaceSize(width, height);
        }
        previewWidth = width;
        previewHeight = height;
    }

    /**
     * @param holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        mCameraCaptureDevice.closeCamera();
        if (mVideoFilter != null) {
            mVideoFilter.setPreviewSurface(null);
            mVideoFilter.pause();
        }
    }

    /**
     * 当使用前置摄像头录制时，编码结果是否使用镜子模式,默认为true.
     * 注：前置摄像头录制时，实时预览始终是镜子模式。该设置是改变编码结果的。
     * 推流直播时，推荐为false
     * 录制短视频时，推荐不设置镜子模式，使用默认值true
     *
     * @param isFrontCameraEncodeMirror
     */
    public void setFrontCameraEncodeMirror(boolean isFrontCameraEncodeMirror) {
        this.isFrontCameraEncodeMirror = isFrontCameraEncodeMirror;
    }

    /**
     * close video device. can stop device manually, not rely on surfaceDestroyed
     */
    public void stopVideoDevice() {
        mCameraCaptureDevice.closeCamera();
        if (mVideoFilter != null) {
            // FIXME mVideoFilter not use surfaceholder actually
            mVideoFilter.setPreviewSurface(null);
            mVideoFilter.release();
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

    public void toggleFlash(boolean flag) {
        mCameraCaptureDevice.toggleFlash(flag);
    }

    public boolean canSwitchCamera() {
        return mCameraCaptureDevice.canSwitchCamera();
    }

    public void switchCamera(int cameraId) {
        if (mDefaultCameraId == cameraId) {
            return;
        }
        mDefaultCameraId = cameraId;

        mVideoFilter.pause();
        mCameraCaptureDevice.switchCamera(cameraId);

        // reopen camera, must set new mirror and camera size
        mVideoFilter.setOutputHorizonFlip(isFrontCameraEncodeMirror ? false :
                mCameraCaptureDevice.getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT);
        Camera.Size cameraSize = mCameraCaptureDevice.getCameraSize();
        mVideoFilter.setInputSize(cameraSize.width, cameraSize.height);
        mVideoFilter.resume(); // re-create preview internals

        mCameraCaptureDevice.startCameraPreview(mVideoFilter.getFilterInputSurfaceTexture());
    }

    /**
     * 设置对焦焦点
     *
     * @param x
     * @param y
     */
    public void focusToPoint(int x, int y) {
        if (previewWidth != -1 && previewHeight != -1) {
            mCameraCaptureDevice.focusToPoint(x, y, previewWidth, previewHeight);
        }
    }

    /**
     * 获取相机最大的放大因子
     */
    public int getMaxZoomFactor() {
        return mCameraCaptureDevice.getMaxZoomFactor();
    }

    /**
     * 设置相机放大因子
     *
     * @param factor
     */
    public boolean setZoomFactor(int factor) {
        return mCameraCaptureDevice.setZoomFactor(factor);
    }
}
