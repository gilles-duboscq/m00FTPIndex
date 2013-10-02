package com.m00ware.ftpindex.raw;

import java.nio.ByteBuffer;

import com.m00ware.ftpindex.raw.xr.ExtensibleRecord;

public class VacantSpace extends ExtensibleRecord implements Comparable<VacantSpace>{
	private int start;
	private int size;
	
	public VacantSpace(int start, int size)
	{
		this.start = start;
		this.size = size;
	}
	
	public int getSize()
	{
		return size;
	}
	
	public int getStart()
	{
		return start;
	}

	@Override
	public int compareTo(VacantSpace o)
	{
		return this.start-o.start;
	}

	// the first byte *after* this vs
	public int getEnd()
	{
		return this.start+this.size;
	}
	
	@Override
	public String toString()
	{
		return "VacantSpace [start:0x"+Long.toHexString(this.start)+" size:0x"+Long.toHexString(this.size)+"]";
	}

	@Override
	protected void doWriteToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.start);
		buffer.putInt(this.size);
	}

	@Override
	protected int getRecordId()
	{
		return 0x01;
	}
}