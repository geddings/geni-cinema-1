package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.data.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.representation.Representation;
import org.restlet.resource.Options;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisconnectGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(DisconnectGENICinemaResource.class);

	@SuppressWarnings("unchecked")
	@Post
	public Map<String, String> clientDisconnect(String json) {
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series<Header>(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu"));
	    
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).clientDisconnect(json, getRequest().getClientInfo());
	}
	
	@Options
	public void doOptions(Representation entity) {
	    @SuppressWarnings("unchecked")
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series<Header>(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Methods", "POST,OPTIONS"));
	    responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Credentials", "false")); 
	    responseHeaders.add(new Header("Access-Control-Max-Age", "60")); 
	} 
}
