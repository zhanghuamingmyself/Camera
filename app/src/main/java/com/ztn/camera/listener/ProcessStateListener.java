package com.ztn.camera.listener;

public interface ProcessStateListener {
    /**
     * percent of total
     *
     * @param progress 11 means 11%
     */
    void onProgress(int progress);

    /**
     * finish of process
     *
     * @param isSuccess true is success; false is failed
     */
    void onFinish(boolean isSuccess, int what);
}