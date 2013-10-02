package com.m00ware.ftpindex;

/**
 * @author Wooden
 * 
 */
public abstract class DBEventListener {
    public void newFtp(FTP ftp) {
        // dummy;
    }

    public void newFile(File file) {
        // dummy!
    }

    public void newDirectory(Directory directory) {
        // dummy!
    }

    public void removedFile(File file) {
        // dummy!
    }

    public void removedDirectory(Directory directory) {
        // dummy!
    }
}
