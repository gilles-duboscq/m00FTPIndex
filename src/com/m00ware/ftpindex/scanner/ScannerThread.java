package com.m00ware.ftpindex.scanner;

import java.net.InetAddress;
import java.util.*;

import org.apache.commons.net.ftp.FTPClient;

/**
 * @author Wooden
 * 
 */
public class ScannerThread extends Thread {
    private InetAddressRange range;
    private long hosthostDelay;
    private long rescanDelay;
    private Set<ScannerEventListener> listeners;
    private int timeout;

    public ScannerThread(InetAddressRange range) {
        this.setName("ScannerThread");
        this.range = range;
        this.listeners = new HashSet<ScannerEventListener>();
        this.timeout = 1000;
    }

    @Override
    public void run() {
        try {
            while (!this.isInterrupted()) {
                System.out.println("Starting scan on " + this.range);
                while (!this.isInterrupted() && this.range.hasNext()) {
                    this.scan(this.range.next(), 21);
                    Thread.sleep(hosthostDelay);
                }
                System.out.println("Scan on " + this.range + " finished, going to sleep");
                if (rescanDelay < 0) {
                    return;
                }
                this.range.reset();
                Thread.sleep(rescanDelay);
            }
        } catch (InterruptedException ie) {}
    }

    private void scan(InetAddress address, int port) {
        // System.out.println("Test "+address+":"+port);
        try {
            FTPClient ftpClient = new FTPClient();
            ftpClient.setDataTimeout(this.timeout);
            ftpClient.setConnectTimeout(this.timeout);
            ftpClient.setDefaultTimeout(/* this.timeout*5 */12000);
            ftpClient.connect(address, port);
            if (ftpClient.login("anonymous", "m00")) {
                for (ScannerEventListener sel : this.listeners) {
                    sel.ftpServerUp(address, port);
                }
            }
            ftpClient.disconnect();
        } catch (Exception e) {}
    }

    public long getHosthostDelay() {
        return hosthostDelay;
    }

    public void setHosthostDelay(long hosthostDelay) {
        this.hosthostDelay = hosthostDelay;
    }

    public long getRescanDelay() {
        return rescanDelay;
    }

    public void setRescanDelay(long rescanDelay) {
        this.rescanDelay = rescanDelay;
    }

    public InetAddressRange getRange() {
        return range;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void registerScannerEventListener(ScannerEventListener sel) {
        this.listeners.add(sel);
    }

    public void removeScannerEventListener(ScannerEventListener sel) {
        this.listeners.remove(sel);
    }

    public void shutdown() {
        this.interrupt();
    }
}