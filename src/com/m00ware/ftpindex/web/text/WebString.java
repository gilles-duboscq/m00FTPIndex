package com.m00ware.ftpindex.web.text;

import org.apache.commons.lang.StringEscapeUtils;

/**
 * @author Wooden
 * 
 */
public class WebString implements WebTextElements {
    private String string;

    public WebString(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    @Override
    public int getLength() {
        return StringEscapeUtils.unescapeHtml(this.string).length();
    }

    @Override
    public String toString() {
        return string;
    }

    public WebString breakFor(int rem) {
        if (this.string.length() <= rem) {
            return null;
        }
        int breakIdx = rem - 1;
        while (breakIdx > 0 && this.string.charAt(breakIdx) != ' ') {
            breakIdx--;
        }
        if (breakIdx == 0) {
            // System.out.println("Unbreakable big string : "+this.string);
            return null;
        }
        WebString broken = new WebString(this.string.substring(0, breakIdx + 1));
        this.string = this.string.substring(breakIdx + 1);
        return broken;
    }
}