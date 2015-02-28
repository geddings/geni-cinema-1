package net.floodlightcontroller.genicinema;

import java.util.ArrayList;
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
 * All IGENICinemaService methods are synchronized to ensure
 * data integrity. There is so little that can be done in an
 * unsynchronized manner without a separate copy of data for
 * reading only. TODO
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
	 * Get all Ingress and Egress public IPs in JSON format
	 * @return The message to be sent back to the client
	 */
	public Map<String, ArrayList<String>> getGatewayInfo();
	
	/**
	 * Get all available channels in JSON format
	 * @return The message to be sent back to the client
	 */
	public ArrayList<Map<String, String>> getChannels();
	
	/**
	 * Add a new channel from a JSON string.
	 * Specify a null ClientInfo to set up the default/splash Channel.
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
	public Map<String, String> removeChannel(String json, ClientInfo clientInfo);
	
	/**
	 * Request to watch a channel defined by
	 * the JSON string provided
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public Map<String, String> watchChannel(String json, ClientInfo clientInfo);
	
	/**
	 * Client gracefully disconnects.
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public Map<String, String> clientDisconnect(String json, ClientInfo clientInfo);
	
	/**
	 * Request to edit a channel defined by
	 * the JSON string provided
	 * @param json, the string from the client
	 * @param clientInfo, info on the client from restlet
	 * @return The message to be sent back to the client
	 */
	public Map<String, String> editChannel(String json, ClientInfo clientInfo);

}
