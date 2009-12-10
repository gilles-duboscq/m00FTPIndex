package com.m00ware.ftpindex;

import java.io.*;
import java.io.File;
import java.net.*;

import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.raw2.RawFilesDB2;
import com.m00ware.ftpindex.scanner.*;
import com.m00ware.ftpindex.web.WebBackEnd;

/**
 * @author Wooden
 * 
 */
public class FTPIndexer {

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		System.setProperty("dns.servers", "160.228.152.4");
		System.setProperty("dns.search", "rez-gif.supelec.fr");
		RawFilesDB2 db = new RawFilesDB2(new File("blaaaaa.mdb"));
		PingerScheduler ps = new PingerScheduler(db);
		db.init();
		db.setUnusableVacantSpaceThreshold(3);
		// InetAddress addr =
		// InetAddress.getByName("duboscq.rez-gif.supelec.fr");
		InetAddress addr = InetAddress.getByName("rez-gif.supelec.fr");
		ScannerThread st = new ScannerThread(new Inet4AddressRange(
				(Inet4Address) addr, 0xfffff800)); // /11
		IndexerScheduler is = new IndexerScheduler(db);
		final WebBackEnd wbe = new WebBackEnd(db, st, is, ps, 8080);
		wbe.loadQuotes("quotes.txt");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				wbe.shutdown();
			}
		});
		is.scheduleEarlyIndexing();
		st.start();
		wbe.run();
	}

}
