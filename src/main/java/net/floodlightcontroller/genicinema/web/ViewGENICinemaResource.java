package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
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
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).watchChannel(json, getRequest().getClientInfo());
	}
}
