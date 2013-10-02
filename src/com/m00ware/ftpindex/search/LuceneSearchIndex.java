package com.m00ware.ftpindex.search;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.LockObtainFailedException;

import com.m00ware.ftpindex.*;

/**
 * not that great...
 * maybe build something custom?
 * 
 * @author Wooden
 * 
 */
public class LuceneSearchIndex implements SearchIndex {
    private Lock writeLock;
    private IndexSearcher searcher;
    private FilesDB db;
    private Analyzer analyzer;
    private String indexPath;

    public LuceneSearchIndex(String indexPath, FilesDB db) throws CorruptIndexException, IOException {
        this.indexPath = indexPath;
        this.db = db;
        this.writeLock = new ReentrantLock();
        this.analyzer = new StandardAnalyzer();
    }

    @Override
    public SearchResults search(String search, int page, int resultsPerPage) {
        List<Node> results = new LinkedList<Node>();
        try {
            if (this.searcher == null) {
                this.searcher = new IndexSearcher(this.indexPath);
            }
            QueryParser parser = new QueryParser("name", this.analyzer);
            TopDocs hits = this.searcher.search(parser.parse(search), page * resultsPerPage);
            int startIdx = (page - 1) * resultsPerPage;
            if (hits.totalHits < startIdx) {
                return new SearchResults(results, 0);// no hits
            }
            int end = Math.min(hits.totalHits, page * resultsPerPage);
            for (int i = startIdx; i < end; i++) {
                Document doc = searcher.doc(hits.scoreDocs[i].doc);
                String ftpStr = doc.get("ftp");
                String pathStr = doc.get("path");
                String name = doc.get("name");
                String[] split = ftpStr.split("#");
                if (split.length != 2) {
                    continue;
                }
                try {
                    InetAddress addr = InetAddress.getByName(split[0]);
                    int port = Integer.parseInt(split[1]);
                    FTP ftp = db.getFTP(addr, port, false);
                    if (ftp != null) {
                        String[] pathTerms = pathStr.split(ftp.getPathSeparator());
                        Node node = ftp.getRoot();
                        for (int j = 1; j < pathTerms.length && node != null; j++) {
                            if (node instanceof Directory) {
                                Directory dir = (Directory) node;
                                node = dir.getChild(pathTerms[j]);
                            } else {
                                node = null;
                            }
                        }
                        if (node instanceof Directory) {
                            Directory dir = (Directory) node;
                            node = dir.getChild(name);
                        } else {
                            node = null;
                        }
                        if (node != null) {
                            results.add(node);
                        }
                    }
                } catch (UnknownHostException uhe) {} catch (NumberFormatException nfe) {}

            }
            return new SearchResults(results, hits.totalHits);
        } catch (IOException ioe) {
            ioe.printStackTrace();

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new SearchResults(results, 0);
    }

    public void updateIndex(List<Node> newNodes, List<Node> removedNodes) throws CorruptIndexException, LockObtainFailedException, IOException {
        writeLock.lock();
        IndexWriter writer = new IndexWriter(this.indexPath, this.analyzer, IndexWriter.MaxFieldLength.LIMITED);
        for (Node node : newNodes) {
            if (node.isRoot()) {
                continue;
            }
            Document doc = new Document();
            String ftpStr = node.getFtp().getAddress().getHostName() + "#" + node.getFtp().getPort();
            doc.add(new Field("ftp", ftpStr, Store.YES, Index.NOT_ANALYZED));
            doc.add(new Field("path", node.getParent().getPath(), Store.YES, Index.NOT_ANALYZED));
            doc.add(new Field("name", node.getName(), Store.YES, Index.ANALYZED));
            doc.add(new Field("key", ftpStr + node.getParent().getPath() + "#" + node.getName(), Store.YES, Index.ANALYZED));
            writer.addDocument(doc);
        }
        for (Node node : removedNodes) {
            String key = node.getFtp().getAddress().getHostName() + "#" + node.getFtp().getPort() + node.getParent().getPath() + "#" + node.getName();
            writer.deleteDocuments(new Term("key", key));
        }
        writer.optimize();
        writer.close();
        writeLock.unlock();
    }

    public void indexDB() throws CorruptIndexException, LockObtainFailedException, IOException {
        List<Node> nodes = new LinkedList<Node>();
        for (FTP ftp : this.db.getFTPs()) {
            exploreNode(nodes, ftp.getRoot());
        }
        this.updateIndex(nodes, new ArrayList<Node>(0));
    }

    private void exploreNode(List<Node> nodes, Directory directory) {
        List<Directory> toExplore = new LinkedList<Directory>();
        for (Node node : directory.getChildren()) {
            if (node instanceof Directory) {
                toExplore.add((Directory) node);
            }
            nodes.add(node);
        }
        for (Directory dir : toExplore) {
            exploreNode(nodes, dir);
        }
    }
}