package com.m00ware.ftpindex;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DeamonThreadFactory implements ThreadFactory{
	private ThreadFactory defaultTF;
	
	public DeamonThreadFactory()
	{
		this.defaultTF = Executors.defaultThreadFactory();
	}
	
	@Override
	public Thread newThread(Runnable r)
	{
		Thread thread = this.defaultTF.newThread(r);
		thread.setDaemon(true);
		return thread;
	}
	
}