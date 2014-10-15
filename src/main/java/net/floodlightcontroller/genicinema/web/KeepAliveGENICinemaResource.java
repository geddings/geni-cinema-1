package net.floodlightcontroller.genicinema.web;

import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeepAliveGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(KeepAliveGENICinemaResource.class);

	@Post
	public Map<String, String> keepAlive(String json) {
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).clientKeepAlive(json, getRequest().getClientInfo());
	}
}
