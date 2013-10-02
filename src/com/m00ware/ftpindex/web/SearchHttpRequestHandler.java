package com.m00ware.ftpindex.web;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.*;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import sun.nio.cs.ThreadLocalCoders;

import com.m00ware.ftpindex.*;
import com.m00ware.ftpindex.search.*;
import com.m00ware.ftpindex.web.text.*;

/**
 * @author Wooden
 * 
 */
public class SearchHttpRequestHandler extends BaseHttpRequestHandler {
    private SearchIndex index;

    public SearchHttpRequestHandler(WebBackEnd backEnd) {
        super(backEnd);
        index = new BasicSearchIndex(backEnd.getDb());
    }

    @Override
    public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        if (!request.getRequestLine().getMethod().equals("GET")) {
            throw new MethodNotSupportedException(request.getRequestLine().getMethod() + " : Method not supported (->You could try a mighty GET huh?");
        }
        String uri = decode(request.getRequestLine().getUri());
        if (uri.equals("/search")) {
            response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(new BasicHeader("Location", "/search/"));
            return;
        }
        String search;
        int page = 1;
        if (uri.length() > 8) {
            search = uri.substring(8);
            int idx = search.indexOf('?');
            if (idx > 0) {
                String query = search.substring(idx + 1);
                search = search.substring(0, idx);
                idx = query.indexOf('#');
                if (idx >= 0) {
                    query = query.substring(0, idx);
                }
                for (String couple : query.split("&")) {
                    String[] c = couple.split("=");
                    if (c.length >= 1) {
                        if ("page".equals(c[0])) {
                            if (c.length == 2) {
                                try {
                                    page = Integer.decode(c[1]);
                                } catch (NumberFormatException nfe) {}
                            }
                        }
                    }
                }
            }
        } else {
            search = null;
        }
        if (page < 1) {
            page = 1;
        }
        int resultsPerPage = 50;
        List<WebText> lines = new LinkedList<WebText>();
        lines.add(new WebText(""));
        if (search != null) {
            lines.add(new WebText("   Search for : '" + search + "'"));
            SearchResults results = this.index.search(search, page, resultsPerPage);
            int totalResults = results.getTotalResults();
            lines.add(new WebText("  Found " + totalResults + " matches"));
            int longest = 0;
            for (Node node : results.getNodes()) {
                int len = node.getName().length();
                if (len > 50) {
                    len = 50;
                }
                if (len > longest) {
                    longest = len;
                }
            }
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy", Locale.UK);
            for (Node node : results.getNodes()) {
                FTP ftp = node.getFtp();
                String ftpStr = ftp.getAddress().getHostName() + ":" + ftp.getPort();
                int len = node.getName().length();
                if (len > 50) {
                    len = 50;
                }
                WebText nodeText = new WebText("   ");
                if (node instanceof Directory) {
                    if (ftp.isUp()) {
                        nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + node.getPath()) + "\">",
                                                       "</a>", new WebText("+")));
                    } else {
                        nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + node.getPath()) + "\">",
                                                       "</a>", new WebText("X")));
                    }
                    if (node.isOutDated()) {
                        nodeText.addElement(new WebString("#"));
                    } else if (node.isSuspect()) {
                        nodeText.addElement(new WebString("?"));
                    } else {
                        nodeText.addElement(new WebString(" "));
                    }
                    nodeText.addElement(new WebTag("<a href=\"/browse/" + StringEscapeUtils.escapeHtml(ftpStr + node.getPath()) + "\">", "</a>",
                                                   new WebText(StringEscapeUtils.escapeHtml(capStringLength(node.getName(), 50)))));
                } else if (node instanceof File) {
                    if (ftp.isUp()) {
                        nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + node.getPath()) + "\">",
                                                       "</a>", new WebText("-")));
                    } else {
                        nodeText.addElement(new WebTag("<a class=\"nou\" href=\"ftp://" + StringEscapeUtils.escapeHtml(ftpStr + node.getPath()) + "\">",
                                                       "</a>", new WebText("x")));
                    }
                    if (node.isOutDated()) {
                        nodeText.addElement(new WebString("#" + StringEscapeUtils.escapeHtml(capStringLength(node.getName(), 50))));
                    } else if (node.isSuspect()) {
                        nodeText.addElement(new WebString("?" + StringEscapeUtils.escapeHtml(capStringLength(node.getName(), 50))));
                    } else {
                        nodeText.addElement(new WebString(" " + StringEscapeUtils.escapeHtml(capStringLength(node.getName(), 50))));
                    }
                }
                StringBuilder sb = new StringBuilder();
                int pad = longest - len;
                for (int i = 0; i < pad; i++) {
                    sb.append(' ');
                }
                String sizeStr = FTP.formatSize(node.getSize());
                sb.append(sizeStr);
                pad = 9 - sizeStr.length();
                for (int i = 0; i < pad; i++) {
                    sb.append(' ');
                }
                sb.append(sdf.format(node.getDate()));
                nodeText.addElement(new WebString(sb.toString()));
                lines.add(nodeText);
            }
            lines.add(new WebText(""));
            int maxPages = totalResults / resultsPerPage + (totalResults % resultsPerPage == 0 ? 0 : 1);
            if (maxPages > 0) {
                WebText pagesLine = new WebText("  Pages : ");
                int before = 3;
                int after = 3;
                if (page > maxPages) {
                    page = maxPages;
                }
                if (page < 5) {
                    before = page - 2;
                }
                if (page == 1) {
                    pagesLine.addElement(new WebString("1 "));
                } else {
                    pagesLine.addElement(new WebTag("<a href=\"/search/" + StringEscapeUtils.escapeHtml(search) + "?page=1\">", "</a>", new WebText("1")));
                    pagesLine.addElement(new WebString(" "));
                }
                if (page > before + 2) {
                    pagesLine.addElement(new WebString("... "));
                }
                int current = page - before;
                while (before > 0 && current < maxPages) {
                    pagesLine.addElement(new WebTag("<a href=\"/search/" + StringEscapeUtils.escapeHtml(search) + "?page=" + current + "\">", "</a>",
                                                    new WebText(Integer.toString(current))));
                    pagesLine.addElement(new WebString(" "));
                    current++;
                    before--;
                }
                if (page != 1) {
                    pagesLine.addElement(new WebString(Integer.toString(page) + " "));
                }
                current = page + 1;
                int oldAfter = after;
                while (after > 0 && current < maxPages) {
                    pagesLine.addElement(new WebTag("<a href=\"/search/" + StringEscapeUtils.escapeHtml(search) + "?page=" + current + "\">", "</a>",
                                                    new WebText(Integer.toString(current))));
                    pagesLine.addElement(new WebString(" "));
                    current++;
                    after--;
                }
                if (maxPages - page > oldAfter + 1) {
                    pagesLine.addElement(new WebString("... "));
                }
                if (maxPages != 1) {
                    if (page != maxPages) {
                        pagesLine.addElement(new WebTag("<a href=\"/search/" + StringEscapeUtils.escapeHtml(search) + "?page=" + maxPages + "\">", "</a>",
                                                        new WebText(Integer.toString(maxPages))));
                    }
                }

                lines.add(pagesLine);
            }
        } else {
            WebText text = new WebText("  ");
            text.addElement(new WebTag("<a href=\"javascript:doSearch();\">", "</a>", new WebText("Do search...")));
            lines.add(text);
        }
        writeM00Page(lines, "Search", response, null);
    }

    private static String capStringLength(String str, int maxLen) {
        if (str.length() <= maxLen) {
            return str;
        }
        int before = (maxLen - 3) / 2;
        int after = str.length() - (maxLen - 3 - before);
        return str.substring(0, before) + "..." + str.substring(after);
    }

    private static String decode(String s) {
        if (s == null) {
            return s;
        }
        int n = s.length();
        if (n == 0) {
            return s;
        }
        if (s.indexOf('%') < 0) {
            return s;
        }

        // byte[] ba = new byte[n];
        StringBuffer sb = new StringBuffer(n);
        ByteBuffer bb = ByteBuffer.allocate(n);
        CharBuffer cb = CharBuffer.allocate(n);
        CharsetDecoder dec = ThreadLocalCoders.decoderFor("UTF-8").onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);

        // This is not horribly efficient, but it will do for now
        char c = s.charAt(0);
        boolean betweenBrackets = false;

        for (int i = 0; i < n;) {
            assert c == s.charAt(i); // Loop invariant
            if (c == '[') {
                betweenBrackets = true;
            } else if (betweenBrackets && c == ']') {
                betweenBrackets = false;
            }
            if (c != '%' || betweenBrackets) {
                sb.append(c);
                if (++i >= n) {
                    break;
                }
                c = s.charAt(i);
                continue;
            }
            bb.clear();
            // int ui = i;
            for (;;) {
                assert (n - i >= 2);
                bb.put(decode(s.charAt(++i), s.charAt(++i)));
                if (++i >= n) {
                    break;
                }
                c = s.charAt(i);
                if (c != '%') {
                    break;
                }
            }
            bb.flip();
            cb.clear();
            dec.reset();
            CoderResult cr = dec.decode(bb, cb, true);
            assert cr.isUnderflow();
            cr = dec.flush(cb);
            assert cr.isUnderflow();
            sb.append(cb.flip().toString());
        }

        return sb.toString();
    }

    private static int decode(char c) {
        if ((c >= '0') && (c <= '9')) {
            return c - '0';
        }
        if ((c >= 'a') && (c <= 'f')) {
            return c - 'a' + 10;
        }
        if ((c >= 'A') && (c <= 'F')) {
            return c - 'A' + 10;
        }
        assert false;
        return -1;
    }

    private static byte decode(char c1, char c2) {
        return (byte) (((decode(c1) & 0xf) << 4) | ((decode(c2) & 0xf) << 0));
    }
}