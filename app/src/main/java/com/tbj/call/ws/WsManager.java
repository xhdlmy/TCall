package com.tbj.call.ws;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.tbj.call.App;
import com.tbj.call.NetworkUtils;
import com.tbj.call.R;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 重连机制：
 1 服务端执行WebSocket的onclose方法，检测到onclose，表明连接断开，立即重新连接；
 2 不触发服务端执行WebSocket的onclose方法，比如切换网络，4G or Wifi，或者网络断开等因素，在检测到网络变化后进行重连；
 3 不触发服务端执行WebSocket的onclose方法，未知的原因导致，那么需要每隔一段时间发送心跳消息去判断是否保持着连接；

 多进程、多线程问题
 */

public class WsManager implements IWsManager {

    private final String TAG = WsManager.class.getSimpleName();

    private Context mContext;
    private String mWebSocketUrl;
    private OkHttpClient mOkHttpClient;
    private Request mRequest;
    private WebSocketListener mWebSocketListener;
    private WebSocket mWebSocket;

    private int mWsStatus = WsStatus.DISCONNECTED;
    private WsStatusListener mWsStatusListener;

    private OnNetworkStateChangedListener mNetworkListener;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public WsManager(WsManager.Builder builder) throws Exception {
        mContext = builder.mContext;
        mWebSocketUrl = builder.mWebSocketUrl;
        mOkHttpClient = builder.mOkHttpClient;
        mWsStatusListener = builder.mWsStatusListener;
        if(mContext == null
                || TextUtils.isEmpty(mWebSocketUrl)  || !(mWebSocketUrl.startsWith("ws://") || mWebSocketUrl.startsWith("wss://"))
                || mOkHttpClient == null
                || mWsStatusListener == null) {
            throw new Exception(App.sContext.getString(R.string.error_websocket_config));
        }

        App.getWsMap().put(mWebSocketUrl, this);

        mRequest = new Request.Builder().url(mWebSocketUrl).build();

        mWebSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, final Response response) {
                mWebSocket = webSocket;
                setCurrentStatus(WsStatus.CONNECTED);
                runOnUIThread(() -> mWsStatusListener.onOpen(response));
            }

            @Override
            public void onMessage(WebSocket webSocket, final String text) {
                runOnUIThread(() -> mWsStatusListener.onMessage(text));
            }

            @Override
            public void onMessage(WebSocket webSocket, final ByteString bytes) {
                runOnUIThread(() -> mWsStatusListener.onMessage(bytes));
            }

            @Override
            public void onClosing(WebSocket webSocket, final int code, final String reason) {
                runOnUIThread(() -> mWsStatusListener.onClosing(code, reason));
            }

            @Override
            public void onClosed(WebSocket webSocket, final int code, final String reason) {
                setCurrentStatus(WsStatus.DISCONNECTED);
                reconnect();
                runOnUIThread(() -> mWsStatusListener.onClosed(code, reason));
            }

            @Override
            public void onFailure(WebSocket webSocket, final Throwable t, final Response response) {
                setCurrentStatus(WsStatus.DISCONNECTED);
                reconnect();
                runOnUIThread(() -> mWsStatusListener.onFailure(t, response));
            }
        };

        mNetworkListener = type -> {
            setCurrentStatus(WsStatus.DISCONNECTED);
            switch (type) {
                case NetworkUtils.TYPE_MOBILE:
                case NetworkUtils.TYPE_WIFI:
                    reconnect();
                    break;
                case NetworkUtils.NO_NET:
                    Toast.makeText(mContext, R.string.error_no_net, Toast.LENGTH_SHORT).show();
                    break;
            }
        };
    }

    private synchronized void newWebSocket() {
        // 释放之前的连接(并重新连接)
        mOkHttpClient.dispatcher().cancelAll();
        // 建立该长连接通道的线程就一直在OkHttp的线程池里不会被释放掉，直到网络变化或者 onClosed 等。
        mWebSocket = mOkHttpClient.newWebSocket(mRequest, mWebSocketListener);
    }

    private void connect() {

    }

    private void reconnect() {
        switch (getCurrentStatus()) {
            case WsStatus.DISCONNECTED:
            case WsStatus.RECONNECT:
                newWebSocket();
                break;
            case WsStatus.CONNECTING:
                Log.i(TAG, "connecting...");
                break;
            case WsStatus.CONNECTED:
                Log.i(TAG, "conected!");
                break;
        }
    }

    private void cancelReconnect() {

    }

    private void runOnUIThread(OnUIThreadListener todo){
        if(Looper.myLooper() != Looper.getMainLooper()){
            mHandler.post(todo::onUIThread);
        }else{
            todo.onUIThread();
        }
    }

    interface OnUIThreadListener {
        void onUIThread();
    }

    public OnNetworkStateChangedListener getNetworkListener(){
        return mNetworkListener;
    }

    public interface OnNetworkStateChangedListener {
        void onNetStateChanged(String type);
    }

    @Override
    public WebSocket getWebSocket() {
        return mWebSocket;
    }

    @Override
    public void startConnect() {

    }

    @Override
    public void stopConnect() {

    }

    @Override
    public boolean isConnected() {
        return mWsStatus == WsStatus.CONNECTED;
    }

    @Override
    public int getCurrentStatus() {
        return mWsStatus;
    }

    @Override
    public void setCurrentStatus(int currentStatus) {
        this.mWsStatus = currentStatus;
    }

    @Override
    public boolean sendMessage(String msg) {
        return false;
    }

    @Override
    public boolean sendMessage(ByteString byteString) {
        return false;
    }

    public static final class Builder {

        private Context mContext;
        private String mWebSocketUrl;
        private OkHttpClient mOkHttpClient;
        private WsStatusListener mWsStatusListener;

        public Builder(@NonNull Context context) {
            mContext = context;
        }

        public WsManager.Builder url(@NonNull String webSocketUrl) {
            mWebSocketUrl = webSocketUrl;
            return this;
        }

        public WsManager.Builder client(@NonNull OkHttpClient client) {
            mOkHttpClient = client;
            return this;
        }

        public WsManager.Builder listener(@NonNull WsStatusListener listener) {
            mWsStatusListener = listener;
            return this;
        }

        public WsManager build() throws Exception {
            return new WsManager(this);
        }
    }

}
