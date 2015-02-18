package net.floodlightcontroller.genicinema.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class GENICinemaWebRoutable implements RestletRoutable {

	/**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/list/json", ListGENICinemaResource.class);
        router.attach("/add/json", AddGENICinemaResource.class);
        router.attach("/view/json", ViewGENICinemaResource.class);
        router.attach("/remove/json", RemoveGENICinemaResource.class);
        router.attach("/disconnect/json", DisconnectGENICinemaResource.class);
        router.attach("/modify/json", ModifyGENICinemaResource.class);
        router.attach("/keep-alive/json", KeepAliveGENICinemaResource.class);
        return router;
    }

    /**
     * Set the base path for the GENI Cinema service
     */
    @Override
    public String basePath() {
        return "/wm/geni-cinema";
    }

}
