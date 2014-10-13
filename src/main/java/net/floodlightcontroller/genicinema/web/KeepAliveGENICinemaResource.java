package net.floodlightcontroller.genicinema.web;

import net.floodlightcontroller.genicinema.IGENICinemaService;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeepAliveGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(KeepAliveGENICinemaResource.class);

	@Get
	public String getChannels() {
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).getChannels();
	}
}
