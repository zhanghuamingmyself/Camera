package com.ztn.camera.session;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

/**
 * HandlerThread for session that needs to start or stop device/filter/encoder in standalone thread
 * Created by baidu on 2017/4/19.
 */

public abstract class HandlerThreadSession {
    private HandlerThread mHandlerThread = null;
    private Handler mOwnThreadHandler = null;

    protected synchronized void setupHandler() {
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("" + this.getClass().getSimpleName());
            mHandlerThread.start();
            Looper looper = mHandlerThread.getLooper();
            mOwnThreadHandler = new MyHandler(looper);
        }
    }

    protected synchronized void destroyHandler() {
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    protected Message obtainMessage(int what) {
        Message msg = mOwnThreadHandler.obtainMessage(what);
        return msg;
    }

    protected boolean hasMessages(int what) {
        return mOwnThreadHandler.hasMessages(what);
    }

    protected void removeMessages(int what) {
        mOwnThreadHandler.removeMessages(what);
    }

    protected void sendMessageToHandlerThread(Message msg) {
        mOwnThreadHandler.sendMessage(msg);
    }

    // handler created
    class MyHandler extends Handler {

        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            handleMessageInHandlerThread(msg);
        }
    }

    protected abstract void handleMessageInHandlerThread(Message msg);
}
