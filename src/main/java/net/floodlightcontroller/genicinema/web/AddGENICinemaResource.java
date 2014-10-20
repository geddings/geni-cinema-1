package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.data.Form;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.representation.Representation;
import org.restlet.resource.Options;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(AddGENICinemaResource.class);

	/**
	 * Takes a GENI Cinema add-video-feed request in JSON format,
	 * parses it, modifies the GENI Cinema configuration to support
	 * the new video feed, and returns back to the sender information
	 * needed to start streaming the video:
	 * 
	 * REQUEST STRING
	 * "name" : "name-to-give-video"
	 * "description" : "description-of-video-content"
	 * "view-password" : "optional-password-to-view-video"
	 * "admin-password" : "required-password-to-modify-video-later"
	 * "my-ip" : "the-client-ip-address" //TODO won't be relevant if behind NAT...
	 * "my-port" : "the-client-tp-port"
	 * "my-protocol" : "the-tp-protocol-to-use"
	 * 
	 * RESPONSE STRING
	 * "name" : "acked-name"
	 * "gw-ip" : "the-gateway-ip-address"
	 * "gw-port" : "the-gateway-tp-port"
	 * "gw-protocol" : "the-tp-protocol-to-use"
	 * "id" : "a-unique-identifier-representing-this-video"
	 * 
	 * @param fmJson The Static Flow Pusher entry in JSON format.
	 * @return A string status message
	 */
	@Post
	public Map<String, String> parseAddRequest(String json) {
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers"); 
	    getResponse().getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
	    if (responseHeaders == null) {
	    	responseHeaders = new Series(Header.class);
	    	getResponse().getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, responseHeaders);
	    }
	    responseHeaders.add(new Header("Access-Control-Allow-Origin", "http://myweb.clemson.edu")); 

		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).addChannel(json, getRequest().getClientInfo());
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
	    responseHeaders.add(new Header("Access-Control-Allow-Methods", "POST,GET,OPTIONS"));
	    responseHeaders.add(new Header("Access-Control-Allow-Headers", "Content-Type")); 
	    responseHeaders.add(new Header("Access-Control-Allow-Credentials", "false")); 
	    responseHeaders.add(new Header("Access-Control-Max-Age", "60")); 
	} 	
}
