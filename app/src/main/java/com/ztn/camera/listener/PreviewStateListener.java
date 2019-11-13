package com.ztn.camera.listener;


public interface PreviewStateListener {
    /**
     * percent of total
     *
     * @param progress       11 means 11%
     * @param currentPTSInUs
     */
    void onProgress(int progress, long currentPTSInUs);

    void onSizeChanged(int videoWidth, int videoHeight, int videoOrientation);

    /**
     * finish of process
     *
     * @param isSuccess true is success; false is failed
     */
    void onFinish(boolean isSuccess, int what);

    void onDuration(int durationInMilliSec);

    /**
     * start command is done
     */
    void onStarted();

    /**
     * stop command is done
     */
    void onStopped();

    /**
     * pauseBGM command is done
     */
    void onPaused();

    /**
     * resumeBGM command is done
     */
    void onResumed();

    /**
     * release command is done
     */
    void onReleased();
}
