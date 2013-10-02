package com.m00ware.ftpindex.web;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.http.impl.*;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.protocol.BufferingHttpServiceHandler;
import org.apache.http.nio.reactor.*;
import org.apache.http.params.*;
import org.apache.http.protocol.*;

import com.m00ware.ftpindex.*;
import com.m00ware.ftpindex.indexer.IndexerScheduler;
import com.m00ware.ftpindex.scanner.*;

/**
 * @author Wooden
 * 
 */
public class WebBackEnd extends Thread {
    private FilesDB db;
    private ScannerThread scanner;
    private IndexerScheduler indexerScheduler;
    private ListeningIOReactor ioReactor;
    private IOEventDispatch ioEventDispatch;
    private int port;
    private String[] quotes;
    private List<Tab> tabs;
    private String name;
    private int pageWidth;
    private int minPageHeight;
    private Timer dbTimer;
    private PingerScheduler pingScheduler;
    private HttpRequestHandlerRegistry registry;

    public WebBackEnd(FilesDB db, ScannerThread scanner, IndexerScheduler scheduler, PingerScheduler ps, int port) {
        this.db = db;
        this.scanner = scanner;
        this.indexerScheduler = scheduler;
        this.pingScheduler = ps;
        this.port = port;
        this.quotes = new String[0];
        this.tabs = new LinkedList<Tab>();
        this.name = "m00FTPIndexer";
        this.pageWidth = 80;
        this.minPageHeight = 25;

        this.scanner.setTimeout(95);
        this.scanner.setRescanDelay(5 * 60 * 1000);
        this.scanner.registerScannerEventListener(new ScannerListener());

        this.dbTimer = new Timer(true);
        this.dbTimer.schedule(new DbTask(this.db), 5 * 60 * 1000, 5 * 60 * 1000);

        HttpParams params = new BasicHttpParams();
        params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 2500);
        params.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024);
        params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, true);
        params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF8");
        params.setParameter(CoreProtocolPNames.ORIGIN_SERVER, "m00FTPIndex");

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());

        BufferingHttpServiceHandler handler = new BufferingHttpServiceHandler(httpproc, new DefaultHttpResponseFactory(), new DefaultConnectionReuseStrategy(),
                                                                              params);

        registry = new HttpRequestHandlerRegistry();
        BrowseHttpRequestHandler browse = new BrowseHttpRequestHandler(this);
        StatHttpRequestHandler stats = new StatHttpRequestHandler(this);
        SearchHttpRequestHandler search = new SearchHttpRequestHandler(this);
        AboutHttpRequestHandler about = new AboutHttpRequestHandler(this);
        registry.register("/", browse);
        registry.register("/browse", browse);
        registry.register("/browse/*", browse);
        registry.register("/stats", stats);
        registry.register("/search", search);
        registry.register("/search/*", search);
        registry.register("/about", about);

        handler.setHandlerResolver(registry);

        // TODO event logging

        ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        try {
            ioReactor = new DefaultListeningIOReactor(2, params);
        } catch (IOReactorException iore) {
            iore.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            ioReactor.listen(new InetSocketAddress(this.port));
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {} catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    public FilesDB getDb() {
        return db;
    }

    public ScannerThread getScanner() {
        return scanner;
    }

    public IndexerScheduler getScheduler() {
        return indexerScheduler;
    }

    public void loadQuotes(String quotesPath) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(quotesPath));
        List<String> quotesList = new LinkedList<String>();
        String line = null;
        try {
            while ((line = br.readLine()) != null) {
                quotesList.add(line);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        this.quotes = quotesList.toArray(new String[0]);
    }

    public String[] getQuotes() {
        return quotes;
    }

    public List<Tab> getTabs() {
        return this.tabs;
    }

    public void addtab(Tab tab) {
        this.tabs.add(tab);
    }

    public void removeTab(Tab tab) {
        this.tabs.remove(tab);
    }

    public String getWebName() {
        return name;
    }

    public void setWebName(String name) {
        this.name = name;
    }

    public int getPageWidth() {
        return pageWidth;
    }

    public void setPageWidth(int pageWidth) {
        this.pageWidth = pageWidth;
    }

    public int getMinPageHeight() {
        return minPageHeight;
    }

    public void setMinPageHeight(int minPageHeight) {
        this.minPageHeight = minPageHeight;
    }

    public PingerScheduler getPingScheduler() {
        return pingScheduler;
    }

    public void shutdown() {
        this.scanner.shutdown();
        this.indexerScheduler.shutdownt();
        this.db.shutdown();
    }

    private class ScannerListener implements ScannerEventListener {

        @Override
        public void ftpServerUp(InetAddress addr, int port) {
            FTP ftp = db.getFTP(addr, port, true);
            ftp.setUp(true);
        }
    }

    private static class DbTask extends TimerTask {

        private FilesDB db;

        public DbTask(FilesDB db) {
            this.db = db;
        }

        @Override
        public void run() {
            db.forceCommit();
        }
    }
}