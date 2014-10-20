package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.representation.Representation;
import org.restlet.resource.Options;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(ViewGENICinemaResource.class);

	/**
	 * Takes a GENI Cinema view-video-feed request in JSON format,
	 * parses it, modifies the GENI Cinema configuration to add
	 * this client as a viewer, and returns back to the sender information
	 * needed to start viewing the video:
	 * 
	 * REQUEST STRING
	 * "name" : "name-of-video-to-watch"
	 * "id" : "a-unique-identifier-representing-this-video"
	 * "view-password" : "optional-password-to-view-video"
	 * "my-protocol" : "the-tp-protocol-to-use"
	 * 
	 * RESPONSE STRING
	 * "name" : "acked-name"
	 * "id" : "acked-id"
	 * "gw-ip" : "the-gateway-ip-address"
	 * "gw-port" : "the-gateway-tp-port"
	 * "gw-protocol" : "the-tp-protocol-to-use"
	 * 
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	public Map<String, String> parseViewRequest(String json) {
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu"));
	    
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).watchChannel(json, getRequest().getClientInfo());
	}
	
	@Options
	public void doOptions(Representation entity) {
	    Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Methods", "POST,OPTIONS"));
	    responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Credentials", "false")); 
	    responseHeaders.add(new Header("Access-Control-Max-Age", "60")); 
	} 
}
