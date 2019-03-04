package com.tbj.call;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;


public class NetworkUtils {

    private static Context sContext = App.sContext;

    public static NetworkInfo getNetworkInfo(){
        // LeakCanary 检测到不要用 Activity 作为 Context，否则 ConnectivityManager.mContext 会引发内存泄漏
        ConnectivityManager connectivityManager = (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager == null) return null;
        return connectivityManager.getActiveNetworkInfo();
    }

    public static boolean isNetworkConnected() {
        NetworkInfo networkInfo = getNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable();
    }

    public static final String TYPE_WIFI = "WIFI";
    public static final String TYPE_MOBILE = "MOBILE";
    public static final String TYPE_UNKNOW = "UNKNOW";
    public static final String NO_NET = "NO_NET";

    public static String getNetworkType() {
        NetworkInfo networkInfo = NetworkUtils.getNetworkInfo();
        if (networkInfo != null && networkInfo.isAvailable()) {
            String type = networkInfo.getTypeName();
            if (type.equalsIgnoreCase(TYPE_WIFI)) {
                return TYPE_WIFI;
            } else if (type.equalsIgnoreCase(TYPE_MOBILE)) {
                return TYPE_MOBILE;
            } else {
                return TYPE_UNKNOW;
            }
        } else {
            return NO_NET;
        }
    }

}
