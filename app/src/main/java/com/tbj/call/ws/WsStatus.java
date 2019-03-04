package com.tbj.call.ws;

/*

  Socket.readyState 只读：
  0 - 表示连接尚未建立。
  1 - 表示连接已建立，可以进行通信。
  2 - 表示连接正在进行关闭。
  3 - 表示连接已经关闭或者连接不能打开。

 */
class WsStatus {

  final static int DISCONNECTED = -1;
  final static int CONNECTING = 0;
  final static int CONNECTED = 1;
  final static int RECONNECT = 2;

}

