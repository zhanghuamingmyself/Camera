package com.ztn.camera.listener;

/**
 * 录像回调监听
 */
public interface SessionStateListener {
    /**
     * prepareSessionAsync、startRtmpSession、stopRtmpSession等接口执行成功后回调参数，
     * 收到此回调参数则表明session已经处于prepared、started或者stopped状态
     */
    public static final int RESULT_CODE_OF_OPERATION_SUCCEEDED = 0;
    /**
     * prepareSessionAsync操作失败后，onSessionError回调接口的错误码参数，
     * 收到此回调参数则表明session已经处于错误状态，建议此时回收session相关资源
     */
    public static final int ERROR_CODE_OF_PREPARE_SESSION_FAILED = -1;
    /**
     * startRtmpSession过程中连接服务器出错后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议提示用户检查网络环境以及推流地址，稍后再调用startRtmpSession接口
     */
    public static final int ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED = -2;
    /**
     * stopRtmpSession过程中出错后，onSessionError回调接口的错误码参数，此时session已经停止推流，
     * 收到此回调参数后，不可再调用startRtmpSession接口，建议销毁session再次创建并重新调用prepareSeesionAsync
     */
    public static final int ERROR_CODE_OF_DISCONNECT_FROM_SERVER_FAILED = -3;
    /**
     * prepareRtmpSession过程中打开MIC设备出错后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议提示用户允许应用使用MIC权限。
     * 注意：收到此回调参数后，还会收到带有ERROR_CODE_OF_PREPARE_SESSION_FAILED参数的
     * onSessionError回调
     */
    public static final int ERROR_CODE_OF_OPEN_MIC_FAILED = -4;
    /**
     * prepareRtmpSession过程中打开相机设备出错后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议提示用户允许应用使用相机权限。
     * 注意：收到此回调参数后，还会收到带有ERROR_CODE_OF_PREPARE_SESSION_FAILED参数的
     * onSessionError回调
     */
    public static final int ERROR_CODE_OF_OPEN_CAMERA_FAILED = -5;
    /**
     * 推流过程中，遇到未知错误导致推流失败后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议调用stopRtmpSession立即停止推流
     */
    public static final int ERROR_CODE_OF_UNKNOWN_STREAMING_ERROR = -501;
    /**
     * 推流过程中，遇到弱网情况导致推流失败后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议提示用户当前网络不稳定，
     * 如果反复收到此错误码，建议调用stopRtmpSession停止推流
     */
    public static final int ERROR_CODE_OF_WEAK_CONNECTION_ERROR = -502;
    /**
     * 推流过程中，遇到服务器内部错误导致推流失败后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议调用stopRtmpSession立即停止推流，并在服务恢复后再重新推流
     */
    public static final int ERROR_CODE_OF_SERVER_INTERNAL_ERROR = -503;
    /**
     * 推流过程中，遇到设备断网导致推流失败后，onSessionError回调接口的错误码参数，
     * 收到此回调参数后，建议提示用户检查网络连接，然后调用stopRtmpSession立即停止推流
     */
    public static final int ERROR_CODE_OF_LOCAL_NETWORK_ERROR = -504;
    
    /**
     * 录制设备准备完毕
     * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
     */
    void onSessionPrepared(int code);
    
    /**
     * 推流开始后的回调
     * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
     */
    void onSessionStarted(int code);
    
    /**
     * 推流结束后的回调
     * @param code 固定为RESULT_CODE_OF_OPERATION_SUCCEEDED
     */
    void onSessionStopped(int code);
    
    /**
     * 推流SDK出错后的回调
     * @param code 错误类型如下：
     *                ERROR_CODE_OF_OPEN_MIC_FAILED
     *                ERROR_CODE_OF_OPEN_CAMERA_FAILED
     *                ERROR_CODE_OF_PREPARE_SESSION_FAILED
     *                ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED
     *                ERROR_CODE_OF_DISCONNECT_FROM_SERVER_FAILED
     *                ERROR_CODE_OF_UNKNOWN_STREAMING_ERROR
     *                ERROR_CODE_OF_WEAK_CONNECTION_ERROR
     *                ERROR_CODE_OF_SERVER_INTERNAL_ERROR
     *                ERROR_CODE_OF_LOCAL_NETWORK_ERROR
     *                
     */
    void onSessionError(int code);

    /**
     * 人脸道具加载成功事件的回调
     */
    void onFaceStickerLoaded();
    
    /**
     * 伴奏音乐播放完成事件的回调
     */
    void onMusicPlaybackCompleted();
}
