package com.m00ware.ftpindex;

import java.util.concurrent.*;

public class DeamonThreadFactory implements ThreadFactory {
    private final ThreadFactory defaultTF;

    public DeamonThreadFactory() {
        defaultTF = Executors.defaultThreadFactory();
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = defaultTF.newThread(r);
        thread.setDaemon(true);
        return thread;
    }
}