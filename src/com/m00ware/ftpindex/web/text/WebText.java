package com.m00ware.ftpindex.web.text;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Wooden
 *
 */
public class WebText
{
	private List<WebTextElements> elements;
	
	public WebText(String text)
	{
		this.elements = new LinkedList<WebTextElements>();
		this.addElement(new WebString(text));
	}
	
	public WebText()
	{
		this.elements = new LinkedList<WebTextElements>();
	}
	
	public void addElement(WebTextElements element){
		this.elements.add(element);
	}
	
	public List<WebTextElements> getElements()
	{
		return elements;
	}
	
	public boolean hasFirst()
	{
		return !this.elements.isEmpty();
	}
	
	public WebTextElements removeFirst()
	{
		return this.elements.remove(0);
	}
	
	public WebTextElements getFirst()
	{
		return this.elements.get(0);
	}
	
	public void addFirst(WebTextElements element)
	{
		this.elements.add(0, element);
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		for(WebTextElements element : this.elements){
			sb.append(element.toString());
		}
		return sb.toString();
	}

	public int getLength()
	{
		int len = 0;
		for(WebTextElements element : this.elements){
			len += element.getLength();
		}
		return len;
	}
}
