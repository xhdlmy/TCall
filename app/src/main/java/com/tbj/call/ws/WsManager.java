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

 TODO 如果连接在客户端非正常关闭了，那么会不会造成服务端发送的数据的丢失？服务端发送数据之前会不会检查心跳是否存在？服务端检查心跳不存在后，想要连客户端一直连不上，所以客户端需要主动在连接关闭的时候，或者检测心跳包没有的时候，主动打开WebSocket连接


 多进程、多线程问题
 */

public class WsManager implements IWsManager {

    private final String TAG = "MockWebSocket";

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

    public WsManager(WsManager.Builder builder) {
        mContext = builder.mContext;
        mWebSocketUrl = builder.mWebSocketUrl;
        mOkHttpClient = builder.mOkHttpClient;
        mWsStatusListener = builder.mWsStatusListener;
        if(mContext == null
                || TextUtils.isEmpty(mWebSocketUrl)  || !(mWebSocketUrl.startsWith("ws://") || mWebSocketUrl.startsWith("wss://"))
                || mOkHttpClient == null
                || mWsStatusListener == null) {
            Toast.makeText(App.sContext, App.sContext.getString(R.string.error_websocket_config), Toast.LENGTH_SHORT).show();
            return;
        }

        App.getWsMap().put(mWebSocketUrl, this);

        mRequest = new Request.Builder().url(mWebSocketUrl).build();

        mWebSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, final Response response) {
                Log.i(TAG, "client onOpen");
                mWebSocket = webSocket;
                setCurrentStatus(WsStatus.CONNECTED);
                runOnUIThread(() -> mWsStatusListener.onOpen(response));
            }

            @Override
            public void onMessage(WebSocket webSocket, final String text) {
                Log.i(TAG, "client onMessage " + text);
                int i = Integer.valueOf(text);
                if(0 == i%3){
                    Log.i(TAG, "client want reconnect");
                    setCurrentStatus(WsStatus.DISCONNECTED);
                    reconnect();
                }
                runOnUIThread(() -> mWsStatusListener.onMessage(text));
            }

            @Override
            public void onMessage(WebSocket webSocket, final ByteString bytes) {
                runOnUIThread(() -> mWsStatusListener.onMessage(bytes));
            }

            // 当服务端指示不再传输传入消息时调用。
            @Override
            public void onClosing(WebSocket webSocket, final int code, final String reason) {
                Log.i(TAG, "client onClosing code " + code + " msg " + reason);
                setCurrentStatus(WsStatus.DISCONNECTED);
                reconnect();
                runOnUIThread(() -> mWsStatusListener.onClosing(code, reason));
                // 客户端通知服务端可以完全关闭链接了 这样服务端也要重新启动么？
//                mWebSocket.close(code, reason);
            }

            // 当两个对等方都表示不再传输消息并且连接已成功释放时调用。
            @Override
            public void onClosed(WebSocket webSocket, final int code, final String reason) {
                Log.i(TAG, "client onClosed code " + code + " msg " + reason);
                // 服务器端发送的关闭，如果非正常关闭，那么会丢失数据吧
                // code == 1000，正常关闭，但在该项目下，应该不会服务器主动关闭
                setCurrentStatus(WsStatus.DISCONNECTED);
                reconnect();
                runOnUIThread(() -> mWsStatusListener.onClosed(code, reason));
            }

            // 由于读取或写入传出和传入消息的错误而关闭Web套接字时调用可能已丢失。
            @Override
            public void onFailure(WebSocket webSocket, final Throwable t, final Response response) {
                Log.i(TAG, "client onFailure throwable " + t.toString() + " response " + response);
                // 服务器端发送的错误
//                setCurrentStatus(WsStatus.DISCONNECTED);
//                reconnect();
//                runOnUIThread(() -> mWsStatusListener.onFailure(t, response));
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

    // TODO
    private synchronized void reconnect() {
        stopConnect();
        switch (getCurrentStatus()) {
            case WsStatus.DISCONNECTED:
                startConnect();
                break;
            case WsStatus.CONNECTED:

                break;
        }
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
        Log.i(TAG, "client newWebSocket");
        mOkHttpClient.newWebSocket(mRequest, mWebSocketListener);
    }

    @Override
    public void stopConnect() {
        // 在关闭之前，所有已经在队列中的消息将被传送完毕
        mWebSocket.close(1000, "client close");
        // 在关闭之前，所有已经在队列中的消息将被丢弃
//        mWebSocket.cancel();
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

        public WsManager build() {
            return new WsManager(this);
        }
    }

}
