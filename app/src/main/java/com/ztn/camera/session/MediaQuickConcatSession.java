package com.ztn.camera.session;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

/**
 * 多Mp4快速合并
 * 调用方法：
 * MediaQuickConcatSession quickConcatSession = new MediaQuickConcatSession();
 * boolean success = quickConcatSession.concat(localFilePathArrayList,
 *                  "/sdcard/quickConcat" + System.currentTimeMillis() + ".mp4");
 *
 * 快速合并的前提：
 * 1、视频分辨率、编码Profile等完全一致；
 * 2、音频采样率、声道等完全一致；
 * 比如，多个视频均由使用同样编码参数配置的MediaProcessSession生成，可使用该类进行多文件合并。
 *
 * 如果以上条件不符合，请使用MediaConcatSession。
 *
 * 快速合并的原理：
 * 不进行音视频解码，仅将不同文件的压缩帧Frame修改PTS信息后，合并入同一个文件；
 */
public class MediaQuickConcatSession {
    private static final String TAG = "MediaQuickConcatSession";

    private static final boolean VERBOSE = false;

    /**
     * 最大帧大小，256k。编码后的一帧，一般都比这个值小很多。
     */
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;

    /**
     * 开始合并
     * @param srcList 待合并文件的列表.这些合并文件必须为同样编码格式、同样编码参数的mp4文件
     * @param destFilePath
     */
    public boolean concat(List<String> srcList, String destFilePath) {
        boolean concatSuccess = true;
        // do sth concat
        MediaMuxer muxer = null;
        try {

            if (srcList == null || srcList.size() < 2) {
                Log.e(TAG, "concat error! Source Files count is less that 2");
                return false;
            }

            if (destFilePath == null || destFilePath.equals("")) {
                Log.e(TAG, "concat error! Destination Saved File should be set a valid path");
                return false;
            }

            File file = new File(destFilePath);
            if (file.exists()) {
                file.delete();
            }
            muxer = new MediaMuxer(destFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            doMultiMuxer(muxer, srcList);
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
            // notif error
            concatSuccess = false;
        } finally {

            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception e) {
                Log.d(TAG, "when release muxer: " + e.getMessage());
            }

        }
        return concatSuccess;
    }

    /**
     * Using the MediaMuxer to concat media files.
     */
    private void doMultiMuxer(MediaMuxer muxer, List<String> srcMediaList)
            throws Exception {
        // Set up MediaExtractor to read from the source.
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcMediaList.get(0));
        int trackCount = extractor.getTrackCount();
        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        int degrees = 0;
        int videoTrackForMuxer = -1;
        int audioTrackForMuxer = -1;
        int width = -1;
        int height = -1;
        boolean hasAudioSelected = false;
        boolean hasVideoSelected = false;
        for (int i = 0; i < trackCount; i++) {

            MediaFormat format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                if (hasVideoSelected) {
                    continue;
                }
                hasVideoSelected = true;
                extractor.selectTrack(i);
                try {
                    degrees = format.getInteger(MediaFormat.KEY_ROTATION);
                } catch (Exception e) {
                    // some file may have no rotation property, it's ok, using default 0.
                }
                width = format.getInteger(MediaFormat.KEY_WIDTH);
                height = format.getInteger(MediaFormat.KEY_HEIGHT);
                videoTrackForMuxer = muxer.addTrack(format);
                indexMap.put(i, videoTrackForMuxer);
            } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                // 仅支持一个音频track
                if (hasAudioSelected) {
                    continue;
                }
                hasAudioSelected = true;
                extractor.selectTrack(i);
                audioTrackForMuxer = muxer.addTrack(format);
                indexMap.put(i, audioTrackForMuxer);
            }

        }
        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int frameCount = 0;
        int offset = 100;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if (degrees >= 0) {
            muxer.setOrientationHint(degrees);
        }
        muxer.start();
        int currentIndex = 0;
        long lastPts = 0L;
        long epochNow = 0L;
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                if (currentIndex < srcMediaList.size() - 1) {
                    currentIndex++;
                    epochNow = lastPts;
                    extractor.release();
                    // reuse a new src
                    extractor = new MediaExtractor();
                    extractor.setDataSource(srcMediaList.get(currentIndex));
                    trackCount = extractor.getTrackCount();
                    hasAudioSelected = false;
                    hasVideoSelected = false;
                    for (int i = 0; i < trackCount; i++) {
                        MediaFormat format = extractor.getTrackFormat(i);
                        if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                            // 仅支持一个视频track
                            if (hasVideoSelected) {
                                continue;
                            }
                            hasVideoSelected = true;
                            extractor.selectTrack(i);

                            int followingDegrees = 0;
                            try {
                                followingDegrees = format.getInteger(MediaFormat.KEY_ROTATION);
                            } catch (Exception e) {
                                // some file may have no rotation property, it's ok, using default 0.
                            }
                            if (followingDegrees != degrees) {
                                String errorNote = "InputError! rotation degrees of fileList is not the same";
                                Log.e(TAG, errorNote);
                                extractor.release();
                                throw new IllegalArgumentException(errorNote);
                            }
                            int followingWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                            int followingHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                            if (followingWidth != width || followingHeight != height) {
                                String errorNote = "InputError! width or height of fileList is not the same";
                                Log.e(TAG, errorNote);
                                extractor.release();
                                throw new IllegalArgumentException(errorNote);
                            }
                            indexMap.put(i, videoTrackForMuxer);
                        } else if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                            // 仅支持一个音频track
                            if (hasAudioSelected) {
                                continue;
                            }
                            hasAudioSelected = true;
                            extractor.selectTrack(i);
                            indexMap.put(i, audioTrackForMuxer);
                        }
                    }

                } else {
                    sawEOS = true;
                    bufferInfo.size = 0;
                }

            } else {
                bufferInfo.presentationTimeUs = epochNow + extractor.getSampleTime();
                lastPts = bufferInfo.presentationTimeUs;

                bufferInfo.flags = extractor.getSampleFlags();
//                Log.d(TAG, "flags = " + bufferInfo.flags + ";pts=" + bufferInfo.presentationTimeUs);
                int trackIndex = extractor.getSampleTrackIndex();
                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                        bufferInfo);
                extractor.advance();
                frameCount++;
            }
        }
        extractor.release();

        return;
    }
}
