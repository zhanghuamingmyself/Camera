package com.ztn.camera.session;

/**
 * Created by baidu on 2017/4/19.
 */

public class Constraints {

    public static final int MSG_FAILED = 1;
    public static final int MSG_FAILED_ARG1_REASON_DEVICE = 11;
    public static final int MSG_FAILED_ARG1_REASON_BGM_DEVICE = 111;
    public static final int MSG_FAILED_ARG1_REASON_FILTER = 12;
    public static final int MSG_FAILED_ARG1_REASON_ENCODER = 13;
    public static final int MSG_FAILED_ARG1_REASON_MUXER = 14;

    public static final int MSG_FAILED_ARG1_REASON_CAMERA = 15;
    public static final int MSG_FAILED_ARG1_REASON_AUDIO_MIC = 16;

    public static final int MSG_SUCCESS = 2;

    public static final int MSG_BGM_FINISHED = 3;

    // use 4N to represent action command
    public static final int MSG_TO_START = 41;
    public static final int MSG_TO_PAUSE = 42;
    public static final int MSG_TO_RESUME = 43;
    public static final int MSG_TO_STOP = 44;
    public static final int MSG_TO_RELEASE = 45;
}
