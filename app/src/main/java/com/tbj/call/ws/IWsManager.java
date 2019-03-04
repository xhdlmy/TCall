package com.tbj.call.ws;

import okhttp3.WebSocket;
import okio.ByteString;

interface IWsManager {

  WebSocket getWebSocket();

  void startConnect();
  void stopConnect();
  boolean isConnected();

  int getCurrentStatus();
  void setCurrentStatus(int currentStatus);

  boolean sendMessage(String msg);
  boolean sendMessage(ByteString byteString); // ByteString 代表一个不可变的字节数组 byte[] data

}
