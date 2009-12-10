package com.m00ware.ftpindex.scanner;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;

/**
 * @author Wooden
 *
 */
public class Inet4AddressRange extends InetAddressRange
{
	private Inet4Address base;
	private int mask;
	private byte[] current;

	public Inet4AddressRange(Inet4Address base, int mask)
	{
		this.base = base;
		this.mask = mask;
		this.reset();
	}

	/* (non-Javadoc)
	 * @see com.m00ware.ftpindex.InetAddressRange#reset()
	 */
	@Override
	public void reset()
	{
		current = this.base.getAddress();
		current[3] &= (byte)( mask     &0xff);
		current[2] &= (byte)((mask>>8) &0xff);
		current[1] &= (byte)((mask>>16)&0xff);
		current[0] &= (byte)((mask>>24)&0xff);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext()
	{
		int test = current[3]&0xff;
		test |= (current[2]<<8 )&0xff00;
		test |= (current[1]<<16)&0xff0000;
		test |= (current[0]<<24)&0xff000000;
		return ((test+1)&mask) == (test&mask);
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#next()
	 */
	@Override
	public InetAddress next()
	{
		if(!this.hasNext())
			throw new NoSuchElementException();
		InetAddress addr = null;
		try{
			addr = InetAddress.getByAddress(current);
		}catch(UnknownHostException uhe){}
		current[3]++;
		if(current[3] == 0){
			current[2]++;
			if(current[2] == 0){
				current[1]++;
				if(current[1] == 0){
					current[0]++;
				}
			}
		}
		return addr;
	}

	@Override
	public String toString()
	{
		byte[] tmp = this.base.getAddress();
		tmp[3] &= (byte)( mask     &0xff);
		tmp[2] &= (byte)((mask>>8) &0xff);
		tmp[1] &= (byte)((mask>>16)&0xff);
		tmp[0] &= (byte)((mask>>24)&0xff);
		int bits = 0;
		while(((mask>>bits)&0x01) == 0) bits++;
		return (tmp[0] & 0xff) + "." + (tmp[1] & 0xff) + "." + (tmp[2] & 0xff) + "." + (tmp[3] & 0xff)+"/"+bits;
	}

}
