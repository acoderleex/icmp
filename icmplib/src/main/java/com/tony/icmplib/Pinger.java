/*
 * Copyright (c) 2019 Tony
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.tony.icmplib;


import android.util.SparseArray;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Pinger {

    private OnPingListener onPingListener;

    private static final byte[] SEND_PREFIX_ARRAY = new byte[]{0x4c, (byte) 0xc6, (byte) 0xa5, 0x00, 0x55, (byte) 0xaa};
    private static final String SEND_PREFIX = "4cc6a50055aa";
    private static final String SEND_ERROR = "4552524f520000000000";
    private static final String SEND_TIMEOUT = "54494d454f5554000000";
    private static final int DEFAULT_TIMEOUT = 1000;
    private static final int DEFAULT_SLEEP = 100;
    private static final int DEFAULT_TTL = 64;
    private static final int DEFAULT_SIZE = 292;

    private static List<String> whiteOrderList = new ArrayList<>();

    /**
     * 加入白名单 满足有些Order需要一直运行的情况
     * 大部分Order只运行一次
     *
     * @param whiteOrderList
     */
    public void add2WhiteOrderList(List<String> whiteOrderList) {
        Pinger.whiteOrderList.clear();
        Pinger.whiteOrderList.addAll(whiteOrderList);
    }

    private AtomicInteger lastId = new AtomicInteger(0);
    private SparseArray<Thread> pingThreads = new SparseArray<>();
    private SparseArray<Thread> pingIdArray = new SparseArray<>();

    public void setOnPingListener(OnPingListener onPingListener) {
        this.onPingListener = onPingListener;
    }

    private String byte2Hex(byte[] bytes) {
        StringBuilder stringBuffer = new StringBuilder();
        String temp = null;
        for (int i = 0; i < bytes.length; i++) {
            temp = Integer.toHexString(bytes[i] & 0xFF);
            if (temp.length() == 1)
                stringBuffer.append("0");
            stringBuffer.append(temp);
        }
        return stringBuffer.toString();
    }

    private byte[] hexToByte(String hex) {
        int m = 0, n = 0;
        int byteLen = hex.length() / 2; // 每两个字符描述一个字节
        byte[] ret = new byte[byteLen];
        for (int i = 0; i < byteLen; i++) {
            m = i * 2 + 1;
            n = m + 1;
            int intVal = Integer.decode("0x" + hex.substring(i * 2, m) + hex.substring(m, n));
            ret[i] = (byte) intVal;
        }
        return ret;
    }


    private String stringToHex(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + s4;
        }
        return SEND_PREFIX + str;
    }

    private String hexToString(String s) {
        if (s == null || s.equals("")) {
            return null;
        }
        s = s.replace(" ", "");
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                baKeyword[i] = (byte) (0xff & Integer.parseInt(
                        s.substring(i * 2, i * 2 + 2), 16));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            s = new String(baKeyword, "UTF-8");
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return s;
    }

    private byte[] byteMergerAll(byte[]... values) {
        byte[] all_byte = new byte[DEFAULT_SIZE];
        int countLength = 0;
        for (int i = 0; i < values.length; i++) {
            byte[] b = values[i];
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }

    public int Ping(String host, String action) {
        return this.Ping(host, DEFAULT_TIMEOUT, DEFAULT_SLEEP, DEFAULT_TTL, DEFAULT_SIZE, action);
    }

    public int Ping(final String host, int timeout, int sleep, int ttl, int size, final String action) {
        StopAll();
        int pingId = lastId.addAndGet(1);
        Thread pingThread = new Thread(new PingRunnable(this, pingId, host, timeout, sleep, ttl, size, byteMergerAll(SEND_PREFIX_ARRAY, action.getBytes())));
        pingThreads.append(pingId, pingThread);
        pingThread.start();
        if (whiteOrderList.indexOf(action) == -1)
            pingIdArray.append(pingId, pingThread);

        return pingId;
    }

    public void Stop(int pingId) {
        Thread thread = pingThreads.get(pingId);
        if (thread != null)
            thread.interrupt();
        pingThreads.remove(pingId);
    }

    private void stopAllOncePingId() {
        for (int i = 0, size = pingIdArray.size(); i < size; i++) {
            Thread thread = pingIdArray.valueAt(i);
            if (thread != null)
                thread.interrupt();
        }
        pingIdArray.clear();
    }

    public void StopAll() {
        for (int i = 0, size = pingThreads.size(); i < size; i++) {
            Thread thread = pingThreads.valueAt(i);
            if (thread != null)
                thread.interrupt();
        }
        pingThreads.clear();
    }

    private native int createicmpsocket(String host, int timeout, int ttl);

    private native void closeicmpsocket(int sock);

    private native byte[] ping(int sock, char sequence, int size, byte[] pattern);

    static {
        System.loadLibrary("icmp_util");
    }


    private class PingRunnable implements Runnable {

        private String host;
        private int timeout;
        private int sleep;
        private int ttl;
        private int size;
        private int id;
        private byte[] pattern;
        private Pinger pinger;

        PingRunnable(final Pinger pinger, final int id, final String host, final int timeout, final int sleep, final int ttl, final int size, final byte[] pattern) {
            this.pinger = pinger;
            this.id = id;
            this.host = host;
            this.timeout = timeout;
            this.sleep = sleep;
            this.ttl = ttl;
            this.size = size;
            this.pattern = pattern == null ? new byte[]{} : pattern;
        }

        @Override
        public void run() {
            InetAddress address;
            String hostAddress, reverseName;
            final PingInfo pingInfo = new PingInfo();
            pingInfo.Pinger = pinger;
            pingInfo.PingId = id;
            pingInfo.Size = size;
            pingInfo.Timeout = timeout;
            pingInfo.Ttl = ttl;
            pingInfo.RemoteHost = host;
            try {
                address = Inet4Address.getByName(host);
                hostAddress = address.getHostAddress();
                reverseName = Inet4Address.getByName(hostAddress).getCanonicalHostName();
                pingInfo.RemoteIp = hostAddress;
                pingInfo.ReverseDns = reverseName;
            } catch (UnknownHostException e) {
                if (onPingListener != null)
                    onPingListener.OnException(pingInfo, e, true);
                return;
            }

            try {
                if (address.isReachable(100))
                    doPingAction(hostAddress, timeout, sleep, ttl, pingInfo, size, pattern, onPingListener);
                else {
                    if (onPingListener != null)
                        onPingListener.OnException(pingInfo, new NoNetworkException(), true);
                }
            } catch (IOException e) {
                e.printStackTrace();

            }
        }
    }


    private void doPingAction(String hostAddress, int timeout, int sleep, int ttl, PingInfo pingInfo, int size, final byte[] pattern, OnPingListener onPingListener) {
        final int sock = createicmpsocket(hostAddress, timeout, ttl);
        if (sock <= 0) {
            if (onPingListener != null)
                onPingListener.OnException(pingInfo, new SocketException(), true);
            return;
        }
        if (onPingListener != null)
            onPingListener.OnStart(pingInfo);
        char seq = 1;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    byte[] response = ping(sock, seq, size, pattern == null ? new byte[]{} : pattern);
                    String allContent = byte2Hex(response);
//                        SCLog.i("==out=all==" + allContent);
                    stopAllOncePingId();
                    if (onPingListener != null) {
                        if (SEND_ERROR.equals(allContent)) {
                            SCLog.i("==out=all=SEND_ERROR=");
                            onPingListener.OnSendError(pingInfo, seq);
                        } else if (SEND_TIMEOUT.equals(allContent)) {
                            SCLog.i("==out=all=SEND_TIMEOUT=");
                            onPingListener.OnTimeout(pingInfo, seq);
                        } else {
                            if (allContent.contains(SEND_PREFIX)) {
                                String content = allContent.substring(16 + SEND_PREFIX.length(), 16 * 2 * 3 + SEND_PREFIX.length());
                                content = hexToString(content);
                                onPingListener.OnReplyReceived(pingInfo, seq, content);
                            }
                        }
                    }
                    Thread.sleep(sleep);
                    seq++;
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    if (onPingListener != null)
                        onPingListener.OnException(pingInfo, e, true);

                }
            }

        } catch (InterruptedException e) {
            // it's ok
        }
        if (onPingListener != null)
            onPingListener.OnStop(pingInfo);
        closeicmpsocket(sock);
    }

    public interface OnPingListener {
        void OnStart(PingInfo pingInfo);

        void OnStop(PingInfo pingInfo);

        void OnSendError(PingInfo pingInfo, int sequence);

        void OnReplyReceived(PingInfo pingInfo, int sequence, String allContent);

        void OnTimeout(PingInfo pingInfo, int sequence);

        void OnException(PingInfo pingInfo, Exception e, boolean isFatal);
    }
}
