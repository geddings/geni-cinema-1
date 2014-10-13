package net.floodlightcontroller.genicinema.web;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Delete;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(RemoveGENICinemaResource.class);
	
	@Delete
	@LogMessageDoc(level="ERROR",
	message="Error deleting flow mod request: {request}",
	explanation="An invalid delete request was sent to static flow pusher",
	recommendation="Fix the format of the static flow mod request")
	public String parseRemoveRequest(String json) {
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).removeChannel(json, getRequest().getClientInfo());
	}
}
