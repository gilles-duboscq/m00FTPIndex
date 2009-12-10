package com.m00ware.ftpindex.web;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.protocol.HttpContext;

import com.eaio.util.text.HumanTime;
import com.m00ware.ftpindex.FTP;
import com.m00ware.ftpindex.FilesDB;
import com.m00ware.ftpindex.web.text.WebText;

/**
 * @author Wooden
 *
 */
public class StatHttpRequestHandler extends BaseHttpRequestHandler
{
	private Tab tab;
	private long startTime;

	public StatHttpRequestHandler(WebBackEnd backEnd)
	{
		super(backEnd);
		tab = new Tab("Stats", "/stats");
		backEnd.addtab(tab);
		this.startTime = System.currentTimeMillis();
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
		lines.add(new WebText("   Indexer uptime                : "+HumanTime.approximately(System.currentTimeMillis() - this.startTime)));
		List<FTP> ftps = this.getBackEnd().getDb().getFTPs();
		long totalSize = 0;
		for(FTP ftp : ftps)
			totalSize += ftp.getRoot().getSize();
		Map<String, Object> stats = this.getBackEnd().getDb().getStats();
		lines.add(new WebText("   Number of FTPs                : "+ftps.size()));
		lines.add(new WebText("   Total size of indexed content : "+FTP.formatSize(totalSize)));
		Long dbFileSize = (Long)stats.get(FilesDB.STAT_DBFILE_SIZE);
		if(dbFileSize != null)
			lines.add(new WebText("   DB file size                  : "+FTP.formatSize(dbFileSize)));
		writeM00Page(lines, "Stats", response, this.tab);
	}

}
