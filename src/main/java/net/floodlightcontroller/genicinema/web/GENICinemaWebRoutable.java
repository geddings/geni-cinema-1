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
        router.attach("/list-gateways/json", GatewayInfoGENICinemaResource.class);
        router.attach("/add-channel/json", AddGENICinemaResource.class);
        router.attach("/modify-channel/json", ModifyGENICinemaResource.class);
        router.attach("/remove-channel/json", RemoveGENICinemaResource.class);
        router.attach("/list-channels/json", ListGENICinemaResource.class);
        router.attach("/watch-channel/json", ViewGENICinemaResource.class);
        router.attach("/remove-client/json", DisconnectGENICinemaResource.class);
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
