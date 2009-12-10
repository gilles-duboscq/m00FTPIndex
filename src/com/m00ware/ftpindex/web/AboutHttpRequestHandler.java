package com.m00ware.ftpindex.web;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.protocol.HttpContext;

import com.m00ware.ftpindex.web.text.WebText;

/**
 * @author Wooden
 *
 */
public class AboutHttpRequestHandler extends BaseHttpRequestHandler
{
	private Tab tab;

	public AboutHttpRequestHandler(WebBackEnd backEnd)
	{
		super(backEnd);
		tab = new Tab("About", "/about");
		backEnd.addtab(tab);
	}

	/* (non-Javadoc)
	 * @see com.m00ware.ftpindex.web.BaseHttpRequestHandler#doHandle(org.apache.http.HttpRequest, org.apache.http.HttpResponse, org.apache.http.protocol.HttpContext)
	 */
	@Override
	public void doHandle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
			IOException
	{
		if(!request.getRequestLine().getMethod().equals("GET"))
			throw new MethodNotSupportedException(request.getRequestLine().getMethod()+" : Method not supported (->You could try a mighty GET huh?");
		List<WebText> lines = new LinkedList<WebText>();
		
		lines.add(new WebText(""));
		lines.add(new WebText("     m00FTPIndex"));
		lines.add(new WebText(""));
		lines.add(new WebText("    This m00FTPIndex server searches for FTP servers with anonymous access within the "+this.getBackEnd().getScanner().getRange()+" IP range and indexes their content. Content may have changed since the FTPs were last indexed."));
		lines.add(new WebText(""));
		lines.add(new WebText("    To run a search just go to /search/XXX where XXX is your search. You may use more than one search term, results will be sorted according to the number of terms found and to their online/offline status. To avoid a term containing whitespaces to be split into multiple terms just use the usual \"multiple term\" notation."));
		lines.add(new WebText(""));
		lines.add(new WebText("   A few flags you'll see around :"));
		lines.add(new WebText("    + : An online directory (or FTP on the FTP page)"));
		lines.add(new WebText("    X : An offline directory (or FTP on the FTP page)"));
		lines.add(new WebText("    - : An online file"));
		lines.add(new WebText("    x : An offline file"));
		lines.add(new WebText("    ! : This file or directory is suspicious"));
		lines.add(new WebText("    # : This file or directory may have moved of been deleted"));
		lines.add(new WebText(""));
		lines.add(new WebText("   (c) m00ware/Wooden - 2009"));
		
		this.writeM00Page(lines, "About", response, tab);
	}

}
