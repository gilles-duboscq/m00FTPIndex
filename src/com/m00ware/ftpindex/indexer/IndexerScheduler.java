package com.m00ware.ftpindex.indexer;

import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.m00ware.ftpindex.DBEventListener;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.FilesDB;

/**
 * @author Wooden
 *
 */
public class IndexerScheduler
{
	private FilesDB db;
	private long indexingDelay;
	private ScheduledExecutorService executorIndex;
	private List<IndexerEventListener> listeners;
	
	public IndexerScheduler(FilesDB db)
	{
		this.db = db;
		this.indexingDelay = TimeUnit.MINUTES.toSeconds(30);
		this.executorIndex = Executors.newScheduledThreadPool(4);
		this.db.registerEventListener(new DBListener());
		this.listeners = new LinkedList<IndexerEventListener>();
		Random rnd = new Random();
		for(FTP ftp : this.db.getFTPs()){
			long initialDelay = this.indexingDelay+rnd.nextInt(120)*30;
			System.out.println("Scheduled Indexation for "+ftp.getAddress().getHostName()+":"+ftp.getPort()+" in "+initialDelay+"s and periodicaly with "+indexingDelay+"s delay");
			executorIndex.scheduleWithFixedDelay(new Indexer(ftp), initialDelay, this.indexingDelay, TimeUnit.SECONDS);
		}
	}
	
	public void setIndexingDelay(long indexingDelay, TimeUnit unit)
	{
		this.indexingDelay = unit.toSeconds(indexingDelay);
	}
	
	public void scheduleEarlyIndexing(){
		Random rnd = new Random();
		for(FTP ftp : this.db.getFTPs()){
			int delay = 8+rnd.nextInt(30);
			System.out.println("Scheduled Indexation for "+ftp.getAddress().getHostName()+":"+ftp.getPort()+" in "+delay+"s");
			executorIndex.schedule(new Indexer(ftp), delay, TimeUnit.SECONDS);
		}
	}
	
	public void registerIndexerEventListener(IndexerEventListener iel){
		this.listeners.add(iel);
	}
	
	public void removeIndexerEventListener(IndexerEventListener iel){
		this.listeners.remove(iel);
	}
	
	private class DBListener extends DBEventListener{
		
		@Override
		public void newFtp(FTP ftp)
		{
			System.out.println("Scheduled Indexation for "+ftp.getAddress().getHostName()+":"+ftp.getPort()+" in 5s and periodicaly with "+indexingDelay+"s delay");
			executorIndex.scheduleWithFixedDelay(new Indexer(ftp), 5, indexingDelay, TimeUnit.SECONDS);
		}
	}
	
	private static class Indexer implements Runnable{
		private FTP ftp;

		public Indexer(FTP ftp)
		{
			this.ftp = ftp;
		}

		@Override
		public void run()
		{
			if(!ftp.isUp() || ftp.isIndexingInProgress()){
				System.out.println("Can not start indexer for "+ftp.getAddress().getHostName()+":"+21+" Status: "+(ftp.isUp() ? "Up" : "Down")+" Last indexed: "+ftp.getLastIndexedString());
				return;
			}
			System.out.println("Starting indexer for "+ftp.getAddress().getHostName()+":"+21);
			try{
				IndexerRunnable ir = new IndexerRunnable(ftp);
				ir.init();
				ir.run();
			}catch(SocketTimeoutException ste){
				ftp.setUp(false);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
	}

	public void shutdownt()
	{
		this.executorIndex.shutdownNow();
	}
}
