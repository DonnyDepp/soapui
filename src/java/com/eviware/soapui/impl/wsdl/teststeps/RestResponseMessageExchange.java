package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.RestResource;
import com.eviware.soapui.impl.wsdl.submit.AbstractRestMessageExchange;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.model.iface.Attachment;
import com.eviware.soapui.model.iface.Operation;
import com.eviware.soapui.support.types.StringToStringMap;

public class RestResponseMessageExchange extends AbstractRestMessageExchange<RestRequest>
{
	private HttpResponse response;
	private String requestContent;

	public RestResponseMessageExchange(RestRequest request)
	{
		super(request);
		
		response = request.getResponse();
	}

	public String getRequestContent()
	{
		if( requestContent != null )
			return requestContent;
		
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? getModelItem().getRequestContent() : response.getRequestContent(); 
	}

	public StringToStringMap getRequestHeaders()
	{
		return response == null ? getModelItem().getRequestHeaders() : response.getRequestHeaders(); 
	}
	
	public Attachment[] getRequestAttachments()
	{
		return getModelItem().getAttachments();
	}

	public Attachment[] getResponseAttachments()
	{
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? null : response.getAttachments();
	}

	public String getResponseContent()
	{
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? null : response.getContentAsString();
	}

	public StringToStringMap getResponseHeaders()
	{
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? null : response.getResponseHeaders();
	}

	public long getTimeTaken()
	{
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? 0 : response.getTimeTaken();
	}

	public long getTimestamp()
	{
		if( response == null )
			response = getModelItem().getResponse();
		
		return response == null ? 0 : response.getTimestamp();
	}

	public void setRequestContent( String requestContent )
	{
		this.requestContent = requestContent;
	}

	public boolean isDiscarded()
	{
		return false;
	}

	@Override
	public RestResource getResource()
	{
		return getModelItem().getResource();
	}

	public Operation getOperation()
	{
		return getResource();
	}

}
