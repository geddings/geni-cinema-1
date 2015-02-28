package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
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

public class ModifyGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(ModifyGENICinemaResource.class);

	/**
	 * Takes a Static Flow Pusher string in JSON format and parses it into
	 * our database schema then pushes it to the database.
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	@LogMessageDoc(level="ERROR",
	message="Error parsing push flow mod request: {request}",
	explanation="An invalid request was sent to static flow pusher",
	recommendation="Fix the format of the static flow mod request")
	public Map<String, String> store(String json) {	
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu"));
	    
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).editChannel(json, getRequest().getClientInfo());
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
