package com.m00ware.ftpindex.web;

import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import com.m00ware.ftpindex.*;
import com.m00ware.ftpindex.web.text.*;

/**
 * @author Wooden
 * 
 */
public class BrowseHttpRequestHandler extends BaseHttpRequestHandler {
    private Tab tab;

    public BrowseHttpRequestHandler(WebBackEnd backEnd) {
        super(backEnd);
        tab = new Tab("Browse", "/browse/");
        backEnd.addtab(tab);
    }

    @Override
    public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        if (!request.getRequestLine().getMethod().equals("GET")) {
            throw new MethodNotSupportedException(request.getRequestLine().getMethod() + " : Method not supported (->You could try a mighty GET huh?");
        }
        if (request.getRequestLine().getUri().equals("/browse") || request.getRequestLine().getUri().equals("/")) {
            response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(new BasicHeader("Location", "/browse/"));
            return;
        }

        String uri = URLDecoder.decode(request.getRequestLine().getUri(), "UTF8").substring(7);
        List<WebText> lines = new LinkedList<WebText>();
        String pageTitle = "m00";
        if (uri.length() > 1) {
            pageTitle = ftpAction(uri, lines);
        } else {
            pageTitle = defaultAction(uri, lines);
        }

        writeM00Page(lines, pageTitle, response, this.tab);

    }

    private String ftpAction(String uri, List<WebText> lines) {
        int idx = uri.indexOf('/', 1);
        String ftpStr;
        String path;
        if (idx < 0) {
            ftpStr = uri.substring(1);
            path = "/";
        } else {
            ftpStr = uri.substring(1, idx);
            path = uri.substring(idx);
        }
        String[] split = ftpStr.split(":");
        if (split.length != 2) {
            throw new WebException("Could not parse ftp string...");
        }
        FTP ftp = null;
        try {
            InetAddress addr = InetAddress.getByName(split[0]);
            int port = Integer.parseInt(split[1]);
            ftp = this.getBackEnd().getDb().getFTP(addr, port, false);
        } catch (Exception e) {
            throw new WebException("Could not parse ftp string : " + e.getMessage());
        }
        if (ftp == null) {
            throw new WebException("Could not parse ftp string...");
        }
        Node node = ftp.getNodeFromPath(path);
        if (node == null) {
            throw new WebException("Unknown node");
        }
        if (!(node instanceof Directory)) {
            throw new WebException("Invalide node type for listing (" + node.getClass().getSimpleName() + ")");
        }
        Directory directory = (Directory) node;
        int longest = 0;
        for (Node child : directory.getChildren()) {
            int len = child.getName().length();
            if (len > 50) {
                len = 50;
            }
            if (len > longest) {
                longest = len;
            }
        }
        longest += 2;
        if (longest < 25) {
            longest = 25;
        }
        lines.add(new WebText(""));
        lines.add(new WebText("    FTP : " + ftpStr));
        StringBuilder sb = new StringBuilder("   Listing for : ");
        String name = directory.getName();
        if (directory.isRoot()) {
            name = "(Root)";
        }
        sb.append(StringEscapeUtils.escapeHtml(capStringLength(name, 40)));
        sb.append("  ");
        WebText headText = new WebText(sb.toString());
        if (!directory.isRoot()) {
            headText.addElement(new WebTag("<a href=\"/browse/" + StringEscapeUtils.escapeHtml(ftpStr + directory.getParent().getPath()) + "\">", "</a>",
                                           new WebText("[Up]")));
        }
        sb = new StringBuilder();
        int pad = longest + 14 - headText.getLength();
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append("Last modified");
        headText.addElement(new WebString(sb.toString()));
        lines.add(headText);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy", Locale.UK);
        for (Node child : directory.getChildren()) {
            int len = child.getName().length();
            if (len > 50) {
                len = 50;
            }
            WebText nodeText = new WebText("   ");
            if (child instanceof Directory) {
                if (ftp.isUp()) {
                    nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + child.getPath()) + "\">", "</a>",
                                                   new WebText("+")));
                } else {
                    nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + child.getPath()) + "\">", "</a>",
                                                   new WebText("X")));
                }
                if (child.isOutDated()) {
                    nodeText.addElement(new WebString("#"));
                } else if (child.isSuspect()) {
                    nodeText.addElement(new WebString("?"));
                } else {
                    nodeText.addElement(new WebString(" "));
                }
                nodeText.addElement(new WebTag("<a href=\"/browse/" + StringEscapeUtils.escapeHtml(ftpStr + child.getPath()) + "\">", "</a>",
                                               new WebText(StringEscapeUtils.escapeHtml(capStringLength(child.getName(), 50)))));
            } else if (child instanceof File) {
                if (ftp.isUp()) {
                    nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + child.getPath()) + "\">", "</a>",
                                                   new WebText("-")));
                } else {
                    nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + child.getPath()) + "\">", "</a>",
                                                   new WebText("x")));
                }
                if (child.isOutDated()) {
                    nodeText.addElement(new WebString("#" + StringEscapeUtils.escapeHtml(capStringLength(child.getName(), 50))));
                } else if (child.isSuspect()) {
                    nodeText.addElement(new WebString("?" + StringEscapeUtils.escapeHtml(capStringLength(child.getName(), 50))));
                } else {
                    nodeText.addElement(new WebString(" " + StringEscapeUtils.escapeHtml(capStringLength(child.getName(), 50))));
                }
            }
            sb = new StringBuilder();
            pad = longest - len;
            for (int i = 0; i < pad; i++) {
                sb.append(' ');
            }
            String sizeStr = FTP.formatSize(child.getSize());
            sb.append(sizeStr);
            pad = 9 - sizeStr.length();
            for (int i = 0; i < pad; i++) {
                sb.append(' ');
            }
            sb.append(sdf.format(child.getDate()));
            nodeText.addElement(new WebString(sb.toString()));
            lines.add(nodeText);
        }
        lines.add(new WebText(""));
        sb = new StringBuilder("   ");
        sb.append(directory.getChildren().size()).append(" Nodes");
        pad = longest - sb.length() + 5;
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        String sizeStr = FTP.formatSize(directory.getSize());
        sb.append(sizeStr);
        lines.add(new WebText(sb.toString()));
        lines.add(new WebText(""));
        WebText footText = new WebText("  ");
        footText.addElement(new WebTag("<a href=\"/browse/\">", "</a>", new WebText("Back to FTPs")));
        lines.add(footText);
        return StringEscapeUtils.escapeHtml(name);
    }

    private String defaultAction(String substring, List<WebText> lines) {
        lines.add(new WebText(""));
        Map<FTP, String> ftpStrings = new HashMap<FTP, String>();
        int longest = 0;
        List<FTP> ftps = this.getBackEnd().getDb().getFTPs();
        ArrayList<FTP> sortedFTPs = new ArrayList<FTP>(ftps);
        Collections.sort(sortedFTPs, new Comparator<FTP>() {
            @Override
            public int compare(FTP o1, FTP o2) {
                if (o1.isUp() && !o2.isUp()) {
                    return -1;
                }
                if (!o1.isUp() && o2.isUp()) {
                    return 1;
                }
                long size1 = o1.getRoot().getSize();
                long size2 = o2.getRoot().getSize();
                if (size1 > size2) {
                    return -1;
                }
                if (size1 == size2) {
                    return 0;
                }
                return 1;
            }
        });

        for (FTP ftp : ftps) {
            String ftpStr = ftp.getAddress().getHostName() + ":" + ftp.getPort();
            if (ftpStr.length() > longest) {
                longest = ftpStr.length();
            }
            ftpStrings.put(ftp, ftpStr);
        }
        longest += 2;
        if (longest < 25) {
            longest = 25;
        }
        int pad = longest + 10;
        StringBuilder sb = new StringBuilder("   FTPs :");
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append("Last indexed");
        lines.add(new WebText(sb.toString()));
        long totalSize = 0;
        int numUp = 0;
        for (FTP ftp : sortedFTPs) {
            String ftpStr = ftpStrings.get(ftp);
            WebText ftpText = new WebText("   ");
            if (ftp.isUp()) {
                ftpText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + ftpStr + "\">", "</a>", new WebText("+")));
                numUp++;
            } else {
                ftpText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + ftpStr + "\">", "</a>", new WebText("X")));
            }
            ftpText.addElement(new WebString(" "));
            ftpText.addElement(new WebTag("<a href=\"/browse/" + ftpStr + "\">", "</a>", new WebText(ftpStr)));
            sb = new StringBuilder();
            pad = longest - ftpStr.length();
            for (int i = 0; i < pad; i++) {
                sb.append(' ');
            }
            long size = ftp.getRoot().getSize();
            totalSize += size;
            String sizeStr = FTP.formatSize(size);
            sb.append(sizeStr);
            pad = 9 - sizeStr.length();
            for (int i = 0; i < pad; i++) {
                sb.append(' ');
            }
            sb.append(ftp.getLastIndexedString());
            ftpText.addElement(new WebString(sb.toString()));
            lines.add(ftpText);
        }
        lines.add(new WebText(""));
        sb = new StringBuilder("   ");
        sb.append(ftps.size()).append(" FTPs (").append(numUp).append(" up)");
        pad = longest - sb.length() + 5;
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append(FTP.formatSize(totalSize));
        lines.add(new WebText(sb.toString()));
        return "FTPs";
    }

    private static String capStringLength(String str, int maxLen) {
        if (str.length() <= maxLen) {
            return str;
        }
        int before = (maxLen - 3) / 2;
        int after = str.length() - (maxLen - 3 - before);
        return str.substring(0, before) + "..." + str.substring(after);
    }
}