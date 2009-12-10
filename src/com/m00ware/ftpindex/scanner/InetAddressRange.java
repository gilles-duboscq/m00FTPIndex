package com.m00ware.ftpindex.scanner;

import java.net.InetAddress;
import java.util.Iterator;

/**
 * @author Wooden
 *
 */
public abstract class InetAddressRange implements Iterator<InetAddress>
{
	public abstract void reset();
	
	@Override
	public void remove()
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public abstract String toString();
}
