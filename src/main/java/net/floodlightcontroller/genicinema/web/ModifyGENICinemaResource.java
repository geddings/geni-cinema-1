package net.floodlightcontroller.genicinema.web;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
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
	public String store(String json) {
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).editChannel(json, getRequest().getClientInfo());
	}
}
