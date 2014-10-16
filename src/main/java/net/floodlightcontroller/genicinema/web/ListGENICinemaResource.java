package net.floodlightcontroller.genicinema.web;

import java.util.ArrayList;
import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListGENICinemaResource extends ServerResource {
	protected static Logger log = LoggerFactory.getLogger(ListGENICinemaResource.class);

	@Get
	public ArrayList<Map<String, String>> getChannels() {
		return ((IGENICinemaService) getContext().getAttributes().get(IGENICinemaService.class.getCanonicalName())).getChannels();
	}
}
