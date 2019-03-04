package com.tbj.call;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.tbj.call.ws.WsManager;

import java.util.HashMap;

/**
 * Created by computer on 2019/2/26.
 */

public class App extends Application {

    public static Context sContext;

    private static HashMap<String, WsManager> mWsMap = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;

        sContext.startService(new Intent(sContext, NetworkStateService.class));
    }

    public App getApplication(){
        return this;
    }

    public static HashMap<String, WsManager> getWsMap(){
        return mWsMap;
    }

}
