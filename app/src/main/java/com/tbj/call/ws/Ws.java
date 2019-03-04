package com.tbj.call.ws;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class Ws implements IWsManager {

  final static int NORMAL_CLOSE = 1000;
  final static int ABNORMAL_CLOSE = 1001;

  final static String MSG_NORMAL_CLOSE = "normal close";
  final static String MSG_ABNORMAL_CLOSE = "abnormal close";

  private final static int RECONNECT_INTERVAL = 10 * 1000;    //重连自增步长
  private final static long RECONNECT_MAX_TIME = 120 * 1000;   //最大重连间隔
  
  private Context mContext;
  private String mWsUrl;
  private WebSocket mWebSocket;
  private OkHttpClient mOkHttpClient;
  private Request mRequest;
  
  private int mCurrentStatus = WsStatus.DISCONNECTED;     
  private boolean mIsNeedReconnect;
  private int mReconnectCount;
  private boolean mIsManualClose;         
  private WsStatusListener mWsStatusListener;
  
  private Handler mHandler = new Handler(Looper.getMainLooper());
  private Lock mLock;

  private Runnable reconnectRunnable = new Runnable() {
    @Override
    public void run() {
      if (mWsStatusListener != null) {
        mWsStatusListener.onReconnect();
      }
      buildConnect();
    }
  };

  private WebSocketListener mWebSocketListener = new WebSocketListener() {

    @Override
    public void onOpen(WebSocket webSocket, final Response response) {
      mWebSocket = webSocket;
      setCurrentStatus(WsStatus.CONNECTED);
      connected();
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onOpen(response);
            }
          });
        } else {
          mWsStatusListener.onOpen(response);
        }
      }
    }

    @Override
    public void onMessage(WebSocket webSocket, final ByteString bytes) {
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onMessage(bytes);
            }
          });
        } else {
          mWsStatusListener.onMessage(bytes);
        }
      }
    }

    @Override
    public void onMessage(WebSocket webSocket, final String text) {
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onMessage(text);
            }
          });
        } else {
          mWsStatusListener.onMessage(text);
        }
      }
    }

    @Override
    public void onClosing(WebSocket webSocket, final int code, final String reason) {
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onClosing(code, reason);
            }
          });
        } else {
          mWsStatusListener.onClosing(code, reason);
        }
      }
    }

    @Override
    public void onClosed(WebSocket webSocket, final int code, final String reason) {
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onClosed(code, reason);
            }
          });
        } else {
          mWsStatusListener.onClosed(code, reason);
        }
      }
    }

    @Override
    public void onFailure(WebSocket webSocket, final Throwable t, final Response response) {
      tryReconnect();
      if (mWsStatusListener != null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
          mHandler.post(new Runnable() {
            @Override
            public void run() {
              mWsStatusListener.onFailure(t, response);
            }
          });
        } else {
          mWsStatusListener.onFailure(t, response);
        }
      }
    }
  };

  public Ws(Builder builder) {
    mContext = builder.mContext;
    mWsUrl = builder.mWsUrl;
    mIsNeedReconnect = builder.needReconnect;
    mOkHttpClient = builder.mOkHttpClient;
    this.mLock = new ReentrantLock();
  }

  private void initWebSocket() {
    if (mOkHttpClient == null) {
      mOkHttpClient = new OkHttpClient.Builder()
          .retryOnConnectionFailure(true)
          .build();
    }
    if (mRequest == null) {
      mRequest = new Request.Builder()
          .url(mWsUrl)
          .build();
    }
    mOkHttpClient.dispatcher().cancelAll();
    try {
      mLock.lockInterruptibly();
      try {
        mOkHttpClient.newWebSocket(mRequest, mWebSocketListener);
      } finally {
        mLock.unlock();
      }
    } catch (InterruptedException e) {
    }
  }

  @Override
  public WebSocket getWebSocket() {
    return mWebSocket;
  }

  public void setWsStatusListener(WsStatusListener mWsStatusListener) {
    this.mWsStatusListener = mWsStatusListener;
  }

  @Override
  public synchronized int getCurrentStatus() {
    return mCurrentStatus;
  }

  @Override
  public synchronized void setCurrentStatus(int currentStatus) {
    this.mCurrentStatus = currentStatus;
  }

  @Override
  public void startConnect() {
    mIsManualClose = false;
    buildConnect();
  }

  @Override
  public void stopConnect() {
    mIsManualClose = true;
    disconnect();
  }

  @Override
  public boolean isConnected() {
    return mCurrentStatus == WsStatus.CONNECTED;
  }

  private void tryReconnect() {
    if (!mIsNeedReconnect | mIsManualClose) {
      return;
    }

    if (!isNetworkConnected(mContext)) {
      setCurrentStatus(WsStatus.DISCONNECTED);
      return;
    }

    setCurrentStatus(WsStatus.RECONNECT);

    long delay = mReconnectCount * RECONNECT_INTERVAL;
    mHandler
        .postDelayed(reconnectRunnable, delay > RECONNECT_MAX_TIME ? RECONNECT_MAX_TIME : delay);
    mReconnectCount++;
  }

  private void cancelReconnect() {
    mHandler.removeCallbacks(reconnectRunnable);
    mReconnectCount = 0;
  }

  private void connected() {
    cancelReconnect();
  }

  private void disconnect() {
    if (mCurrentStatus == WsStatus.DISCONNECTED) {
      return;
    }
    cancelReconnect();
    if (mOkHttpClient != null) {
      mOkHttpClient.dispatcher().cancelAll();
    }
    if (mWebSocket != null) {
      boolean isClosed = mWebSocket.close(NORMAL_CLOSE, MSG_NORMAL_CLOSE);
      //非正常关闭连接
      if (!isClosed) {
        if (mWsStatusListener != null) {
          mWsStatusListener.onClosed(ABNORMAL_CLOSE, MSG_ABNORMAL_CLOSE);
        }
      }
    }
    setCurrentStatus(WsStatus.DISCONNECTED);
  }

  private synchronized void buildConnect() {
    if (!isNetworkConnected(mContext)) {
      setCurrentStatus(WsStatus.DISCONNECTED);
      return;
    }
    switch (getCurrentStatus()) {
      case WsStatus.CONNECTED:
      case WsStatus.CONNECTING:
        break;
      default:
        setCurrentStatus(WsStatus.CONNECTING);
        initWebSocket();
    }
  }

  //发送消息
  @Override
  public boolean sendMessage(String msg) {
    return send(msg);
  }

  @Override
  public boolean sendMessage(ByteString byteString) {
    return send(byteString);
  }

  private boolean send(Object msg) {
    boolean isSend = false;
    if (mWebSocket != null && mCurrentStatus == WsStatus.CONNECTED) {
      if (msg instanceof String) {
        isSend = mWebSocket.send((String) msg);
      } else if (msg instanceof ByteString) {
        isSend = mWebSocket.send((ByteString) msg);
      }
      //发送消息失败，尝试重连
      if (!isSend) {
        tryReconnect();
      }
    }
    return isSend;
  }

  //检查网络是否连接
  private boolean isNetworkConnected(Context context) {
    if (context != null) {
      ConnectivityManager mConnectivityManager = (ConnectivityManager) context
          .getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
      if (mNetworkInfo != null) {
        return mNetworkInfo.isAvailable();
      }
    }
    return false;
  }

  public static final class Builder {

    private Context mContext;
    private String mWsUrl;
    private boolean needReconnect = true;
    private OkHttpClient mOkHttpClient;

    public Builder(Context val) {
      mContext = val;
    }

    public Builder mWsUrl(String val) {
      mWsUrl = val;
      return this;
    }

    public Builder client(OkHttpClient val) {
      mOkHttpClient = val;
      return this;
    }

    public Builder needReconnect(boolean val) {
      needReconnect = val;
      return this;
    }

    public Ws build() {
      return new Ws(this);
    }
  }
}
