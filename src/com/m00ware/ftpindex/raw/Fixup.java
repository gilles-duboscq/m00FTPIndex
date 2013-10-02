package com.m00ware.ftpindex.raw;

public class Fixup
{
	private int fixupPos;
	
	public Fixup(int position)
	{
		this.fixupPos = position;
	}

	public int getFixupPos()
	{
		return fixupPos;
	}

	public void makeAbsolute(int base)
	{
		this.fixupPos += base;
	}
	
	@Override
	public String toString()
	{
		return " fixup=0x"+Long.toHexString(this.fixupPos);
	}

}