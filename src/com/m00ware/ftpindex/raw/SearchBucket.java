package com.m00ware.ftpindex.raw;

import java.nio.ByteBuffer;

import com.m00ware.ftpindex.raw.xr.ExtensibleRecord;

/**
 * @author Wooden
 *
 */
public class SearchBucket extends ExtensibleRecord
{
	private int start;
	private int size;
	private int appendPosition;
	private ByteBuffer tempBuffer;
	
	public SearchBucket(int start, int size)
	{
		this.start = start;
		this.size = size;
		this.appendPosition = 0;
	}
	
	public int getSize()
	{
		return size;
	}
	
	public int getStart()
	{
		return start;
	}
	
	public int getAppendPosition()
	{
		return appendPosition;
	}
	
	public void setAppendPosition(int appendPosition)
	{
		this.appendPosition = appendPosition;
	}
	
	public ByteBuffer getTempBuffer()
	{
		return tempBuffer;
	}
	
	public void setTempBuffer(ByteBuffer tempBuffer)
	{
		this.tempBuffer = tempBuffer;
	}
	
	@Override
	public String toString()
	{
		return "SearchBucket [start:0x"+Long.toHexString(this.start)+" size:0x"+Long.toHexString(this.size)+"]";
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
