package com.m00ware.ftpindex.indexer;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.Node;
import com.m00ware.ftpindex.TempChild;
import com.m00ware.ftpindex.Node.NodeType;

/**
 * @author Wooden
 * 
 */
public class IndexerRunnable implements Runnable {
    private FTPClient ftpClient;
    private FTP ftp;
    private boolean connected;
    private boolean utf8;

    public IndexerRunnable(FTP ftp) {
        ftpClient = new FTPClient();
        this.ftp = ftp;
    }

    public void init() throws SocketException, IOException {
        utf8 = false;
        ftpClient.setDataTimeout(5000);
        ftpClient.setConnectTimeout(3500);
        ftpClient.setDefaultTimeout(15000);
        ftpClient.setRemoteVerificationEnabled(false);
        ftpClient.connect(ftp.getAddress(), ftp.getPort());
        if (ftpClient.login("anonymous", "m00")) {
            if (ftpClient.sendCommand("FEAT") == 211) {
                for (String line : ftpClient.getReplyStrings()) {
                    if ("UTF8".equals(line.trim())) {
                        if (ftpClient.sendCommand("OPTS", "UTF8 ON") == 200) {
                            ftpClient.setControlEncoding("UTF8");
                            utf8 = true;
                        }
                        break;
                    }
                }
            }
            this.connected = true;
        }
        System.out.println("Using encoding : " + ftpClient.getControlEncoding());
    }

    @Override
    public void run() {
        ftp.acquireIndexingLock();
        try {
            Directory root = ftp.getRoot();
            root.incIndexingId();
            this.explore(root);
        } catch (Exception e) {}
        ftp.releaseIndexingLock();
    }

    public void explore(Directory directory) {
        if (Thread.currentThread().isInterrupted() || !this.ftpClient.isConnected()) {
            return;
        }
        try {
            String cdto = directory.getName();
            if (directory.isRoot())
                cdto = this.ftp.getPathSeparator();
            if (ftpClient.changeWorkingDirectory(cdto)) {
                List<TempChild> tempChildren = new LinkedList<TempChild>();
                for (FTPFile file : ftpClient.listFiles(".")) {
                    String name = file.getName();
                    if (utf8 && name.contains("\uFFFD")) { // invalid char drop UTF8
                        System.out.println("Invalid file name, dropping UTF8 support");
                        ftpClient.setControlEncoding(org.apache.commons.net.ftp.FTP.DEFAULT_CONTROL_ENCODING);
                        // rollback and re-try
                        ftpClient.cdup();
                        this.explore(directory);
                        return;
                    }
                    if (name.equals(".") || name.equals(".."))
                        continue;
                    if (file.isDirectory()) {
                        tempChildren.add(new TempChild(name, file.getTimestamp(), file.getSize(), NodeType.directory));
                    } else if (file.isSymbolicLink()) {
                        if (file.getLink().endsWith(this.ftp.getPathSeparator())) {
                            tempChildren.add(new TempChild(name, file.getTimestamp(), file.getSize(), NodeType.directory));
                        } else {
                            if (ftpClient.changeWorkingDirectory(name)) {
                                ftpClient.cdup();
                                tempChildren.add(new TempChild(name, file.getTimestamp(), file.getSize(), NodeType.directory));
                            } else {
                                tempChildren.add(new TempChild(name, file.getTimestamp(), file.getSize(), NodeType.file));
                            }
                        }
                    } else if (file.isFile()) {
                        tempChildren.add(new TempChild(name, file.getTimestamp(), file.getSize(), NodeType.file));
                    }
                }
                List<Directory> toExplore = directory.checkAddChilds(tempChildren);
                List<Node> toRemove = new LinkedList<Node>();
                for (Node node : directory.getOutDatedChildren()) {
                    node.setOutDated(true);
                    if (System.currentTimeMillis() - node.getLastSeen().getTime() > ftp.getOutdatedLimit()) {
                        toRemove.add(node);
                    } else {
                        ftp.getDb().updateNode(node);
                    }
                    if (node instanceof Directory) {
                        deepOutDated((Directory) node, toRemove);
                    }
                }
                directory.removeChildren(toRemove);
                for (Directory dir : toExplore)
                    this.explore(dir);
                if (this.ftpClient.isConnected())
                    ftpClient.changeToParentDirectory();
            } else {
                directory.setSuspect(true);
                this.ftp.getDb().updateNode(directory);
                System.out.println("Failed to change directory to '" + directory.getName() + "' from " + (directory.getParent() != null ? directory.getParent()
                                                                                                                                                   .getPath() : "(Root) " + directory.getName()));
            }
        } catch (IOException ioe) {
            if (ioe.getMessage() == null || !ioe.getMessage().startsWith("Host attempting data connection")) {
                try {
                    if (ioe.getMessage() != null)
                        System.err.println("IOException while indexing " + this.ftp + " : " + ioe.getMessage());
                    else
                        ioe.printStackTrace();
                    ftpClient.disconnect();
                } catch (IOException ioe2) {} catch (NullPointerException npe) {}
            } else {
                System.err.println(ioe.getMessage());
            }
        } catch (Exception e) {
            try {
                if (e.getMessage() != null)
                    System.err.println("Exception while indexing " + this.ftp + " : " + e.getMessage());
                else
                    e.printStackTrace();
            } catch (Exception ioe2) {}
        }
    }

    public void deepOutDated(Directory directory, List<Node> toRemove) {
        for (Node node : directory.getChildren()) {
            node.setOutDated(true);
            boolean containsParent = toRemove.contains(directory);
            if (!containsParent && System.currentTimeMillis() - node.getLastSeen().getTime() > ftp.getOutdatedLimit()) {
                toRemove.add(node);
            } else {
                ftp.getDb().updateNode(node);
            }
            if (node instanceof Directory) {
                if (!containsParent)
                    deepOutDated((Directory) node, toRemove);
                else
                    deepOutDated((Directory) node);
            }
        }
    }

    public void deepOutDated(Directory directory) {
        for (Node node : directory.getChildren()) {
            node.setOutDated(true);
            ftp.getDb().updateNode(node);
            if (node instanceof Directory) {
                deepOutDated((Directory) node);
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
