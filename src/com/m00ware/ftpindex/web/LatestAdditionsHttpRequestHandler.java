package com.m00ware.ftpindex.web;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.protocol.HttpContext;

import com.m00ware.ftpindex.DBEventListener;
import com.m00ware.ftpindex.Directory;
import com.m00ware.ftpindex.File;
import com.m00ware.ftpindex.Node;
import com.m00ware.ftpindex.web.text.WebText;

/**
 * @author Wooden
 *
 */
public class LatestAdditionsHttpRequestHandler extends BaseHttpRequestHandler
{
	private Tab tab;
	private Queue<Node> latest;
	private int maxLatest = 50;

	public LatestAdditionsHttpRequestHandler(WebBackEnd backEnd)
	{
		super(backEnd);
		this.tab = new Tab("Latest Additions", "/latest/");
		this.getBackEnd().addtab(this.tab);
		this.getBackEnd().getDb().registerEventListener(new LatestDBEventListener());
		this.latest = new LinkedList<Node>();
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
		lines.add(new WebText("Last "+maxLatest+" additions :"));
		
		this.writeM00Page(lines, "Latest Additions", response, this.tab);
	}
	
	public int getMaxLatest()
	{
		return maxLatest;
	}
	
	public void setMaxLatest(int maxLatest)
	{
		this.maxLatest = maxLatest;
	}
	
	private class LatestDBEventListener extends DBEventListener{
		@Override
		public void newFile(File file)
		{
			this.addNode(file);
		}
		
		@Override
		public void newDirectory(Directory directory)
		{
			this.addNode(directory);
		}

		private void addNode(Node node){
			synchronized(latest)
			{
				if(latest.size() > maxLatest){
					latest.peek();
				}
				latest.offer(node);
			}
		}
	}

}
