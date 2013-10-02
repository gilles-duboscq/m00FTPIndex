package com.m00ware.ftpindex.web.text;

/**
 * @author Wooden
 * 
 */
public class WebTag implements WebTextElements {
    private String start;
    private String end;
    private WebText text;

    public WebTag(String start, String end) {
        this(start, end, new WebText());
    }

    public WebTag(String start, String end, WebText text) {
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public String getEnd() {
        return end;
    }

    public String getStart() {
        return start;
    }

    public WebText getText() {
        return text;
    }

    @Override
    public int getLength() {
        return this.text.getLength();
    }

    @Override
    public String toString() {
        return this.start + this.text + this.end;
    }
}