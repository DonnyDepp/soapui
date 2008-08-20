/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.rest.panels.request.views.json;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.sf.json.JSONObject;

import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.panels.request.AbstractRestRequestDesktopPanel.RestResponseDocument;
import com.eviware.soapui.impl.rest.panels.request.AbstractRestRequestDesktopPanel.RestResponseMessageEditor;
import com.eviware.soapui.impl.support.AbstractHttpRequest;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.editor.views.AbstractXmlEditorView;
import com.eviware.soapui.support.xml.JXEditTextArea;

@SuppressWarnings("unchecked")
public class RestJsonResponseView extends AbstractXmlEditorView<RestResponseDocument> implements PropertyChangeListener
{
	private final RestRequest restRequest;
	private JPanel contentPanel;
	private JXEditTextArea contentEditor;
	private boolean updatingRequest;
	private JPanel panel;

	public RestJsonResponseView(RestResponseMessageEditor restRequestMessageEditor, RestRequest restRequest)
	{
		super( "JSON", restRequestMessageEditor, RestJsonResponseViewFactory.VIEW_ID );
		this.restRequest = restRequest;
		
		restRequest.addPropertyChangeListener( this );
	}

	public JComponent getComponent()
	{
		if( panel == null )
		{
			panel = new JPanel( new BorderLayout() );
			
			panel.add( buildToolbar(), BorderLayout.NORTH );
			panel.add( buildContent(), BorderLayout.CENTER );
			panel.add( buildStatus(), BorderLayout.SOUTH );
		}
		
		return panel;
	}

	@Override
	public void release()
	{
		super.release();
		
		restRequest.removePropertyChangeListener( this );
	}

	private Component buildStatus()
	{
		return new JPanel();
	}

	private Component buildContent()
	{
		contentPanel = new JPanel( new BorderLayout() );
		
		contentEditor = JXEditTextArea.createJavaScriptEditor();
		HttpResponse response = restRequest.getResponse();
		if( response != null)
			setEditorContent(response);
		
		contentPanel.add( new JScrollPane( contentEditor ));
		contentEditor.setEditable( false );
		
		return contentPanel;
	}

	protected void setEditorContent(HttpResponse httpResponse)
	{
		if( httpResponse.getContentType().contains("javascript"))
		{
			String content = httpResponse.getContentAsString();
			
			try
			{
				JSONObject json = JSONObject.fromObject(httpResponse.getContentAsString());
				content = json.toString(3);
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}
			
			contentEditor.setText( content );
		}
		else
		{
			contentEditor.setText( "" );
		}
	}

	private Component buildToolbar()
	{
		JXToolBar toolbar = UISupport.createToolbar();
		
		return toolbar;
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		if( evt.getPropertyName().equals( AbstractHttpRequest.RESPONSE_PROPERTY ) && !updatingRequest )
		{
			setEditorContent( ((HttpResponse)evt.getNewValue()) );
		}
	}

	@Override
	public void setXml(String xml)
	{
	}

	public boolean saveDocument(boolean validate)
	{
		return false;
	}

	public void setEditable(boolean enabled)
	{
	}
}