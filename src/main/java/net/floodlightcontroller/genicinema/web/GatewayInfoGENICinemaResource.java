package net.floodlightcontroller.genicinema.web;

import java.util.ArrayList;
import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.data.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Options;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayInfoGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(GatewayInfoGENICinemaResource.class);

	@SuppressWarnings("unchecked")
	@Get
	public Map<String, ArrayList<String>> getChannels() {
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series<Header>(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu"));
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).getGatewayInfo();
	}
	
	@SuppressWarnings("unchecked")
	@Options
	public void doOptions(Representation entity) {
	    Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series<Header>(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Methods", "GET,OPTIONS"));
	    responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Credentials", "false")); 
	    responseHeaders.add(new Header("Access-Control-Max-Age", "60")); 
	} 
}
