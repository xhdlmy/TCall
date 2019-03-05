package com.tbj.call;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;

import com.tbj.call.ws.WsManager;
import com.tbj.call.ws.WsStatusListener;

import java.util.Random;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;

/**
 * Created by computer on 2019/3/5.
 */

public class MockService extends Service {

    private static final String TAG = "MockWebSocket";

    private MockWebServer mMockWebServer;
    private WebSocket mWebSocket;
    private String mWebSocketUrl = "";

    private MockBinder mMockBinder = new MockBinder();

    private int mReSendCount;

    private WsManager mWsManager;

    private Handler mHandler = new Handler(Looper.myLooper());
    private Runnable mRunnable;

    private void clientConnection(){
        if(mWsManager == null) {
            OkHttpClient client = new OkHttpClient.Builder()
//                    .retryOnConnectionFailure(true)
                    .build();
            WsStatusListener listener = new WsStatusListener() {};
            mWsManager = new WsManager.Builder(this)
                    .url(mWebSocketUrl)
                    .client(client)
                    .listener(listener)
                    .build();
        }
        mWsManager.startConnect();
    }

    private void startService(){
        Executors.newSingleThreadExecutor().execute(() -> {
            if(mMockWebServer == null){
                mMockWebServer = new MockWebServer();
                String hostName = mMockWebServer.getHostName();
                int port = mMockWebServer.getPort();
                String url = "ws://" + hostName + ":" + port + "/";
                Log.i(TAG, "mock websocket url:" + url);
                setWebSocketUrl(url);
            }
            Log.i(TAG, "newWebSocket");
            clientConnection();
            mMockWebServer.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.i(TAG, "server onOpen");
                    mWebSocket = webSocket;
                    sendMsg();
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    Log.i(TAG, "server onClosing code:" + code + " reason:" + reason);
                    mHandler.removeCallbacks(mRunnable);

                    mReSendCount += 10;
                    sendMsg(mReSendCount);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    Log.i(TAG, "server onClosed code:" + code + " reason:" + reason);
                }
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.i(TAG, "server onFailure response:" + response);
                }
            }));
        });
    }

    private void sendMsg() {
        sendMsg(mReSendCount);
    }

    private void sendMsg(int min) {
        int max = min + 10;
        // 服务端间隔发送消息
        mRunnable = new Runnable() {
            @Override
            public void run() {
                Random random = new Random();
                int integer = random.nextInt(max)%(max-min+1) + min;
                Log.i(TAG, "service send msg " + integer);
                mWebSocket.send("" + integer);
                mHandler.postDelayed(this, 2000);
            }
        };
        if(mWebSocket != null){
            mHandler.post(mRunnable);
        }
    }

    private void stopService(){
        mHandler.removeCallbacks(mRunnable);
        Log.i(TAG, "service close webSocket");
        mWebSocket.close(1000, "Service want close this connection!");
//        mWebSocket.cancel();
    }

    private void setWebSocketUrl(String url){
        mWebSocketUrl = url;
    }

    public String getWebSocketUrl(){
        return mWebSocketUrl;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMockBinder;
    }

    public interface MockInterface {
        void startService();
        void stopService();
        String getWebSocketUrl();
    }

    class MockBinder extends Binder implements MockInterface {

        private MockService mMockService = MockService.this;

        @Override
        public void startService() {
            mMockService.startService();
        }

        @Override
        public void stopService() {
            mMockService.stopService();
        }

        @Override
        public String getWebSocketUrl() {
            return mMockService.getWebSocketUrl();
        }
    }
}
