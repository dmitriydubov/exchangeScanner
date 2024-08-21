package com.exchange.scanner.services.utils.AppUtils;

public class WebSocketConnectionState {
    private int retryCount;

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public void resetRetryCount() {
        retryCount = 0;
    }
}
