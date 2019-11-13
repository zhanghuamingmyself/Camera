package com.ztn.camera.session.track;

import android.content.Context;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.filter.VideoFilter;
import com.baidu.cloud.mediaprocess.listener.OnDeviceVideoSizeChangedListener;

import java.util.List;

/**
 * background process video
 * mention: config videoFilter to go through
 * Created by baidu on 2017/3/23.
 */

public class VideoPreviewSession implements SurfaceHolder.Callback {
    public static final String TAG = "VideoPreviewSession";
    private volatile VideoFilter mVideoFilter;

    private volatile boolean mIsVideoSizeChanged = false;
    private volatile boolean mIsSurfaceCreated = false;

    public VideoPreviewSession(Context context) {
        // get portrait inner

        mVideoFilter = new VideoFilter();
        mVideoFilter.setup();
    }

    private volatile Surface mInputSurface;

    public Surface getInputSurface() {
        if (mInputSurface == null) {
            mInputSurface = new Surface(mVideoFilter.getFilterInputSurfaceTexture());
        }
        return mInputSurface;
    }

    public void setSurfaceHolder(SurfaceHolder holder) {
        if (holder == null) {
            Log.d(TAG, "setSurfaceHolder is null");
        }
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private OnDeviceVideoSizeChangedListener mSizeChangeListener = new OnDeviceVideoSizeChangedListener() {
        @Override
        public void onDeviceVideoSizeChanged(int videoWidth, int videoHeight, int videoOrientation) {
            synchronized (mVideoFilter) {
                if (mVideoFilter != null) {
                    // set input size
                    int width = videoWidth;
                    int height = videoHeight;
                    // if width and height with orientation , should swich them
                    if (videoOrientation == 90 || videoOrientation == 270) {
                        width = videoHeight;
                        height = videoWidth;
                    }
                    mVideoFilter.setInputSize(width, height);
                    if (mIsSurfaceCreated) {
                        Log.d(TAG, "onSurfaceCreated from onDeviceVideoSizeChanged");
                        mVideoFilter.setPreviewSurface(mSurfacePreview);
                        mVideoFilter.resume();
                        if (mPreviewWidth > 0 && mPreviewHeight > 0) {
                            mVideoFilter.setPreviewSurfaceSize(mPreviewWidth, mPreviewHeight);
                        }
                    }
                    mIsVideoSizeChanged = true;
                }
            }

        }
    };

    // MediaProcessSession get this listener & set to MediaDecoderDevice
    public OnDeviceVideoSizeChangedListener getOnDeviceVideoSizeChangedListener() {
        return mSizeChangeListener;
    }

    /**
     * setup when start push!
     *
     * @return
     */
    public boolean startPreview() {
        try {

            mIsVideoSizeChanged = false;

            // set filters before setup is failed. so here set it again;
            if (mGPUImageFilters != null) {
                mVideoFilter.setGPUImageFilters(mGPUImageFilters);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return false;
        }
    }

    /**
     * can start/stop many times
     */
    public void stopPreview() {
        mVideoFilter.pause(); // release gl
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
    }

    /**
     * release only once for on session
     */
    public void release() {

        mVideoFilter.release();
    }

    List<GPUImageFilter> mGPUImageFilters = null;

    private volatile boolean mIsFilterSurfaceCreated = true;

    /**
     * 设置滤镜列表
     *
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mGPUImageFilters = filters;
        mVideoFilter.setGPUImageFilters(filters);
    }

    /**
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfacePreview = holder.getSurface();
        Log.d(TAG, "SurfacePreview:" + mSurfacePreview);
        synchronized (mVideoFilter) {
            mIsSurfaceCreated = true;
            if (mVideoFilter != null) {
                if (mIsVideoSizeChanged) {
                    mVideoFilter.setPreviewSurface(holder.getSurface());
                    mVideoFilter.resume();
                }
            }
        }
    }

    private volatile Surface mSurfacePreview;
    private volatile int mPreviewWidth = 0;
    private volatile int mPreviewHeight = 0;

    /**
     * @param holder
     * @param format
     * @param width
     * @param height
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
        if (mVideoFilter != null) {
            // it will change the preview size
            mVideoFilter.setPreviewSurfaceSize(width, height);
        }
    }

    /**
     * @param holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfacePreview = null;
        mIsSurfaceCreated = false;
        if (mVideoFilter != null) {
            mVideoFilter.setPreviewSurface(null);
            mVideoFilter.pause();
        }
    }
}
