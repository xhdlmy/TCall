package com.tbj.call;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

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

public class MainActivity extends AppCompatActivity {

    private MockWebServer mMockWebServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_conn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        createSevice();
                        createClient();
                    }
                });
            }
        });
    }

    private WebSocket mWebSocket;

    private Timer mTimer;
    private int msgCount;

    private void startTask(){
        mTimer= new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if(mWebSocket == null) return;
                msgCount++;
                boolean isSuccessed = mWebSocket.send("msg" + msgCount + "-" + System.currentTimeMillis());
                //除了文本内容外，还可以将如图像，声音，视频等内容转为ByteString发送
                //boolean send(ByteString bytes);
            }
        };
        mTimer.schedule(timerTask, 0, 1000);
    }

    public void createSevice(){
        mMockWebServer = new MockWebServer();
        mMockWebServer.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("server onOpen");
                System.out.println("server request header:" + response.request().headers());
                System.out.println("server response header:" + response.headers());
                System.out.println("server response:" + response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String string) {
                System.out.println("server onMessage");
                System.out.println("message:" + string);
                // 接受到5条信息后，关闭消息定时发送器
                if(msgCount == 5){
                    mTimer.cancel();
                    webSocket.close(1000, "close by server");
                    return;
                }
                // WebSocket 发送消息给客户端
                webSocket.send("response-" + string);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("server onClosing");
                System.out.println("code:" + code + " reason:" + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("server onClosed");
                System.out.println("code:" + code + " reason:" + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                //出现异常会进入此回调
                System.out.println("server onFailure");
                System.out.println("throwable:" + t);
                System.out.println("response:" + response);
            }
        }));
    }

    private void createClient(){
        String hostName = mMockWebServer.getHostName();
        int port = mMockWebServer.getPort();
        System.out.println("hostName:" + hostName);
        System.out.println("port:" + port);
        String wsUrl = "ws://" + hostName + ":" + port + "/";
        //新建client
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        //构造request对象
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();
        //建立连接
        client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                mWebSocket = webSocket;
                System.out.println("client onOpen");
                System.out.println("client request header:" + response.request().headers());
                System.out.println("client response header:" + response.headers());
                System.out.println("client response:" + response);
                // 当双方建立连接了，客户端向服务端发送消息 开启消息定时发送
                startTask();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("client onMessage");
                System.out.println("message:" + text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("client onClosing");
                System.out.println("code:" + code + " reason:" + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("client onClosed");
                System.out.println("code:" + code + " reason:" + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                //出现异常会进入此回调
                System.out.println("client onFailure");
                System.out.println("throwable:" + t);
                System.out.println("response:" + response);
            }
        });
    }

}
