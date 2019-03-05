package com.tbj.call;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.tbj.call.ws.WsManager;
import com.tbj.call.ws.WsStatusListener;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;

public class MainActivity extends AppCompatActivity {

    private String TAG = "MockWebSocket";

    private Context mContext;

    private ServiceConnection mConnection;
    private MockService.MockInterface mMockInterface;
    private WsManager mWsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_start_service).setOnClickListener(v -> bindMockService());
        findViewById(R.id.btn_stop_service).setOnClickListener(v -> {
            if(mMockInterface != null) mMockInterface.stopService();
        });
//        findViewById(R.id.btn_conn).setOnClickListener(v -> connection());
    }

    private void bindMockService() {
        if(mConnection == null){
            Intent intent = new Intent(mContext, MockService.class);
            mConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mMockInterface = (MockService.MockInterface) service;
                    mMockInterface.startService();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mMockInterface.stopService();
                    mConnection = null;
                }
            };
            mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }else{
            mMockInterface.startService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mContext.unbindService(mConnection);
        mConnection = null;
    }

}
