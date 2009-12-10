package com.m00ware.ftpindex.scanner;

import java.net.InetAddress;

/**
 * @author Wooden
 *
 */
public interface ScannerEventListener
{
	public void ftpServerUp(InetAddress addr, int port);
}
