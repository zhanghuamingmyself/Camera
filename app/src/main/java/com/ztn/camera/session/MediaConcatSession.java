package com.ztn.camera.session;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Message;
import android.util.Log;

import com.baidu.cloud.gpuimage.basefilters.GPUImageFilter;
import com.baidu.cloud.mediaprocess.device.MediaDecoderDevice;
import com.baidu.cloud.mediaprocess.listener.MediaFormatChangedListener;
import com.baidu.cloud.mediaprocess.listener.OnDeviceFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnEncodedFrameUpdateListener;
import com.baidu.cloud.mediaprocess.listener.OnFinishListener;
import com.ztn.camera.config.ProcessConfig;
import com.ztn.camera.listener.ProcessStateListener;
import com.ztn.camera.session.track.AudioConcatSession;
import com.ztn.camera.session.track.VideoConcatSession;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

/**
 *
 * 合并多个mp4文件
 * 注：如果mp4的编码格式、编码参数相同，推荐使用MediaQuickConcatSession --- 该类的合并速度极快！
 *
 * 如果文件分辨率等参数不同，则使用该类进行合成。因为涉及到重新编解码，速度会比较慢。
 *
 * start: start concat process
 * stop: stop concat process
 *
 * contain AudioConcatSession && VideoConcatSession
 *
 */

public class MediaConcatSession extends HandlerThreadSession {
    public static final String TAG = "MediaConcatSession";

    public static final int MSG_SELFDEFINED_ONESRC_DONE = 10000;

    AudioConcatSession mAudioConcatSession;
    VideoConcatSession mVideoConcatSession;
    MediaDecoderDevice mMediaDecoderDevice;

//    private volatile Mp4Muxer mMp4Muxer;
    private MediaMuxer mMediaMuxer;

//    private String mFilePath = "";
    private List<ConcatMp4File> mConcatMp4FileList;
    private volatile int mCurrentIndex = 0;

    private volatile boolean mIsDecodeVideo = false;
    private volatile boolean mIsDecodeAudio = false;

    private volatile boolean mIsEncodeVideoDone = false;
    private volatile boolean mIsEncodeAudioDone = false;

    private ProcessStateListener mProcessStateListener = null;

    /**
     * 构造函数，如果想复用session，需要
     * @param context
     * @param config should contains only video/audio, contain file-path and so on!!!
     */
    public MediaConcatSession(Context context, ProcessConfig config) {
        mVideoConcatSession = new VideoConcatSession(context, config.getVideoWidth(),
                config.getVideoHeight(),
                config.getInitVideoBitrate(), config.getVideoFPS(), config.getGopLengthInSeconds());
        mAudioConcatSession = new AudioConcatSession(config.getAudioSampleRate(),
                config.getAudioChannelCount(), config.getAudioBitrate());

        mVideoConcatSession.setOnEncodedOverListener(videoEncodedOverListener);
        mAudioConcatSession.setOnEncodedOverListener(audioEncodedOverListener);

        mIsDecodeVideo = config.isVideoEnabled(); // use setVideoAudioEnabled to set value
        mIsDecodeAudio = config.isAudioEnabled(); // use setVideoAudioEnabled to set value
    }

    public void setProcessStateListener(ProcessStateListener listener) {
        mProcessStateListener = listener;
    }


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
                sendMessageToHandlerThread(obtainMessage(Constraints.MSG_FAILED));
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
                sendMessageToHandlerThread(obtainMessage(Constraints.MSG_FAILED));
            }
        }
    };

    private void judgeEncodeStatus() {
        if ((!mIsDecodeAudio || mIsEncodeAudioDone) // don't decode audio or done
                && (!mIsDecodeVideo || mIsEncodeVideoDone) // don't decode video or done
                ) {
            sendMessageToHandlerThread(obtainMessage(Constraints.MSG_SUCCESS));
        }
    }

    public void setSrcFileList(List<ConcatMp4File> srcFiles) {
        mConcatMp4FileList = srcFiles;
    }

//    public void setMediaFilePath(String mediaFilePath) {
//        mFilePath = mediaFilePath;
//    }

    private long startTimeInMilliSeconds = 0L;

    public void start() {
        try {
            if (!mIsDecodeAudio && !mIsDecodeVideo) {
                Log.e(TAG, "start failed! not decode audio and video, nothing to be done");
                return;
            }

            if (mConcatMp4FileList == null || mConcatMp4FileList.size() < 2) {
                throw new IllegalArgumentException("SrcFileList.size() must be no less than 2");
            }

            mIsEncodeAudioDone = false;
            mIsEncodeVideoDone = false;
            super.setupHandler();

            resetTrackValue();

            startTimeInMilliSeconds = System.currentTimeMillis();
            isStopped = false;
            // try fetch total duration
            tryFetchTotalDuration();

            muxerAudioTrack = -1;
            muxerVideoTrack = -1;

            if (mSaveMp4) {
                File file = new File(mLocalMp4Path);
                if (file.exists()) {
                    file.delete();
                }
                mMediaMuxer = new MediaMuxer(mLocalMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
            mAudioConcatSession.setMediaFormatChangedListener(mAudioMediaFormatChangeListener);
            mVideoConcatSession.setMediaFormatChangedListener(mVideoMediaFormatChangeListener);

            mAudioConcatSession.setOnEncodedFrameUpdateListener(mOnEncodedAudioFrameUpdateListener);
            mVideoConcatSession.setOnEncodedFrameUpdateListener(mOnEncodedVideoFrameUpdateListener);

            //
            mVideoConcatSession.startEncoder();
            mAudioConcatSession.startEncoder();

            reStartNewDevice();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
            if (mProcessStateListener != null) {
                mProcessStateListener.onFinish(false, 0);
            }
        }

    }

    private volatile long totalDurationInUs = 0L;
    private volatile int currentProgress = 0;

    private void tryFetchTotalDuration() throws Exception {
        long totalDuration = 0L;
        // calculate every file duration
        for (int i = 0; i < mConcatMp4FileList.size(); ++i) {
            ConcatMp4File oneFile = mConcatMp4FileList.get(i);
            if (oneFile.getClipDurationInUSec() > 0) {
                totalDuration += oneFile.getClipDurationInUSec();
            } else {
                // fetch use Extractor
                MediaExtractor videoExtractor = new MediaExtractor();
                videoExtractor.setDataSource(oneFile.getMp4FilePath());
                int index = 0;
                for (; index < videoExtractor.getTrackCount(); ++index) {
                    MediaFormat format = videoExtractor.getTrackFormat(index);
                    if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                        long thisDuration = format.getLong(MediaFormat.KEY_DURATION);
                        totalDuration += thisDuration;
                        break;
                    }
                }
                if (index == videoExtractor.getTrackCount()) {
                    throw new IllegalArgumentException("Input:" + oneFile.getMp4FilePath() + " has no video track");
                }
            }
        }
        totalDurationInUs = totalDuration;
    }

    private void computeProgress(long currentPts) {
        int tmpProgress = (int) (currentPts * 100 / totalDurationInUs);
        if (tmpProgress > currentProgress) {
            currentProgress = tmpProgress;
            if (currentProgress >= 100) {
                currentProgress = 100;
            }
            if (mProcessStateListener != null) {
                mProcessStateListener.onProgress(currentProgress);
            }

        }
    }

    private void reStartNewDevice() throws Exception {
        if (mMediaDecoderDevice != null) {
            mMediaDecoderDevice.stopDecoder();
            mMediaDecoderDevice.release();
            mMediaDecoderDevice = null;
        }
        ConcatMp4File oneFile = mConcatMp4FileList.get(mCurrentIndex);
        mMediaDecoderDevice = new MediaDecoderDevice(oneFile.getMp4FilePath());
        mMediaDecoderDevice.setup();
        mMediaDecoderDevice.configClip(oneFile.getClipStartInUSec(), oneFile.getClipDurationInUSec());
        mMediaDecoderDevice.setOnDecodeStateChangeListener(mDecodeDeviceStateListener);
        // if not sync, video frame will lose a lot (maybe from 20 to 6)
        // --> we have deal it by wait filter-consume time in VideoProcessSession
        mMediaDecoderDevice.setIsSyncWithSystemTime(false);

        mMediaDecoderDevice.setExtractAudioEnabled(mIsDecodeAudio);
        mMediaDecoderDevice.setExtractVideoEnabled(mIsDecodeVideo);

        // audio output listener
        mMediaDecoderDevice.setOnAudioDeviceFrameUpdateListener(mDeviceAudioFrameUpdateListener);

        // video output listener
        mMediaDecoderDevice.setOnDeviceVideoSizeChangedListener(mVideoConcatSession
                .getOnDeviceVideoSizeChangedListener());
        mMediaDecoderDevice.setVideoOutputSurface(mVideoConcatSession.getInputSurface());
        mMediaDecoderDevice.setOnVideoDeviceFrameUpdateListener(mDeviceVideoFrameUpdateListener);
        mMediaDecoderDevice.startDecoder();
    }

    private volatile long lastVideoPts = 0L;
    private volatile long lastAudioPts = 0L;
    private volatile long lastEpoch = 0L;

    private OnDeviceFrameUpdateListener mDeviceVideoFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            // change pts here
            bufferInfo.presentationTimeUs = lastEpoch + bufferInfo.presentationTimeUs;
            if (bufferInfo.presentationTimeUs > lastVideoPts) {
                lastVideoPts = bufferInfo.presentationTimeUs;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    && mCurrentIndex < mConcatMp4FileList.size() - 1) {
                Log.d(TAG, "onDeviceFrameUpdateSoon<video> end of stream;mCurrentIndex=" + mCurrentIndex);
                // only end for one;

            } else {
                return mVideoConcatSession.getOnVideoFrameUpdateListener()
                        .onDeviceFrameUpdateSoon(bufferData, bufferInfo);
            }
            return 0;
        }
    };

    private OnDeviceFrameUpdateListener mDeviceAudioFrameUpdateListener = new OnDeviceFrameUpdateListener() {
        @Override
        public int onDeviceFrameUpdateSoon(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
            // change pts here
            bufferInfo.presentationTimeUs = lastEpoch + bufferInfo.presentationTimeUs;
            if (bufferInfo.presentationTimeUs > lastAudioPts) {
                lastAudioPts = bufferInfo.presentationTimeUs;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    && mCurrentIndex < mConcatMp4FileList.size() - 1) {
                Log.d(TAG, "onDeviceFrameUpdateSoon<audio> end of stream;mCurrentIndex=" + mCurrentIndex);
                // only end for one;
            } else {
                return mAudioConcatSession.getOnAudioFrameUpdateListener()
                        .onDeviceFrameUpdateSoon(bufferData, bufferInfo);
            }
            return 0;
        }
    };


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

            mVideoConcatSession.stopEncoder();
            mAudioConcatSession.stopEncoder();

//        stopMp4Muxer();
            muxerAudioTrack = -1;
            muxerVideoTrack = -1;

            if (mMediaMuxer != null) {
                mMediaMuxer.stop();
                mMediaMuxer.release();
            }

            super.destroyHandler();
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));

        }

        resetTrackValue();

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
                sendMessageToHandlerThread(msg);
            } else {
                // send
                Message msg = new Message();
                msg.what = MSG_SELFDEFINED_ONESRC_DONE;
                sendMessageToHandlerThread(msg);
            }
        }

        @Override
        public void onProgress(int progress, long currentPTSInUs) {
        }

        @Override
        public void onDurationUpdated(int durationInMilliSec) {
            Log.d(TAG, "duration=" + durationInMilliSec);
        }
    };


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
            Log.d(TAG, "audioTrackId = " + audioTrackId);
            // MediaMuxer need start first, then writeSampleData
            checkMuxerTrackUpdated(audioTrackId, true);
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
//        Log.d(TAG, "where am i");
        // muxer start first
        mMediaMuxer.start();
//        startMp4Muxer();
        if (mIsDecodeAudio) {
            mMp4AudioTrack = muxerAudioTrack;
        }
        if (mIsDecodeVideo) {
            mMp4VideoTrack = muxerVideoTrack;
        }

        synchronized (mFormatAllLock) {
            mFormatAllLockNotified = true;
            mFormatAllLock.notify();
        }

    }

    private void resetTrackValue() {
        muxerAudioTrack = -1;
        muxerVideoTrack = -1;
        mMp4VideoTrack = -1;
        mMp4AudioTrack = -1;
        mFormatUpdateLockNotified = false;
        mFormatAllLockNotified = false;

        latestVideoPts = 0L;
        latestAudioPts = 0L;
    }

    private volatile long latestVideoPts = 0L;
    private volatile long latestAudioPts = 0L;

    private volatile int mMp4VideoTrack = -1;
    private volatile int mMp4AudioTrack = -1;


    private OnEncodedFrameUpdateListener mOnEncodedVideoFrameUpdateListener = new OnEncodedFrameUpdateListener() {

        @Override
        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
//            Log.d(TAG, "onEncodedVideoFrameUpdate bufferInfo.pts = ["
//                    + bufferInfo.presentationTimeUs
//                    + "];bufferInfo.flags=" + bufferInfo.flags);

            if (mMediaMuxer != null && mMp4VideoTrack >= 0) {
                try {
//                    Log.d(TAG, "mediamuxer write video sample size=" + bufferInfo.size + ";pts=" + bufferInfo.presentationTimeUs);
                    long pts = bufferInfo.presentationTimeUs;

                    if (pts == 0) { // first encode: audio config or sps, pts is 0
                        if (bufferInfo.size < 100) {
                            // pts is 0 and is sps,pps; we already have this in output format
                            return;
                        }
                    }
                    if (pts < latestVideoPts) {
                        bufferInfo.presentationTimeUs = latestVideoPts + 1;
                    }

                    latestVideoPts = bufferInfo.presentationTimeUs;

                    computeProgress(latestVideoPts);

                    ByteBuffer record = bufferData.duplicate();
                    mMediaMuxer.writeSampleData(mMp4VideoTrack, record, bufferInfo);
                } catch (Exception e) {
                    Log.e(TAG, "mediamuxer write video sample failed.");
                    e.printStackTrace();
                }
            }
        }
    };

    private OnEncodedFrameUpdateListener mOnEncodedAudioFrameUpdateListener = new OnEncodedFrameUpdateListener() {

        @Override
        public void onEncodedFrameUpdate(ByteBuffer bufferData, MediaCodec.BufferInfo bufferInfo) {
//            Log.d(TAG, "onEncodedFrameUpdate pts=" + bufferInfo.presentationTimeUs + ";size=" + bufferInfo.size);

            if (mMediaMuxer != null && mMp4AudioTrack >= 0) {
                try {
                    long pts = bufferInfo.presentationTimeUs;

                    if (pts == 0) { // first encode: audio config or sps, pts is 0
                        if (bufferInfo.size < 10) {
                            // pts is 0 and is audio-config info; we already have this in output format
                            return;
                        }
                    }

                    if (pts < latestAudioPts) {
                        bufferInfo.presentationTimeUs = latestAudioPts + 1;
                    }

                    latestAudioPts = bufferInfo.presentationTimeUs;

                    ByteBuffer record = bufferData.duplicate();
                    mMediaMuxer.writeSampleData(mMp4AudioTrack, record, bufferInfo);
                } catch (Exception e) {
                    Log.e(TAG, "mediamuxer write audio sample failed.");
                    e.printStackTrace();
                }
            }
        }
    };

//    public void configBackgroundMusic(boolean enableBGM, String bgmPath, boolean isLooping) {
//        mAudioConcatSession.configBackgroundMusic(enableBGM, bgmPath, isLooping);
//    }
//
//    public void configBackgroundMusicClip(long clipStartInUSec, long clipDurationInUSec) {
//        mAudioConcatSession.configBackgroundMusicClip(clipStartInUSec, clipDurationInUSec);
//    }

    /**
     * 设置背景音乐音量的大小，默认是 1f
     *
     * @param gain 范围在 0f 到 1f 之间
     */
//    public void setBGMTrackGain(float gain) {
//        mAudioConcatSession.setBGMTrackGain(gain);
//    }

//    public void setMasterTrackGain(float mainGain) {
//        if (mAudioConcatSession != null) {
//            mAudioConcatSession.setMasterTrackGain(mainGain);
//        }
//    }

//    private long mClipStartPositionInUSec = -1;
//    private long mClipDurationInUSec = -1;
//
//    public void configMediaFileClip(long clipStartInUSec, long clipDurationInUSec) {
//        mClipStartPositionInUSec = clipStartInUSec;
//        mClipDurationInUSec = clipDurationInUSec;
//    }


    String mLocalMp4Path;
    boolean mSaveMp4 = false;

    public void configMp4Saver(boolean saveMp4, String localMp4Path) {
        mLocalMp4Path = localMp4Path;
        mSaveMp4 = saveMp4;
    }

    /**
     * 设置滤镜列表
     * @param filters
     */
    public void setGPUImageFilters(List<GPUImageFilter> filters) {
        mVideoConcatSession.setGPUImageFilters(filters);
    }

    @Override
    protected void handleMessageInHandlerThread(Message msg) {
        if (isStopped) {
            Log.d(TAG, "stopped already, msg.what=" + msg.what + " is not delivered");
            return;
        }
        switch (msg.what) {
            case Constraints.MSG_SUCCESS:
                // notif sucess
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
            case MSG_SELFDEFINED_ONESRC_DONE:
                // deal with restart device
                if (mCurrentIndex < mConcatMp4FileList.size() - 1) {
                    mCurrentIndex++;
                    lastEpoch = lastVideoPts > lastAudioPts ? lastVideoPts : lastAudioPts;
                    Log.d(TAG, "will startNewDevice, updated lastEpoch=" + lastEpoch
                    + "; lastVideoPts=" + lastVideoPts + ";lastAudioPts=" + lastAudioPts);
                    // start a new device
                    try {
                        reStartNewDevice();
                    } catch (Exception e) {
                        Log.d(TAG, Log.getStackTraceString(e));
                        stop();
                        if (mProcessStateListener != null) {
                            mProcessStateListener.onFinish(false, msg.arg1);
                        }
                    }

                } else {
                    Log.d(TAG, "last concat part decode over;wait encoder to finish");
                }
                break;
            default:
                break;
        }
    }

    public static class ConcatMp4File {
        private String mp4FilePath;
        private long clipStartInUSec = -1L;
        private long clipDurationInUSec = -1L;

        public ConcatMp4File(String localPath) {
            this(localPath, -1L, -1L);
        }

        public ConcatMp4File(String localPath, long startInUSec, long durationInUSec) {
            mp4FilePath = localPath;
            clipStartInUSec = startInUSec;
            clipDurationInUSec = durationInUSec;
        }

        public String getMp4FilePath() {
            return mp4FilePath;
        }

        public long getClipStartInUSec() {
            return clipStartInUSec;
        }

        public long getClipDurationInUSec() {
            return clipDurationInUSec;
        }
    }

}
