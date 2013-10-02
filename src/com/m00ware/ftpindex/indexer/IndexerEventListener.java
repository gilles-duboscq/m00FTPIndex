package com.m00ware.ftpindex.indexer;

import com.m00ware.ftpindex.FTP;

/**
 * @author Wooden
 * 
 */
public interface IndexerEventListener {
    public void indexingStart(FTP ftp);

    public void indexingEnd(FTP ftp);
}