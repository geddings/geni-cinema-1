package net.floodlightcontroller.genicinema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowModify;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupModify;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.restlet.data.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.genicinema.Channel.ChannelBuilder;
import net.floodlightcontroller.genicinema.EgressStream.EgressStreamBuilder;
import net.floodlightcontroller.genicinema.IngressStream.IngressStreamBuilder;
import net.floodlightcontroller.genicinema.VLCStreamServer.VLCStreamServerBuilder;
import net.floodlightcontroller.genicinema.VideoSocket.VideoSocketBuilder;
import net.floodlightcontroller.genicinema.web.GENICinemaWebRoutable;
import net.floodlightcontroller.genicinema.web.JsonStrings;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.FlowModUtils;

public class GENICinemaManager implements IFloodlightModule, IOFSwitchListener, IOFMessageListener, IGENICinemaService {

	/* 
	 * Services 
	 */

	private static IOFSwitchService switchService;
	private static IRestApiService restApiService;
	private static Logger log;

	/*
	 * Class variables
	 */

	/* All available Channels per aggregate per VLC Server */
	private static Map<String, ArrayList<Channel>> channelsPerAggregate;

	/* Ongoing Egress Streams */
	private static Map<String, ArrayList<EgressStream>> egressStreamsPerAggregate;

	/* Ongoing Ingress Streams */
	private static Map<String, ArrayList<IngressStream>> ingressStreamsPerAggregate;

	/* All available VLCSS per Server/Gateway */
	private static Map<Server, ArrayList<VLCStreamServer>> vlcStreamsPerServer;
	private static Map<Gateway, ArrayList<VLCStreamServer>> vlcStreamsPerEgressGateway;

	/* All Aggregates in the GENI world */
	private static ArrayList<Aggregate> aggregates;

	/* All clients (EgressStream's) and Channels will have a unique ID. 
	 * Perhaps should use UUID or something stronger. */
	private static int clientIdGenerator = 0;
	private static int channelIdGenerator = 0;
	private static int groupIDGenerator = 0;

	/*
	 * IFloodlightModule implementation
	 */

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IGENICinemaService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(IGENICinemaService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IOFSwitchService.class);
		deps.add(IRestApiService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		log = LoggerFactory.getLogger(GENICinemaManager.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new GENICinemaWebRoutable());
		switchService.addOFSwitchListener(this);

		/*
		 * Initialize all class variables.
		 */
		aggregates = new ArrayList<Aggregate>(1);
		channelsPerAggregate = new HashMap<String, ArrayList<Channel>>(1); // for initial test, start of with a predefined two channels
		ingressStreamsPerAggregate = new HashMap<String, ArrayList<IngressStream>>(1); // --> need two ingress streams
		egressStreamsPerAggregate = new HashMap<String, ArrayList<EgressStream>>(1); // "large" number of clients possible
		vlcStreamsPerServer = new HashMap<Server, ArrayList<VLCStreamServer>>(1);
		vlcStreamsPerEgressGateway = new HashMap<Gateway, ArrayList<VLCStreamServer>>(1);



		/*
		 * For now, let's fake an existing aggregate w/o discovery.
		 */
		Gateway ingress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.0.2.2"))
		.setPublicIP(IPv4Address.of("130.127.215.170")) // should be correct now
		.build();

		Gateway egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.215.169"))
		.build();
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());

		Node server_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:02"))
		.setIngressPort(OFPort.LOCAL)
		.setEgressPort(OFPort.of(1)) // should be correct now
		.build();
		Server server = new Server.ServerBuilder()
		.setPrivateIP(ingress_gw.getPrivateIP()) // for initial test, server will use public IP as ingress GW
		.setPublicIP(ingress_gw.getPublicIP())
		.setOVSNode(server_ovs)
		.build();
		vlcStreamsPerServer.put(server, new ArrayList<VLCStreamServer>());

		Node ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:01"))
		.setIngressPort(OFPort.of(1)) // should be correct now
		.setEgressPort(OFPort.of(2))
		.build();

		Aggregate clemson = new Aggregate.AggregateBuilder()
		.addServer(server)
		.addEgressGateway(egress_gw)
		.addIngressGateway(ingress_gw)
		.addSwitch(ovs_switch)
		.setName("Clemson")
		.setDescription("First time creating a GENI Cinema aggregate")
		.build();

		addAggregate(clemson); // initializes all class variables dependent on Aggregates

		/*
		 * Now, configure the pre-existing VLC streams per Server and per egress Gateway
		 */

		// create first server stream's sockets
		VideoSocketBuilder vsb = new VideoSocketBuilder();
		VideoSocket pubSock;
		VideoSocket privSock;
		VLCStreamServerBuilder vlcssb = new VLCStreamServerBuilder();
		int udpPort = 5000;
		int tcpPort = 31000;

		for (int i = 0; i < 2; i++) {
			vsb.setIP(server.getPublicIP())
			.setPort(TransportPort.of(tcpPort++)) // will need to set this port
			.setProtocol(IpProtocol.TCP);
			pubSock = vsb.build();
			vsb.setIP(server.getPrivateIP())
			.setPort(TransportPort.of(udpPort++)) 
			.setProtocol(IpProtocol.UDP);
			privSock = vsb.build();

			vlcssb.setIngress(pubSock)
			.setEgress(privSock);
			vlcStreamsPerServer.get(server).add(vlcssb.build());
		}

		// now do the egress gateway's
		udpPort = 5000;
		tcpPort = 31000;
		for (int i = 0; i < 10; i++) {
			vsb.setIP(egress_gw.getPublicIP())
			.setPort(TransportPort.of(tcpPort++)) // will need to set this port
			.setProtocol(IpProtocol.TCP);
			pubSock = vsb.build();
			vsb.setIP(egress_gw.getPrivateIP())
			.setPort(TransportPort.of(udpPort++)) 
			.setProtocol(IpProtocol.UDP);
			privSock = vsb.build();

			vlcssb.setIngress(privSock)
			.setEgress(pubSock);
			vlcStreamsPerEgressGateway.get(egress_gw).add(vlcssb.build());
		}
	}

	/*
	 * IOFMessageListener implementation
	 */

	@Override
	public String getName() {
		return GENICinemaManager.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		// Be on the lookout for packets that should be handled by GENI Cinema already...
		if (msg.getType() ==  OFType.PACKET_IN) { //TODO will only work for OF1.1+
			if (msg.getVersion().compareTo(OFVersion.OF_11) >= 0) {
				log.debug("Got PI. DPID {}, Port {}", sw.getId().toString(), ((OFPacketIn) msg).getMatch().get(MatchField.IN_PORT));
			} else {
				log.debug("Got PI. DPID {}, Port {}", sw.getId().toString(), ((OFPacketIn) msg).getInPort());
			}
		}

		return Command.CONTINUE;
	}

	/*
	 * IOFSwitchListener implementation
	 */

	@Override
	public void switchAdded(DatapathId switchId) {
		/*
		 * Add OFGroups to switch if it's a "sort" switch.
		 */
		log.debug("Switch {} connected. Checking if it's a sort switch.", switchId.toString());
		initializeSortSwitchOFGroups(switchService.getSwitch(switchId));
		
		log.debug("Switch {} connected. Removing any existing UDP flows if it's a GENI Cinema switch.", switchId.toString());
		removeExistingUDPFlows(switchService.getSwitch(switchId));

		/*
		 * For all aggregates, find the switch and set it as connected.
		 * TODO I don't like how this works, but it'll do for now.
		 * switchConnected(DatapathId) will set the switch as connected if
		 * it exists in the Aggregate's configuration (lists of sort and 
		 * enable switches). If the switch does not exist, no change will
		 * be made. I think the aggregates should have a wrapper to search
		 * for, fetch, and set certain things.
		 */
		for (Aggregate aggregate : aggregates) {
			boolean result = aggregate.switchConnected(switchId);
			if (result) {
				log.debug("Switch {} connected in Aggregate {}.", switchId.toString(), aggregate.getName());
			} else {
				log.debug("Switch {} does not belong to Aggregate {}.", switchId.toString(), aggregate.getName());
			}
		}
		
		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		for (Aggregate aggregate : aggregates) {
			boolean result = aggregate.switchDisconnected(switchId);
			if (result) {
				log.debug("Switch {} disconnected in Aggregate {}.", switchId.toString(), aggregate.getName());
			} else {
				log.debug("Switch {} does not belong to Aggregate {}.", switchId.toString(), aggregate.getName());
			}
		}
	}

	@Override
	public void switchActivated(DatapathId switchId) {
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
	}

	@Override
	public void switchChanged(DatapathId switchId) {
	}

	/* 
	 * IGENICinemaService implementation 
	 */

	@Override
	public String getChannels() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addChannel(String json, ClientInfo clientInfo) {
		/* 
		 * This will presumably get us the client IP of the most recent hop.
		 * e.g. If NAT is involved between us and the client, this should
		 * give us the public-facing IP of the NAT server, which is what we
		 * will directly communicate with over the Internet.
		 */
		String clientIP = clientInfo.getAddress(); 

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		ChannelBuilder cb = new ChannelBuilder();

		int reqFields = 0;
		try {
			jp = f.createJsonParser(json);
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

				/*
				 * Parse values from all expected JSON fields.
				 */
				switch (n) {
				case JsonStrings.Add.Request.admin_password:
					cb.setAdminPassword(jp.getText());
					reqFields++;
					break;
				case JsonStrings.Add.Request.view_password:
					cb.setViewPassword(jp.getText());
					reqFields++;
					break;
				case JsonStrings.Add.Request.description:
					cb.setDescription(jp.getText());
					reqFields++;
					break;
				case JsonStrings.Add.Request.name:
					cb.setName(jp.getText());
					reqFields++;
					break;
				default:
					log.error("Got unmatched JSON string in Add-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Add request. If not, bail out.
		 * TODO We should probably send a JSON description of 
		 * the error condition back instead of an empty string.
		 */
		if (reqFields < 4) {
			log.error("Did not receive all expected JSON fields in Add request! Only got {} matches. CHANNEL NOT ADDED.", reqFields);
			return "";
		}

		//TODO this is a naive approach as-is. The following functions need to be non-dependent.
		//i.e. we need an algorithm to select a Gateway, sort Node, and Server.

		/*
		 * Try to determine where the client is, and based on
		 * location, assign the client to a nearby ingress GCGW.
		 */
		Gateway ingressGW = findBestIngressGateway(IPv4Address.of(clientIP));		
		if (ingressGW == null) {
			log.error("Could not find an available GENI Cinema Ingress Gateway for the client with IP {}", clientIP);
			return "";
		}

		/*
		 * Check to see if there are any VLC servers with sockets available
		 * that are reachable from the Gateway.
		 */
		Server hostServer = findBestHostServer(ingressGW); // TODO possibly return ArrayList of reachable Servers (to then search for a good one)

		VLCStreamServer hostServerVLCSS = getVLCSSOnHostServer(hostServer);		
		if (hostServerVLCSS ==  null) {
			log.error("Could not find an available VLCStreamServer (i.e. an available VLC listen socket) for the client to stream to.");
			return "";
		}

		/*
		 * Create the video-producing client. TODO not sure how to get src tp port info...
		 */
		VideoSocketBuilder vsb = new VideoSocketBuilder();
		vsb.setIP(IPv4Address.of(clientIP))
		.setPort(TransportPort.NONE)
		.setProtocol(IpProtocol.TCP);

		IngressStreamBuilder isb = new IngressStreamBuilder();
		isb.setGateway(ingressGW)
		.setServer(hostServerVLCSS)
		.setClient(vsb.build());

		/* Update Manager with new IngressStream */
		if (!addIngressStream(isb.build())) {
			log.error("Could not add new IngressStream to the Manager!");
			return "";
		}

		Channel theChannel = 
				cb.setGroup(OFGroup.ZERO)
				.setHostVLCStreamServer(hostServerVLCSS)
				.setHostServer(hostServer)
				.setLive(true)
				.setSortNode(null)
				.setId(generateChannelId())
				.build();

		/*
		 * Update Manager with new Channel
		 */
		if (!addChannel(theChannel)) {
			log.error("Could not add new Channel to the Manager!");
			return "";
		}
		return "";
	}

	@Override
	public String removeChannel(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String watchChannel(String json, ClientInfo clientInfo) {

		String clientIP = clientInfo.getAddress(); 

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		int reqFields = 0;
		String json_clientId = "";
		String json_channelId = "";
		String json_viewPass = "";

		try {
			jp = f.createJsonParser(json);
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

				/*
				 * Parse values from all expected JSON fields.
				 */
				switch (n) {
				case JsonStrings.Watch.Request.view_password:
					json_viewPass = jp.getText().trim();
					reqFields++;
					break;
				case JsonStrings.Watch.Request.channel_id:
					json_channelId = jp.getText().trim();
					reqFields++;
					break;
				case JsonStrings.Watch.Request.client_id:
					json_clientId = jp.getText().trim();
					reqFields++;
					break;
				default:
					log.error("Got unmatched JSON string in Watch-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Add request. If not, bail out.
		 * TODO We should probably send a JSON description of 
		 * the error condition back instead of an empty string.
		 */
		if (reqFields < 2 || (reqFields == 3 && json_clientId.equals(""))) {
			log.error("Did not receive all expected JSON fields in Add request! Only got {} matches. CHANNEL NOT ADDED.", reqFields);
			return "";
		}

		/*
		 * Lookup the Channel to see if it exists and is live.
		 * The Channel will contain the sort Node and the Server.
		 */
		int requestedChannelAsInt = -1;
		try {
			requestedChannelAsInt = Integer.parseInt(json_channelId);
		} catch (NumberFormatException e) {
			log.error("Could not parse specified Channel ID '{}'.", json_channelId);
			return "";
		}

		Channel channel = lookupChannel(requestedChannelAsInt);
		if (channel == null) {
			log.error("Could not locate specified Channel ID '{}'.", requestedChannelAsInt);
			return "";
		}

		/*
		 * Make note of the aggregate for later.
		 */
		Aggregate aggregate = lookupAggregate(channel);

		/*
		 * Check if the client is currently watching something. If so, it already
		 * has an egress Gateway, VLCStreamServer, and an EgressStream.
		 */
		int requestedClientAsInt = -1;
		try {
			requestedClientAsInt = Integer.parseInt(json_clientId);
		} catch (NumberFormatException e) {
			if (json_clientId.isEmpty()) {
				log.debug("Client ID was empty string. Assuming new client connection.");
			} else {
				log.error("Could not parse specified Client ID '{}'.", json_clientId);
				return "";
			}
		}

		EgressStream existingStream = lookupClient(requestedClientAsInt);
		if (existingStream == null) {
			/*
			 * Determine a Gateway where the client can attach and watch.
			 */
			Gateway egressGW = findBestEgressGateway(IPv4Address.of(clientIP)); // TODO should base on where the video is located AND the client, not just the client...
			if (egressGW == null) {
				log.error("Could not locate a suitable egress Gateway for client IP {}", clientIP);
				return "";
			}
			log.debug("Found a suitable egress Gateway: {}", egressGW);

			/*
			 * Allocate a new VLCStreamServer on the egress Gateway.
			 */
			VLCStreamServer vlcss = getVLCSSOnGateway(egressGW);
			if (vlcss == null) {
				log.error("Could not allocate a VLCStreamServer on Gateway {}", egressGW.toString());
				return "";
			}
			log.debug("On egress Gateway {}, found an available VLCSS: {}", egressGW, vlcss);

			/*
			 * If there is not a sort node assigned, allocate one.
			 */
			if (channel.getSortNode() == null) {
				log.debug("No sort Node was set (should be the first viewer watching the Channel then).");
				channel.setSortNode(aggregate.getSwitches().get(0)); // TODO use live load to allocate best one
			}

			/*
			 * Insert required flows along the path we just determined (VLCS w/OVS, OVS Node, and VLCS @GCGW)
			 */
			VideoSocketBuilder vsb = new VideoSocketBuilder();
			vsb.setIP(IPv4Address.of(clientIP))
			.setPort(TransportPort.NONE)
			.setProtocol(IpProtocol.TCP);

			EgressStreamBuilder esb = new EgressStreamBuilder();
			esb.setChannel(channel)
			.setGateway(vlcss)
			.setId(generateClientId())
			.setClient(vsb.build());
			EgressStream es = esb.build();
			log.debug("Pushing flows for EgressStream.");
			insertEgressStreamFlows(es, null);

			addEgressStream(es);
			log.debug("All resources allocated for NEW EgressStream. EgressStream set:", es);

			/*
			 * Check if we are wanting to change Channels to the one we're watching already.
			 * If so, we can simply do nothing and return.
			 */
		} else if (existingStream.getChannel().getId() == requestedChannelAsInt) {
			log.debug("Client {} tried to change Channels to same Channel {}. Leaving same configuration.", existingStream.getId(), existingStream.getChannel().getId());
			return "";

			/*
			 * Otherwise, the client is currently watching a Channel and would like to switch.
			 */
		} else {
			/*
			 * If there is not a sort node assigned, allocate one.
			 */
			if (channel.getSortNode() == null) {
				log.debug("No sort Node was set (should be the first viewer watching the Channel then).");
				channel.setSortNode(aggregate.getSwitches().get(0)); // TODO use live load to allocate best one
			}

			/*
			 * All info from old stream is still relevant except the Channel.
			 */
			EgressStreamBuilder esb = existingStream.createBuilder();
			esb.setChannel(channel);

			log.debug("Inserting flows to change Client {}'s Channel from {} to " + channel.getId(), existingStream.getId(), existingStream.getChannel().getId());
			insertEgressStreamFlows(esb.build(), existingStream);

			/*
			 * Now, update manager's copy with new Channel.
			 */
			existingStream.changeChannel(channel);
		}

		return "";
	}

	@Override
	public String editChannel(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * GENICinemaManager helper functions
	 */

	private void addAggregate(Aggregate aggregate) {
		if (aggregates.contains(aggregate)) {
			log.debug("Tried to add pre-existing Aggregate '{}'.", aggregate.getName());
			return;
		} else {
			log.debug("Adding new Aggregate '{}'.", aggregate.getName());
			aggregates.add(aggregate);
			channelsPerAggregate.put(aggregate.getName(), new ArrayList<Channel>()); // maps will replace
			ingressStreamsPerAggregate.put(aggregate.getName(), new ArrayList<IngressStream>()); 
			egressStreamsPerAggregate.put(aggregate.getName(), new ArrayList<EgressStream>());
			return;
		}
	}

	private Aggregate lookupAggregate(Channel whereAmI) {
		for (Aggregate aggregate : aggregates) {
			if (channelsPerAggregate.get(aggregate.getName()).contains(whereAmI)) {
				return aggregate;
			}
		}
		return null;
	}

	private Aggregate lookupAggregate(IngressStream whereAmI) {
		for (Aggregate aggregate : aggregates) {
			if (ingressStreamsPerAggregate.get(aggregate.getName()).contains(whereAmI)) {
				return aggregate;
			}
		}
		return null;
	}

	private Aggregate lookupAggregate(EgressStream whereAmI) {
		for (Aggregate aggregate : aggregates) {
			if (egressStreamsPerAggregate.get(aggregate.getName()).contains(whereAmI)) {
				return aggregate;
			}
		}
		return null;
	}

	private Aggregate lookupAggregate(Server whereAmI) {
		for (Aggregate aggregate : aggregates) {
			if (aggregate.getServers().contains(whereAmI)) {
				return aggregate;
			}
		}
		return null;
	}

	private Aggregate lookupAggregate(VLCStreamServer whereAmI) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<Server> servers = aggregate.getServers();
			for (Server server : servers) {
				if (vlcStreamsPerServer.get(server).contains(whereAmI)) {
					return aggregate;
				}
			}
			ArrayList<Gateway> gateways = aggregate.getEgressGateways();
			for (Gateway gateway : gateways) {
				if (vlcStreamsPerEgressGateway.get(gateway).contains(whereAmI)) {
					return aggregate;
				}
			}
		}
		return null;
	}

	private Channel lookupChannel(int id) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<Channel> cl = channelsPerAggregate.get(aggregate.getName());
			for (Channel c : cl) {
				if (c.getId() == id) {
					return c;
				}
			}
		}
		return null;
	}

	private EgressStream lookupClient(int id) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<EgressStream> esl = egressStreamsPerAggregate.get(aggregate.getName());
			for (EgressStream es : esl) {
				if (es.getId() == id) {
					return es;
				}
			}
		}
		return null;
	}

	/**
	 * Takes a new Channel and updates the Manager appropriately.
	 * 
	 * @param newChannel, The recently-added Channel
	 * @return, true upon success; false upon failure
	 */
	private boolean addChannel(Channel newChannel) {
		Aggregate theAggregate = lookupAggregate(newChannel.getHostServer());

		if (theAggregate == null) {
			log.error("Could not find Aggregate for this Channel: {}", newChannel.getId());
			return false;
		}

		/*
		 * Add the Channel to the list.
		 */
		if (channelsPerAggregate.get(theAggregate.getName()).contains(newChannel)) {
			return false;
		} else {
			/* 
			 * Reserve the resources.
			 */
			newChannel.getHostVLCStreamServer().setInUse();
			channelsPerAggregate.get(theAggregate.getName()).add(newChannel);
			return true;
		}
	}

	/**
	 * Takes a new IngressStream and updates the Manager appropriately.
	 * 
	 * @param newIngressStream, The recently-added IngressStream
	 * @return, true upon success; false upon failure
	 */
	private boolean addIngressStream(IngressStream newIngressStream) {
		Aggregate theAggregate = lookupAggregate(newIngressStream.getServer());

		/*
		 * Add the IngressStream to the list.
		 */
		if (!ingressStreamsPerAggregate.containsKey(theAggregate.getName())) {
			return false;
		} else if (ingressStreamsPerAggregate.get(theAggregate.getName()).contains(newIngressStream)) {
			return false;
		} else {
			ingressStreamsPerAggregate.get(theAggregate.getName()).add(newIngressStream);
			return true;
		}
	}

	/**
	 * Takes a new EgressStream and updates the Manager appropriately.
	 * 
	 * @param newEgressStream, The recently-added EgressStream
	 * @return, true upon success; false upon failure
	 */
	private boolean addEgressStream(EgressStream newEgressStream) {
		Aggregate theAggregate = lookupAggregate(newEgressStream.getVLCSSAtGateway());

		/*
		 * Add the EgressStream to the list.
		 */
		if (!egressStreamsPerAggregate.containsKey(theAggregate.getName())) {
			return false;
		} else if (egressStreamsPerAggregate.get(theAggregate.getName()).contains(newEgressStream)) {
			return false;
		} else {
			/*
			 * Reserve resources.
			 */
			newEgressStream.getVLCSSAtGateway().setInUse();
			egressStreamsPerAggregate.get(theAggregate.getName()).add(newEgressStream);
			return true;
		}
	}

	/**
	 * Use the client's IP to try and determine where it is, and
	 * thus it's closest ingress Gateway. Load-balancing, in addition
	 * to location may be used to determine the most appropriate
	 * Gateway. If a Gateway cannot be found, either no aggregates
	 * are configured with an ingress Gateway, or the GENI Cinema
	 * service is overloaded and cannot accept any additional channels.
	 * 
	 * @param clientIP, The IP address of the client; used to guess location.
	 * @return A valid Gateway or null if one could not be found/allocated.
	 */
	private Gateway findBestIngressGateway(IPv4Address clientIP) {
		/*
		 * Look through the available aggregates to see who has a GW
		 * that is the best match (closest?).
		 */
		int bestScore = Integer.MAX_VALUE;
		Gateway bestGW = null;
		int score;

		for (Aggregate aggregate : aggregates) {
			ArrayList<Gateway> igs = aggregate.getIngressGateways();
			for (Gateway ig : igs) {
				score = geoLocateScore(ig.getPublicIP(), clientIP);
				if (score < bestScore) {
					bestScore = score;
					bestGW = ig;
				}
			}
		}
		return bestGW;
	}

	/**
	 * Attempt to score how close two public IPs are to each other.
	 * A low result is best, and high result indicates lower link
	 * performance.
	 * 
	 * @param ip1
	 * @param ip2
	 * @return
	 */
	private int geoLocateScore(IPv4Address ip1, IPv4Address ip2) {
		IPv4Address commonBits = ip1.and(ip2);
		// TODO something cool here. For now, assume they all have the same score.

		return 0;
	}

	/**
	 * Use the client's IP to try and determine where it is, and
	 * thus it's closest egress Gateway. Load-balancing, in addition
	 * to location may be used to determine the most appropriate
	 * Gateway. If a Gateway cannot be found, either no aggregates
	 * are configured with an egress Gateway, or the GENI Cineam
	 * service is overlaoded and cannot accept any additional clients.
	 * 
	 * @param clientIP, The IP address of the client; used to guess location.
	 * @return A valid Gateway or null if one could not be found/allocated.
	 */
	private Gateway findBestEgressGateway(IPv4Address clientIP) {
		/*
		 * Look through the available aggregates to see who has a GW
		 * that is the best match (closest?).
		 */
		int bestScore = Integer.MAX_VALUE;
		Gateway bestGW = null;
		int score;

		for (Aggregate aggregate : aggregates) {
			ArrayList<Gateway> igs = aggregate.getEgressGateways();
			for (Gateway ig : igs) {
				score = geoLocateScore(ig.getPublicIP(), clientIP);
				if (score < bestScore) {
					bestScore = score;
					bestGW = ig;
				}
			}
		}
		return bestGW;
	}

	/**
	 * Based on the ingress Gateway, determine which Server the video feed
	 * should be routed to and hosted on.
	 * 
	 * @param ingressGW, The Gateway serving as the entry-point in the GC network.
	 * @return The Server the IngressStream should be stored on, or null upon failure.
	 */
	private Server findBestHostServer(Gateway ingressGW) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<Server> servers = aggregate.getServers();
			return servers.get(0); // TODO stupid approach for now
		}
		return null;
	}

	/**
	 * Based on the Gateway assigned, try to allocate in/out sockets on the VLCS. 
	 * 
	 * @param egressGW, The output GCGW where the client would like to connect.
	 * @return non-null upon success; null if no resources available.
	 */
	private VLCStreamServer getVLCSSOnGateway(Gateway egressGW) {
		ArrayList<VLCStreamServer> ssl = vlcStreamsPerEgressGateway.get(egressGW);

		if (ssl == null) {
			return null;
		}

		for (VLCStreamServer ss : ssl) {
			if (ss.isAvailable()) {
				return ss;
			}
		}
		return null;
	}

	/**
	 * Based on the Server assigned, try to allocate in/out sockets on the VLCS.
	 * 
	 * @param hostServer, The host VLCS the ingress stream should be stored on.
	 * @return non-null upon success; null if no resources available.
	 */
	private VLCStreamServer getVLCSSOnHostServer(Server hostServer) {
		ArrayList<VLCStreamServer> ssl = vlcStreamsPerServer.get(hostServer);

		if (ssl == null) {
			return null;
		}

		for (VLCStreamServer ss : ssl) {
			if (ss.isAvailable()) {
				return ss;
			}
		}
		return null;
	}


	private boolean releaseResources() {
		return false;
	}

	/**
	 * Based on the host VLCS, find the next-hop OVS "sort" Node. This
	 * Node should be connected in some way to both the Server and the
	 * Gateway.
	 * 
	 * @param vlcHost, The Server hosting the video.
	 * @param egressGW, The Gateway the client will use.
	 * @return An available Node or null if one could not be found/allocated.
	 */
	private Node getAvailableSortNode(Server vlcHost, Gateway egressGW) {
		return aggregates.get(0).getSwitches().get(0); // return the only one in our aggregate right now
	}

	/**
	 * For use when connecting a new client. Will always
	 * return a "new" integer that has not been assigned
	 * to a client (EgressStream).
	 * 
	 * @return The next integer for a client. Will be unique (within reason).
	 */
	private int generateClientId() {
		return ++clientIdGenerator;
	}

	/**
	 * For use when connecting a new Channel. Will always
	 * return a "new" integer that has not been assigned
	 * to a Channel.
	 * 
	 * @return The next integer for a Channel. Will be unique (within reason).
	 */
	private int generateChannelId() {
		return ++channelIdGenerator;
	}

	/**
	 * For use when connecting a new Channel. Will always
	 * return a new OFGroup that has not been assigned
	 * to a Channel.
	 * 
	 * @return The next OFGroup for a Channel.
	 */
	private OFGroup generateOFGroup() {
		return OFGroup.of(++groupIDGenerator);
	}

	/**
	 * Configure a sort switch with its OFGroups.
	 * A check is done to ensure the switch provided is a
	 * valid sort switch (i.e. has a pre-configured DPID).
	 * (Each switch should have all tables already, so no
	 * OFTable setup/config should be required or is done
	 * here.)
	 * 
	 * @param sw, The IOFSwitch to configure with our OFGroups.
	 */
	private void initializeSortSwitchOFGroups(IOFSwitch sw) {
		boolean found = false;
		ArrayList<OFGroup> groups;
		ArrayList<OFMessage> listOfGroupsToAdd = new ArrayList<OFMessage>();
		ArrayList<OFMessage> listOfGroupsToRemove = new ArrayList<OFMessage>();
		for (Aggregate aggregate : aggregates) {
			ArrayList<Node> aggSortSwitches = aggregate.getSwitches();
			for (Node node : aggSortSwitches) {
				if (node.getSwitchDpid().equals(sw.getId())) {
					groups = aggregate.peekOFGroups(node);
					for (OFGroup group : groups) {
						log.debug("Found Node with matching DPID {} in Aggregate {}. Creating OFGroupAdd's for switch.", sw.getId().toString(), aggregate.getName());
						OFFactory factory = sw.getOFFactory();
						OFGroupAdd newGroup = factory.buildGroupAdd()
								.setGroup(group)
								.setGroupType(OFGroupType.ALL)
								.build();
						OFGroupDelete oldGroup = factory.buildGroupDelete()
								.setGroup(group)
								.setGroupType(OFGroupType.ALL)
								.build();

						listOfGroupsToAdd.add(newGroup);
						listOfGroupsToRemove.add(oldGroup);
						found = true;
					}
					if (found) break;
				}
			}
			if (found) break; // not sure if the first break will break all loops or just the internal one...
		}

		if (!listOfGroupsToRemove.isEmpty()) {
			log.debug("Writing list of OFGroupDelete's of size {} to switch {}.", listOfGroupsToRemove.size(), sw.getId().toString());
			sw.write(listOfGroupsToRemove);
		}

		if (!listOfGroupsToAdd.isEmpty()) {
			log.debug("Writing list of OFGroupAdd's of size {} to switch {}.", listOfGroupsToAdd.size(), sw.getId().toString());
			sw.write(listOfGroupsToAdd);
		}
	}

	/**
	 * For all switches in the GENI Cinema service, remove any
	 * pre-existing UDP-match flows. These might conflict with
	 * the new runtime configuration.
	 * 
	 * @param sw, The IOFSwitch to configure with our OFGroups.
	 */
	private void removeExistingUDPFlows(IOFSwitch sw) {
		boolean found = false;
		OFFlowDelete flowDelete = null;
		for (Aggregate aggregate : aggregates) {
			ArrayList<Node> aggSwitches = aggregate.getSwitches();
			ArrayList<Server> aggServers = aggregate.getServers();

			for (Server server : aggServers) {
				aggSwitches.add(server.getOVSNode());
			}

			for (Node node : aggSwitches) {
				if (node.getSwitchDpid().equals(sw.getId())) {
					log.debug("Found Node with matching DPID {} in Aggregate {}. Removing existing UDP flows for switch.", sw.getId().toString(), aggregate.getName());
					OFFactory factory = sw.getOFFactory();
					Match.Builder mb = factory.buildMatch();
					flowDelete = factory.buildFlowDelete()
							.setMatch(mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
									.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
									.build())
							.build();
					found = true;
					break;
				}
			}
			if (found) break; // not sure if the first break will break all loops or just the internal one...
		}

		if (flowDelete != null) {
			log.debug("Writing OFFlowDelete to switch {}.", sw.getId().toString());
			sw.write(flowDelete);
		}
	}

	/**
	 * Push all flows required for this EgressStream to the client.
	 * Prerequisite requirement: All Channel information must be 
	 * complete beforehand. This is a last-stop function that does 
	 * all the OpenFlow dirty-work and does nothing to ensure the 
	 * Channel is ready to be added (if it isn't being viewed 
	 * already). It does however conditionally add the flows based 
	 * on the current demand for the Channel. If there is demand, 
	 * this function assumes the goto OFGroup flow and first-hop 
	 * OVS enable flow have already been inserted (by this same 
	 * function call the first time someone tried to view the Channel,
	 * i.e. when there was no demand on the first time.)
	 * 
	 * @param stream, The complete (missing ZERO information) stream.
	 * @param stream, The old stream if the client is changing Channels.
	 * @return
	 */
	private void insertEgressStreamFlows(EgressStream newStream, EgressStream oldStream) {

		OFFactory factory;

		/*
		 * Only need to enable the Channel if someone isn't watching already.
		 * Also only need to assign it to an OFGroup if someone isn't watching.
		 */
		if (!newStream.getChannel().getDemand()) {

			/* *******************************************
			 * FIRST THE SERVER OVS NODE'S ALLOW/DENY FLOW
			 * *******************************************/

			factory = switchService.getSwitch(newStream.getChannel().getHostServer().getOVSNode().getSwitchDpid()).getOFFactory();

			/*
			 * All this just for an output port...
			 */
			OFActionOutput output = factory.actions().buildOutput()
					.setMaxLen(Integer.MAX_VALUE)
					.setPort(newStream.getChannel().getHostServer().getOVSNode().getEgressPort())
					.build();
			ArrayList<OFAction> actionList = new ArrayList<OFAction>(1);
			actionList.add(output); //TODO should this be "apply" or "write" actions?
			OFInstructionApplyActions applyActionsInstruction = factory.instructions().buildApplyActions()
					.setActions(actionList)
					.build();
			ArrayList<OFInstruction> instructionList = new ArrayList<OFInstruction>(1);
			instructionList.add(applyActionsInstruction);

			/*
			 * Construct the Match for UDP source port of the Channel.
			 * Build the OFFlowAdd with the Match and List<OFInstruction> (from above).
			 */
			OFFlowAdd enableFlow = factory.buildFlowAdd()
					.setMatch(
							factory.buildMatch()
							.setExact(MatchField.ETH_TYPE, EthType.IPv4)
							.setExact(MatchField.IP_PROTO, newStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
							.setExact(MatchField.UDP_SRC, newStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
							.setExact(MatchField.IN_PORT, newStream.getChannel().getHostServer().getOVSNode().getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.setOutPort(newStream.getChannel().getHostServer().getOVSNode().getEgressPort())
							.setPriority(FlowModUtils.PRIORITY_MAX)
							.setInstructions(instructionList)
							.build();

			log.debug("Writing OFFlowAdd to enable Channel {} on UDP port {} out of the VLCS: " + enableFlow.toString(),
					newStream.getChannel().getId(), newStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

			switchService.getSwitch(newStream.getChannel().getHostServer().getOVSNode().getSwitchDpid()).write(enableFlow);

			/* ********************************************
			 * NEXT, THE SORT OVS NODE'S DEFAULT-TABLE FLOW
			 * This should send the UDP-src packet to the
			 * correct OFGroup, which will then duplicate.
			 * ********************************************/

			if (newStream.getChannel().getGroup() == OFGroup.ZERO) {
				newStream.getChannel().setGroup(generateOFGroup());
			}

			factory = switchService.getSwitch(newStream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();
			OFActionGroup actionGotoGroup = factory.actions().buildGroup()
					.setGroup(newStream.getChannel().getGroup())
					.build();
			actionList = new ArrayList<OFAction>(1);
			actionList.add(actionGotoGroup);
			applyActionsInstruction = factory.instructions().buildApplyActions()
					.setActions(actionList)
					.build();
			instructionList = new ArrayList<OFInstruction>(1);
			instructionList.add(applyActionsInstruction);
			OFFlowAdd toGroupFlow = factory.buildFlowAdd()
					.setMatch(
							factory.buildMatch()
							.setExact(MatchField.ETH_TYPE, EthType.IPv4)
							.setExact(MatchField.IP_PROTO, newStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
							.setExact(MatchField.UDP_SRC, newStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
							.setExact(MatchField.IN_PORT, newStream.getChannel().getSortNode().getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.setOutGroup(newStream.getChannel().getGroup())
							.setPriority(FlowModUtils.PRIORITY_MAX)
							.setInstructions(instructionList)
							.build(); /* Don't set the table-id --> will use default table */

			log.debug("Writing OFFlowAdd to send Channel {} on UDP port {} to OFGroup #" + newStream.getChannel().getGroup().getGroupNumber() + " : " + toGroupFlow.toString(),
					newStream.getChannel().getId(), newStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

			switchService.getSwitch(newStream.getChannel().getSortNode().getSwitchDpid()).write(toGroupFlow);

		} // END IF DEMAND == FALSE (initial setup for Channel)

		/* ***************************************
		 * NOW, CONFIGURE THE OFGROUP'S OFBUCKET'S
		 * via an OFGroupMod, which will allow a
		 * new bucket list to be added to the 
		 * OFGroup.
		 * **************************************/

		factory = switchService.getSwitch(newStream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();

		//TODO assume right now that the VLCS already sent with the correct MAC and IP for the GCGW
		ArrayList<OFAction> bucketActions = new ArrayList<OFAction>(2);
		bucketActions.add(factory.actions().setField(factory.oxms().udpDst(newStream.getVLCSSAtGateway().getIngress().getPort()))); 
		bucketActions.add(factory.actions().output(newStream.getChannel().getSortNode().getEgressPort(), Integer.MAX_VALUE));

		OFBucket bucket = factory.buildBucket()
				.setActions(bucketActions)
				.setWatchGroup(OFGroup.ANY) 	/* MUST EXPLICITLY SET OFGroup and OFPort as ANY, */
				.setWatchPort(OFPort.ANY)   	/* even if OFGroupType=ALL --> watch doesn't even matter */
				.build();						/* TODO possible bug in LOXI? */

		/*
		 * Get the current list of Channel viewers/clients and add this client.
		 */
		newStream.getChannel().addBucket(newStream.getId(), bucket);

		OFGroupModify groupMod = factory.buildGroupModify()
				.setGroup(newStream.getChannel().getGroup())
				.setGroupType(OFGroupType.ALL)
				.setBuckets(newStream.getChannel().getBucketList())
				.build();

		log.debug("Writing OFGroupMod to send Channel {} on UDP port {} from OFGroup #" + newStream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString(),
				newStream.getChannel().getId(), newStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

		switchService.getSwitch(newStream.getChannel().getSortNode().getSwitchDpid()).write(groupMod);

		/*
		 * If the client was changing Channels, remove the old
		 * bucket from the previous Channel.
		 */
		if (oldStream != null) {
			/*
			 * First, update the old Channel's bucket list by
			 * removing the client's bucket. The demand and client
			 * count for the Channel will be automatically updated
			 * by the Channel.
			 */
			factory = switchService.getSwitch(oldStream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();

			oldStream.getChannel().removeBucket(oldStream.getId());

			groupMod = factory.buildGroupModify()
					.setGroup(oldStream.getChannel().getGroup())
					.setGroupType(OFGroupType.ALL)
					.setBuckets(oldStream.getChannel().getBucketList())
					.build();

			log.debug("Writing OFGroupMod to remove client {}'s OFBucket from OFGroup {}", 
					newStream.getChannel().getId(), oldStream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString());

			switchService.getSwitch(oldStream.getChannel().getSortNode().getSwitchDpid()).write(groupMod);

			/*
			 * If there is no longer any demand for a particular Channel,
			 * disable it at the Server-side OVS.
			 * TODO should we also revoke the Channel's OFGroup?
			 */
			if (!oldStream.getChannel().getDemand()) {
				factory = switchService.getSwitch(oldStream.getChannel().getHostServer().getOVSNode().getSwitchDpid()).getOFFactory();
				/*
				 * Construct the Match for UDP source port of the Channel.
				 * Build the OFFlowDelete with the Match and List<OFInstruction> (from above).
				 */
				OFFlowModify disableFlow = factory.buildFlowModify()
						.setMatch(
								factory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, oldStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
								.setExact(MatchField.UDP_SRC, oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
								.setExact(MatchField.IN_PORT, oldStream.getChannel().getHostServer().getOVSNode().getIngressPort())
								.build())
								.setBufferId(OFBufferId.NO_BUFFER)
								.build(); /* Do not set any actions --> DROP */

				log.debug("Writing OFFlowModify to disable/drop Channel {} on UDP port {} out of the VLCS: " + disableFlow.toString(),
						oldStream.getChannel().getId(), oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

				switchService.getSwitch(oldStream.getChannel().getHostServer().getOVSNode().getSwitchDpid()).write(disableFlow);

				factory = switchService.getSwitch(oldStream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();

				/*
				 * Now repeat for the host sort OVS Node
				 */
				disableFlow = factory.buildFlowModify()
						.setMatch(
								factory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, oldStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
								.setExact(MatchField.UDP_SRC, oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
								.setExact(MatchField.IN_PORT, oldStream.getChannel().getHostServer().getOVSNode().getIngressPort())
								.build())
								.setBufferId(OFBufferId.NO_BUFFER)
								.build(); /* Do not set any actions --> DROP */

				log.debug("Writing OFFlowModify to disable/drop Channel {} on UDP port {} out of the sort OVS Node: " + disableFlow.toString(),
						oldStream.getChannel().getId(), oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

				switchService.getSwitch(oldStream.getChannel().getSortNode().getSwitchDpid()).write(disableFlow);

				/*
				 * Return the OFGroup to the pool.
				 */
				// TODO
			}
		}
	}
}
