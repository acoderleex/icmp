package com.tony.icmplib;

public class NoNetworkException extends Exception {

    public NoNetworkException() {
        super("没有网络连接");
    }
}
