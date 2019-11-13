package com.ztn.camera.listener;

/**
 * 录像信息通知
 */
public interface SessionInfoListener {
   
    /**
     * 码率上调：
     * 开启动态码率后，视频码率会根据网络状态进行调整
     */
    public static final int INFO_QOS_BITRATE_INCREASED = 1;
    
    /**
     * 码率下调：
     * 开启动态码率后，视频码率会根据网络状态进行调整
     */
    public static final int INFO_QOS_BITRATE_DECREASED = 2;
    
    /**
     * 信息回调接口
     * @param what 信息类型
     * @param extra 信息附加值
     */
    void onInfo(int what, int extra);

}
