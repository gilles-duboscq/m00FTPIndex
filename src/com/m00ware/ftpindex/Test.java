package com.m00ware.ftpindex;

import java.io.*;
import java.io.File;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

import com.m00ware.ftpindex.indexer.*;
import com.m00ware.ftpindex.raw.RawFilesDB;
import com.m00ware.ftpindex.scanner.*;
import com.m00ware.ftpindex.search.*;
import com.m00ware.ftpindex.web.WebBackEnd;

/**
 * @author Wooden
 * 
 */
public class Test {

	public static void main1(String[] args) throws IOException {
		InetAddress addr = InetAddress.getByName("160.228.152.88");
		ScannerThread st = new ScannerThread(new Inet4AddressRange(
				(Inet4Address) addr, 0xfffff800));
		st.setTimeout(85);
		st.setRescanDelay(-1);
		final List<String> servers = new LinkedList<String>();
		st.registerScannerEventListener(new ScannerEventListener() {
			@Override
			public void ftpServerUp(InetAddress addr, int port) {
				String str = "FTP server : " + addr.getHostName() + ":" + port;
				servers.add(str);
				System.out.println(str);
			}
		});
		st.run();
		System.out.println("Found : ");
		for (String str : servers) {
			System.out.println(str);
		}
	}

	public static void main3(String[] args) {
		String[] strs = new String[] {
				"ScannerThread st = new ScannerThread(new Inet4AddressRange((Inet4Address) addr, 0xfffff800));avec le serveur",
				"[15:40]	tu commences � m'�nerver toi...aller encore un �pisode!!!(plus qu'une dizaine avant la lib�ration lol)avec le serveur",
				"Index de ftp://norace.rez-gif.supelec.fr/Videos/Series/Heroes/",
				"Firefox ne peut �tablir de connexion avec le serveur � l'adresse localhost:8080.",
				"public static void main(String[] args) throws IOException, ParseException" };
		System.out.println("Java String bench");
		System.out.println("Warmup...");
		for (int i = 0; i < 100000; i++) {
			strs[i % 5].contains("avec le");
		}
		Pattern pattern = Pattern.compile("avec le");
		for (int i = 0; i < 100000; i++) {
			pattern.matcher(strs[i % 5]).find();
		}
		Charset UTF8 = Charset.forName("UTF8");
		byte[] searchBytes = "avec le".getBytes(UTF8);
		byte[][] strsBytes = new byte[strs.length][];
		for (int i = 0; i < strs.length; i++) {
			strsBytes[i] = strs[i].getBytes(UTF8);
		}
		boolean[] impossible = SearchUtil.getImpossibleArray(searchBytes);
		int md2 = SearchUtil.getMD2(searchBytes);
		for (int i = 0; i < 100000; i++) {
			SearchUtil.bytesContains(searchBytes, strsBytes[i % 5], impossible,
					md2);
		}
		for (int i = 0; i < 100000; i++) {
			strs[i % 5].toLowerCase();
		}
		System.out.println("Benching...");
		int iter = 1000000;
		long t = System.nanoTime();
		for (int i = 0; i < iter; i++) {
			strs[i % 5].contains("avec le serveur");
		}
		t = System.nanoTime() - t;
		System.out.println("Contains : " + iter + " iterations took " + t
				+ "ns : " + t / iter + "ns per iteration");
		t = System.nanoTime();
		for (int i = 0; i < iter; i++) {
			strs[i % 5].toLowerCase();
		}
		t = System.nanoTime() - t;
		System.out.println("ToLower : " + iter + " iterations took " + t
				+ "ns : " + t / iter + "ns per iteration");
		t = System.nanoTime();
		for (int i = 0; i < iter; i++) {
			pattern.matcher(strs[i % 5]).find();
		}
		t = System.nanoTime() - t;
		System.out.println("Matcher.find : " + iter + " iterations took " + t
				+ "ns : " + t / iter + "ns per iteration");
		t = System.nanoTime();
		for (int i = 0; i < iter; i++) {
			SearchUtil.bytesContains(searchBytes, strsBytes[i % 5], impossible,
					md2);
		}
		t = System.nanoTime() - t;
		System.out.println("SearchUtil.bytesContains : " + iter
				+ " iterations took " + t + "ns : " + t / iter
				+ "ns per iteration");
	}

	public static void main6(String[] args) throws IOException {
		RawFilesDB db = new RawFilesDB(new File("blop2-test.mdb"));
		db.setUnusableVacantSpaceThreshold(3);
		db.init();
		dumpDB(db);
		long t = System.currentTimeMillis();
		System.out.println("Begin indexing...");
		FTP ftp = db.getFTP(
				InetAddress.getByName("chen-huard.rez-gif.supelec.fr"), 21);
		IndexerRunnable it = new IndexerRunnable(ftp);
		it.init();
		it.run();
		db.shutdown();
		System.out.println("Explored FTP in "
				+ (System.currentTimeMillis() - t) + "ms, total size="
				+ ftp.getRoot().getSize() + "Bytes");
	}

	public static void main/* 7 */(String[] args) throws IOException {
		RawFilesDB db = new RawFilesDB(new File("blop21.mdb"));
		PingerScheduler ps = new PingerScheduler(db);
		db.init();
		db.setUnusableVacantSpaceThreshold(3);
		InetAddress addr = InetAddress.getByName("duboscq.rez-gif.supelec.fr");
		ScannerThread st = new ScannerThread(new Inet4AddressRange(
				(Inet4Address) addr, 0xfffff800));
		IndexerScheduler is = new IndexerScheduler(db);
		final WebBackEnd wbe = new WebBackEnd(db, st, is, ps, 80);
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

	public static void main8(String[] args) throws IOException {

		RawFilesDB db = new RawFilesDB(new File("blop2.mdb"));
		db.setUnusableVacantSpaceThreshold(3);
		db.init();
		loadDB(db);
		long t = System.currentTimeMillis();

		System.out.println("Begin indexing...");
		FTP ftp = db.getFTP(
				InetAddress.getByName("duboscq.rez-gif.supelec.fr"), 21);
		IndexerRunnable it = new IndexerRunnable(ftp);
		it.init();
		it.run();
		db.shutdown();
		System.out.println("Explored FTP in "
				+ (System.currentTimeMillis() - t) + "ms, total size="
				+ ftp.getRoot().getSize() + "Bytes");

	}

	public static void main9(String[] args) throws IOException {
		RawFilesDB db = new RawFilesDB(new File("blop2.mdb"));
		db.init();
		BasicSearchIndex bsi = new BasicSearchIndex(db);
		System.out.println("Begin search...");
		long t = System.currentTimeMillis();
		SearchResults search = bsi.search("porn", 1, 300);
		t = System.currentTimeMillis() - t;
		System.out.println("BasicSearchIndex found " + search.getTotalResults()
				+ " results in " + t + "ms:");
		for (Node node : search.getNodes()) {
			System.out.println(" - " + node + " " + node.getFtp() + " -> "
					+ node.getPath());
		}
		t = System.currentTimeMillis();
		search = bsi.search("Gossip", 1, 30);
		t = System.currentTimeMillis() - t;
		System.out.println("BasicSearchIndex (cached?) found "
				+ search.getTotalResults() + " results in " + t + "ms:");
		for (Node node : search.getNodes()) {
			System.out.println(" - " + node);
		}

	}

	public static void main10(String[] args) throws IOException {
		RandomAccessFile raf = new RandomAccessFile("testRAF", "rw");
		System.out.println("Filling ByteBuffer...");
		ByteBuffer bb = ByteBuffer.allocateDirect(4 * 16 * 1024);
		for (int i = 0; i < 16 * 1024; i++) {
			bb.putInt(0xAAAAAAAA);
		}
		bb.flip();
		System.out.println("Writing ByteBuffer...");
		raf.getChannel().write(bb);
		System.out.println("Closing file...");
		raf.getChannel().force(true);
		raf.close();
		for (int i = 0; i < 10; i++) {
			System.out.println("Pass " + i + " : Opening file...");
			raf = new RandomAccessFile("testRAF", "rw");
			long count = 0;
			long t = System.nanoTime();
			for (int j = 0; j < 16 * 1024; j++) {
				count += raf.readInt();
			}
			t = System.nanoTime() - t;
			System.out.println("Did " + 16 * 1024 + " raf.readInt() in " + t
					+ "ns -> " + (t / (16 * 1024)) + "ns/readInt()");
			raf.close();
			raf = new RandomAccessFile("testRAF", "rw");
			count = 0;
			ByteBuffer bb2 = ByteBuffer.allocateDirect(1024);
			t = System.nanoTime();
			for (int j = 0; j < 16 * 1024; j++) {
				bb2.clear();
				bb2.limit(4);
				raf.getChannel().read(bb2);
				bb2.flip();
				count += bb2.getInt();
			}
			blackhole = count;
			t = System.nanoTime() - t;
			System.out.println("Did " + 16 * 1024 + " ByteBuffered read in "
					+ t + "ns -> " + (t / (16 * 1024)) + "ns/read");
			raf.close();
		}

	}

	public static long blackhole;

	public static void main11(String[] args) throws IOException {
		RawFilesDB db = new RawFilesDB(new File("blop2-testSearch.mdb"));
		db.setUnusableVacantSpaceThreshold(3);
		db.init();
		long t = System.currentTimeMillis();
		System.out.println("Begin indexing...");
		FTP ftp = db.getFTP(
				InetAddress.getByName("duboscq.rez-gif.supelec.fr"), 21);
		IndexerRunnable it = new IndexerRunnable(ftp);
		it.init();
		it.run();
		System.out.println("Explored FTP in "
				+ (System.currentTimeMillis() - t) + "ms, total size="
				+ ftp.getRoot().getSize() + "Bytes");
		db.doDebugUpdateFTPSearchBuckets(ftp);
		t = System.currentTimeMillis();
		SearchResults search = db.getSearchIndex().search("gossip girl", 1, 10);
		t = System.currentTimeMillis() - t;
		System.out.println("SearchIndex found " + search.getTotalResults()
				+ " results in " + t + "ms:");
		for (Node node : search.getNodes()) {
			System.out.println(" - " + node + " " + node.getFtp() + " -> "
					+ node.getPath());
		}
		db.shutdown();
	}

	public static void loadDB(FilesDB db) {
		System.out.println("Loading DB...");
		for (FTP ftp : db.getFTPs()) {
			loadDirectory(ftp.getRoot());
		}
		System.out.println("DB Loaded");
	}

	public static void loadDirectory(Directory directory) {
		for (Node node : directory.getChildren()) {
			if (node instanceof Directory)
				loadDirectory((Directory) node);
		}
	}

	public static void dumpDB(FilesDB db) {
		System.out.println("Dumping DB...");
		System.out.println("FTP List : ");
		for (FTP ftp : db.getFTPs()) {
			System.out.println("FTP : " + ftp.getAddress().getHostName() + ":"
					+ ftp.getPort());
		}
		System.out.println("Details:");
		for (FTP ftp : db.getFTPs()) {
			System.out.println("FTP : " + ftp.getAddress().getHostName() + ":"
					+ ftp.getPort());
			dumpDirectory(ftp.getRoot(), 0);
		}
	}

	public static void dumpDirectory(Directory directory, int indent) {
		String indentStr = "";
		for (int i = 0; i < indent; i++)
			indentStr += "  ";
		if (directory.isOutDated()) {
			System.out.print(indentStr + "+#");
		} else if (directory.isSuspect()) {
			System.out.print(indentStr + "+?");
		} else {
			System.out.print(indentStr + "+ ");
		}
		System.out.println(directory.getName());
		indentStr += "  ";
		for (Node node : directory.getChildren()) {
			if (node instanceof Directory) {
				dumpDirectory((Directory) node, indent + 1);
			} else if (node instanceof com.m00ware.ftpindex.File) {
				if (node.isOutDated()) {
					System.out.print(indentStr + "+#");
				} else if (node.isSuspect()) {
					System.out.print(indentStr + "+?");
				} else {
					System.out.print(indentStr + "+ ");
				}
				System.out.println(node.getName());
			}
		}
	}

}
