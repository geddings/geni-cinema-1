package net.floodlightcontroller.genicinema.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.genicinema.IGENICinemaService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

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
	
	/**
	 * Turns a JSON formatted Static Flow Pusher string into a storage entry
	 * Expects a string in JSON along the lines of:
	 *        {
	 *            "switch":       "AA:BB:CC:DD:EE:FF:00:11",
	 *            "name":         "flow-mod-1",
	 *            "cookie":       "0",
	 *            "priority":     "32768",
	 *            "ingress-port": "1",
	 *            "actions":      "output=2",
	 *        }
	 * @param fmJson The JSON formatted static flow pusher entry
	 * @return The map of the storage entry
	 * @throws IOException If there was an error parsing the JSON
	 */
	public static Map<String, Object> jsonToStorageEntry(String fmJson) throws IOException {
		Map<String, Object> entry = new HashMap<String, Object>();
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		
		String tpSrcPort = "";
		String tpDstPort = "";
		String ipProto = "";

		try {
			jp = f.createJsonParser(fmJson);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}

			String n = jp.getCurrentName();
			jp.nextToken();
			if (jp.getText().equals("")) {
				continue;
			}

			// Java 7 switch-case on strings automatically checks for (deep) string equality.
			// IMHO, this makes things easier on the eyes than if, else if, else's, and it
			// seems to be more efficient than walking through a long list of if-else-ifs

			// A simplification is to make the column names the same strings as those used to
			// compose the JSON flow entry; keeps all names/keys centralized and reduces liklihood
			// for future string errors.
			switch (n) {
			case StaticFlowEntryPusher.COLUMN_NAME:
				entry.put(StaticFlowEntryPusher.COLUMN_NAME, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SWITCH:
				entry.put(StaticFlowEntryPusher.COLUMN_SWITCH, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TABLE_ID:
				entry.put(StaticFlowEntryPusher.COLUMN_TABLE_ID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ACTIVE:
				entry.put(StaticFlowEntryPusher.COLUMN_ACTIVE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT: // TODO @Ryan always store TO's, but conditionally push them (the conditional push hasn't been done yet)
				entry.put(StaticFlowEntryPusher.COLUMN_IDLE_TIMEOUT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT:
				entry.put(StaticFlowEntryPusher.COLUMN_HARD_TIMEOUT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_PRIORITY:
				entry.put(StaticFlowEntryPusher.COLUMN_PRIORITY, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_COOKIE: // set manually, or computed from name
				entry.put(StaticFlowEntryPusher.COLUMN_COOKIE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_IN_PORT:
				entry.put(StaticFlowEntryPusher.COLUMN_IN_PORT, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_VLAN:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_VLAN_PCP, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_DL_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_DL_TYPE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_TOS: // only valid for OF1.0; all other should specify specifics (ECN and/or DSCP bits)
				entry.put(StaticFlowEntryPusher.COLUMN_NW_TOS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_ECN:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_ECN, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_DSCP:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DSCP, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_PROTO:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_PROTO, jp.getText());
				ipProto = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_NW_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_NW_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_NW_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SCTP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_SCTP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_UDP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_UDP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TCP_SRC:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_SRC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TCP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_DST, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TP_SRC: // support for OF1.0 generic transport ports
				entry.put(StaticFlowEntryPusher.COLUMN_TP_SRC, jp.getText());
				tpSrcPort = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_TP_DST:
				entry.put(StaticFlowEntryPusher.COLUMN_TP_DST, jp.getText());
				tpDstPort = jp.getText();
				break;
			case StaticFlowEntryPusher.COLUMN_ICMP_TYPE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_TYPE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ICMP_CODE:
				entry.put(StaticFlowEntryPusher.COLUMN_ICMP_CODE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_OPCODE:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_OPCODE, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_SHA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SHA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_DHA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DHA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_SPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_SPA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ARP_DPA:
				entry.put(StaticFlowEntryPusher.COLUMN_ARP_DPA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_MPLS_LABEL:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_LABEL, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_MPLS_TC:
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_TC, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_MPLS_BOS: // not supported as match in loxi right now
				entry.put(StaticFlowEntryPusher.COLUMN_MPLS_BOS, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_METADATA:
				entry.put(StaticFlowEntryPusher.COLUMN_METADATA, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_TUNNEL_ID: // not supported as match in loxi right now
				entry.put(StaticFlowEntryPusher.COLUMN_TUNNEL_ID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_PBB_ISID: // not supported as match in loxi right now
				entry.put(StaticFlowEntryPusher.COLUMN_PBB_ISID, jp.getText());
				break;
			case StaticFlowEntryPusher.COLUMN_ACTIONS:
				entry.put(StaticFlowEntryPusher.COLUMN_ACTIONS, jp.getText());
				break;
			default:
				log.error("Could not decode field from JSON string: {}", n);
			}  
		} 
		
		// For OF1.0, transport ports are specified using generic tp_src, tp_dst type strings.
		// Once the whole json string has been parsed, find out the IpProto to properly assign the ports.
		// If IpProto not specified, print error, and make sure all TP columns are clear.
		if (ipProto.equalsIgnoreCase("tcp")) {
			if (!tpSrcPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_SRC, tpSrcPort);
			}
			if (!tpDstPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_TCP_DST, tpDstPort);
			}
		} else if (ipProto.equalsIgnoreCase("udp")) {
			if (!tpSrcPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_SRC, tpSrcPort);
			}
			if (!tpDstPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_UDP_DST, tpDstPort);
			}
		} else if (ipProto.equalsIgnoreCase("sctp")) {
			if (!tpSrcPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_SRC);
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_SRC, tpSrcPort);
			}
			if (!tpDstPort.isEmpty()) {
				entry.remove(StaticFlowEntryPusher.COLUMN_TP_DST);
				entry.put(StaticFlowEntryPusher.COLUMN_SCTP_DST, tpDstPort);
			}
		} else {
			log.debug("Got IP protocol of '{}' and tp-src of '{}' and tp-dst of '" + tpDstPort + "' via SFP REST API", ipProto, tpSrcPort);
		}

		return entry;
	}   
	
}
