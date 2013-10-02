package com.m00ware.ftpindex.web;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.protocol.*;

import com.m00ware.ftpindex.web.text.*;

/**
 * @author Wooden
 * 
 */
public abstract class BaseHttpRequestHandler implements HttpRequestHandler {
    private WebBackEnd backEnd;
    private Random random;

    public BaseHttpRequestHandler(WebBackEnd backEnd) {
        this.backEnd = backEnd;
        this.random = new Random();
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        try {
            doHandle(request, response, context);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw ioe;
        } catch (HttpException he) {
            throw he;
        } catch (WebException we) {
            try {
                List<WebText> content = new LinkedList<WebText>();
                content.add(new WebText(""));
                content.add(new WebText("   Error :"));
                content.add(new WebText("    " + we.getMessage()));
                this.writeM00Page(content, "Error", response, null);
            } catch (Exception e) {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                // display something
                e.printStackTrace();
            }
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            // display something
            e.printStackTrace();
        }
    }

    public abstract void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException;

    public WebBackEnd getBackEnd() {
        return backEnd;
    }

    public void writeM00Page(List<WebText> content, String pageTitle, HttpResponse response, Tab selectedTab) {
        int pageWidth = this.backEnd.getPageWidth();
        int minPageHeight = this.backEnd.getMinPageHeight();
        String webName = this.backEnd.getWebName();
        Tab[] tabs = this.backEnd.getTabs().toArray(new Tab[0]);
        StringBuilder sb = new StringBuilder();
        String[] quotes = this.backEnd.getQuotes();
        String quote = "";
        if (quotes != null && quotes.length > 0) {
            quote = quotes[random.nextInt(quotes.length)];
        }
        sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>\n" + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" + "\t<head>\n" + "\t\t<title>");
        sb.append(pageTitle).append(" | ").append(webName);
        sb.append("</title>\n" + "\t\t<style type=\"text/css\">\n" + "body{\n" + "\tbackground-color: #000000;\n" + "\tcolor: #8f8f8f;\n" + "}\n" + "a{\n" + "\tcolor: #8f8f8f;\n" + "}\n" + "a.nou{\n" + "\tcolor: #8f8f8f;\n" + "\ttext-decoration : none;\n" + "}\n" + ".white{\n" + "\tcolor : #ffffff;\n" + "}\n" + "\t\t\t\t</style>\n" + "\t\t\t\t<script type=\"text/javascript\">\n" + "function doSearch(){\n" + "var search = prompt('Search for...','');\n" + "if(search){document.location.href+=search;}\n" + "}\n" + "\t\t\t\t</script>\n" + "\t\t\t</head>\n" + "\t\t\t<body>\n" + "\t\t\t\t<table width=100% height=100%>\n" + "\t\t\t\t\t<TR>\n" + "\t\t\t\t\t\t<TD align=center>\n" + "\t\t\t\t\t\t\t<pre>");
        int before = (pageWidth + 2 - webName.length()) / 2;
        for (int i = 0; i < before; i++) {
            sb.append(' ');
        }
        sb.append("<span class=\"white\">").append(webName).append("</span>");
        int after = pageWidth + 2 - webName.length() - before;
        for (int i = 0; i < after; i++) {
            sb.append(' ');
        }
        int dash = pageWidth;
        sb.append("\n/");
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Tab nextTab = null;
            if (i + 1 < tabs.length) {
                nextTab = tabs[i + 1];
            }
            if (tab == selectedTab) {
                sb.append("<span class=\"white\">*");
                dash -= 1;
            } else {
                sb.append("<a href=\"").append(tab.getHref() + "\">");
            }
            sb.append(tab.getName());
            dash -= tab.getName().length();
            if (tab == selectedTab) {
                sb.append("</span> ");
            } else {
                sb.append("</a> ");
            }
            dash -= 1;
            if (nextTab != null && nextTab == selectedTab) {
                sb.append('/');
            } else {
                sb.append('\\');
            }
            dash -= 1;
        }
        for (int i = 0; i < dash; i++) {
            sb.append('-');
        }
        sb.append("\\\n");
        int lines = 0;
        for (WebText line : content) {
            for (WebText subLine : this.doLineWrap(line, pageWidth)) {
                sb.append('|').append(subLine.toString());
                for (int i = subLine.getLength(); i < pageWidth; i++) {
                    sb.append(' ');
                }
                sb.append("|\n");
                lines++;
            }
        }
        if (lines < minPageHeight) {
            for (int i = 0; i < minPageHeight - lines; i++) {
                sb.append('|');
                for (int j = 0; j < pageWidth; j++) {
                    sb.append(' ');
                }
                sb.append("|\n");
            }
        }
        sb.append('\\');
        if (quote.trim().length() > 0) {
            before = (pageWidth - quote.length() - 2) / 2;
            for (int i = 0; i < before; i++) {
                sb.append('-');
            }
            sb.append(' ').append(quote).append(' ');
            after = pageWidth - quote.length() - 2 - before;
            for (int i = 0; i < after; i++) {
                sb.append('-');
            }
        } else {
            for (int i = 0; i < pageWidth; i++) {
                sb.append('-');
            }
        }

        sb.append("/</pre>\n" + "\t\t\t\t</td>\n" + "\t\t\t</tr>\n" + "\t\t</table>\n" + "\t</body>\n" + "</html>");

        try {
            NStringEntity entity = new NStringEntity(sb.toString(), "UTF8");
            entity.setContentType("text/html; charset=UTF-8");
            response.setEntity(entity);
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
        }
    }

    private List<WebText> doLineWrap(WebText line, int width) {
        List<WebText> lines = new LinkedList<WebText>();
        int len = line.getLength();
        int start = getWhiteSpaceLength(line);
        boolean firstIt = true;
        String ident = "   ";
        while (line.hasFirst() && len > width) {
            WebText newLine = new WebText();
            if (!firstIt) {
                newLine.addElement(new WebString(ident));
            }
            int rem = width;
            WebTextElements first = line.getFirst();
            if (first instanceof WebString) {
                WebString broken = ((WebString) first).breakFor(rem);
                if (broken != null) {
                    line.addFirst(first);
                    first = broken;
                }
            }
            int firstLen = first.getLength();
            while (line.hasFirst() && rem - firstLen >= 0) {
                rem -= firstLen;
                len -= firstLen;
                newLine.addElement(first);
                line.removeFirst();
                if (line.hasFirst() && rem > 0) {
                    first = line.getFirst();
                    if (first instanceof WebString) {
                        WebString broken = ((WebString) first).breakFor(rem);
                        if (broken != null) {
                            line.addFirst(first);
                            first = broken;
                        }
                    }
                    firstLen = first.getLength();
                }
            }
            lines.add(newLine);
            if (firstIt) {
                int dec = start - 2;
                if (dec < 0) {
                    dec = 0;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < dec; i++) {
                    sb.append(" ");
                }
                ident = sb.toString();
                width -= dec;
            }
            firstIt = false;
        }
        if (!firstIt) {
            line.addFirst(new WebString(ident));
        }
        lines.add(line);
        return lines;
    }

    private int getWhiteSpaceLength(WebText text) {
        return getWhiteSpaceLength(text, new MutableBoolean(false));
    }

    private int getWhiteSpaceLength(WebText text, MutableBoolean mb) {
        int len = 0;
        for (WebTextElements element : text.getElements()) {
            if (element instanceof WebString) {
                int idx = 0;
                String str = element.toString();
                while (idx < str.length() && str.charAt(idx) == ' ') {
                    idx++;
                    len++;
                }
                if (idx < str.length()) {
                    mb.b = true;
                }
            } else if (element instanceof WebTag) {
                len += getWhiteSpaceLength(((WebTag) element).getText(), mb);
            }
            if (mb.b) {
                break;
            }
        }
        return len;
    }

    private static class MutableBoolean {
        public MutableBoolean(boolean b) {
            this.b = b;
        }

        private boolean b;
    }
}