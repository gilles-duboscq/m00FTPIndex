package com.m00ware.ftpindex.web;

/**
 * @author Wooden
 * 
 */
public class WebException extends RuntimeException {
    private static final long serialVersionUID = -4459512399472074174L;

    public WebException(String message) {
        super(message);
    }
}