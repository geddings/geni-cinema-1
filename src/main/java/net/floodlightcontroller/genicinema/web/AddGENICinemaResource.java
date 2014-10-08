package net.floodlightcontroller.genicinema.web;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
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
	public String parseAddRequest(String json) {
		
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class)).addChannel(json, getRequest().getClientInfo());
	}
}
