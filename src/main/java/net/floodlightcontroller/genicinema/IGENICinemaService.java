package net.floodlightcontroller.genicinema;

import java.util.Map;

import org.restlet.data.ClientInfo;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * A live video streaming service for the GENI community.
 * Clients make requests as defined by the functions in 
 * this interface via Floodlight's REST API. All requests
 * should be provided in JSON format and all responses will
 * be returned to the requester as a JSON string.
 * 
 * Clients should know how to parse the returned string to
 * attain the desired information or to get the information
 * within to start a new upload/download connection.
 * 
 * @author Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 *
 */
public interface IGENICinemaService extends IFloodlightService {
	
	/**
	 * Get all available channels in JSON format
	 * @return The message to be sent back to the client
	 */
	public String getChannels();
	
	/**
	 * Add a new channel from a JSON string
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public Map<String, String> addChannel(String json, ClientInfo clientInfo);
	
	/**
	 * Remove an existing channel defined by
	 * the JSON string provided
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public String removeChannel(String json, ClientInfo clientInfo);
	
	/**
	 * Request to watch a channel defined by
	 * the JSON string provided
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public String watchChannel(String json, ClientInfo clientInfo);
	
	/**
	 * Request to edit a channel defined by
	 * the JSON string provided
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public String editChannel(String json, ClientInfo clientInfo);
}
