package com.m00ware.ftpindex;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ftp.FTPClient;

/**
 * @author Wooden
 *
 */
public class PingerScheduler
{
	private ScheduledExecutorService executorPing;
	private int defaultPingDelay;
	private List<FTP> scheduledFTPs;
	
	public PingerScheduler(FilesDB db)
	{
		this.executorPing = Executors.newScheduledThreadPool(2, new DeamonThreadFactory());
		this.defaultPingDelay = 15;
		db.registerEventListener(new DBListener());
		this.scheduledFTPs = new LinkedList<FTP>();
	}
	
	public synchronized void schedulePing(FTP ftp, int delay){
		this.scheduledFTPs.add(ftp);
		this.executorPing.scheduleWithFixedDelay(new PingTask(ftp), 3, delay, TimeUnit.SECONDS);
	}
	
	public void shutdown(){
		this.executorPing.shutdownNow();
	}
	
	public int getDefaultPingDelay()
	{
		return this.defaultPingDelay;
	}
	
	public synchronized void setDefaultPingDelay(int defaultPingDelay)
	{
		if(defaultPingDelay != this.defaultPingDelay){
			this.defaultPingDelay = defaultPingDelay;
			this.executorPing.shutdown();
			this.executorPing = Executors.newScheduledThreadPool(2, new DeamonThreadFactory());
			int i = 0;
			for(FTP ftp : scheduledFTPs){
				this.executorPing.scheduleWithFixedDelay(new PingTask(ftp), defaultPingDelay+(i++), defaultPingDelay, TimeUnit.SECONDS);
			}
		}
	}
	
	private class DBListener extends DBEventListener{
		
		@Override
		public void newFtp(FTP ftp)
		{
			schedulePing(ftp, defaultPingDelay);
		}
	}
	
	private static class PingTask implements Runnable{
		private FTP ftp;

		public PingTask(FTP ftp)
		{
			this.ftp = ftp;
		}
		
		@Override
		public void run()
		{
			try{
				if(ftp.isIndexingInProgress())
					return;
				FTPClient ftpClient = new FTPClient();
				ftpClient.setDataTimeout(2500);
				ftpClient.setDefaultTimeout(15000);
				ftpClient.setConnectTimeout(2500);
				try{
					ftpClient.connect(ftp.getAddress(), ftp.getPort());
					ftp.setUp(ftpClient.login("anonymous", "m00"));
					ftpClient.disconnect();
				}catch(IOException ioe){
					ftp.setUp(false);
				}
			}catch(Exception e){}
		}
		
	}
}
