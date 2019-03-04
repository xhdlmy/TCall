package com.tbj.call;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;

import com.tbj.call.ws.WsManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NetworkStateService extends Service {

    private static final String TAG = NetworkStateService.class.getSimpleName();
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 接收 ConnectivityManager.CONNECTIVITY_ACTION 的系统广播
            if(intent == null) return;
            String action = intent.getAction();
            if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) return;

            String networkType = NetworkUtils.getNetworkType();
            HashMap<String, WsManager> wsMap = App.getWsMap();
            Set<Map.Entry<String, WsManager>> entries = wsMap.entrySet();
            for (Map.Entry<String, WsManager> entry : entries) {
                WsManager wsManager = entry.getValue();
                WsManager.OnNetworkStateChangedListener networkListener = wsManager.getNetworkListener();
                networkListener.onNetStateChanged(networkType);
            }
        }
    };

    @Override
    public void onCreate() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, filter);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
