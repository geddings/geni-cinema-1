package net.floodlightcontroller.genicinema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowModify;
import org.projectfloodlight.openflow.protocol.OFFlowModifyStrict;
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
import com.google.common.collect.ImmutableList;

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

	/* Used by the garbage collector */
	private static GENICinemaManager instance;
	private static ScheduledThreadPoolExecutor clientGarbageCollectorExecutor;
	private static Runnable clientGarbageCollector;

	/* All available Channels per aggregate per VLC Server */
	private volatile static Map<String, ArrayList<Channel>> channelsPerAggregate;

	/* 1-to-1 Mapping b/t Switch and Egress GW */
	private volatile static Map<Node, Gateway> switchToEgressGatewayBindings;

	/* Ongoing Egress Streams */
	private volatile static Map<String, ArrayList<EgressStream>> egressStreamsPerAggregate;

	/* Ongoing Ingress Streams */
	private volatile static Map<String, ArrayList<IngressStream>> ingressStreamsPerAggregate;

	/* All available VLCSS per Server/Gateway */
	private volatile static Map<Server, ArrayList<VLCStreamServer>> vlcStreamsPerServer;
	private volatile static Map<Gateway, ArrayList<VLCStreamServer>> vlcStreamsPerEgressGateway;
	private volatile static Map<Gateway, Integer> numAvailableVlcStreamsPerEgressGateway;

	/* All Aggregates in the GENI world */
	private volatile static ArrayList<Aggregate> aggregates;

	/* All clients (EgressStream's) and Channels will have a unique ID. 
	 * TODO Perhaps should use UUID or something stronger. */
	private volatile static int clientIdGenerator = 0;
	private volatile static int channelIdGenerator = 0;
	private volatile static int groupIDGenerator = 0;

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
		instance = this;
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new GENICinemaWebRoutable());
		switchService.addOFSwitchListener(this);

		/*
		 * Initialize all class variables.
		 */
		aggregates = new ArrayList<Aggregate>(2);
		channelsPerAggregate = new HashMap<String, ArrayList<Channel>>(255); // for initial test, start of with a predefined two channels
		ingressStreamsPerAggregate = new HashMap<String, ArrayList<IngressStream>>(100); // --> need two ingress streams
		egressStreamsPerAggregate = new HashMap<String, ArrayList<EgressStream>>(1000); // "large" number of clients possible
		vlcStreamsPerServer = new HashMap<Server, ArrayList<VLCStreamServer>>(100);
		vlcStreamsPerEgressGateway = new HashMap<Gateway, ArrayList<VLCStreamServer>>(100);
		numAvailableVlcStreamsPerEgressGateway = new HashMap<Gateway, Integer>(100);
		switchToEgressGatewayBindings = new HashMap<Node, Gateway>(5); // this is for telling which OVS corresponds to which egress GW and vice versa

		/*
		 * For now, let's fake an existing aggregate w/o discovery.
		 */
		Gateway ingress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.0.2.2"))
		.setPublicIP(IPv4Address.of("130.127.215.170")) // should be correct now
		.build();

		Node server_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:01")) // was 00:02
		.setIngressPort(OFPort.LOCAL)
		.setEgressPort(OFPort.of(1)) // should be correct now
		.build();
		Server server = new Server.ServerBuilder()
		.setPrivateIP(ingress_gw.getPrivateIP()) // for initial test, server will use public IP as ingress GW
		.setPublicIP(ingress_gw.getPublicIP())
		.setOVSNode(server_ovs)
		.build();
		vlcStreamsPerServer.put(server, new ArrayList<VLCStreamServer>());

		/*
		 * This is the root OVS.
		 * It will input from 1 VLC Server and output to all ports.
		 * Just FLOOD all IP and ARP packets.
		 */
		Node root_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:02"))
		.setIngressPort(OFPort.of(1)) 
		.setEgressPort(OFPort.FLOOD)
		.build();

		/*
		 * Switch 1
		 */
		ArrayList<Node> ovss = new ArrayList<Node>(5);
		Node ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:03"))
		.setIngressPort(OFPort.of(1))
		.setEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 1
		 */
		ArrayList<Gateway> egws = new ArrayList<Gateway>(5);
		Gateway egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.88.1"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 2
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:04"))
		.setIngressPort(OFPort.of(1))
		.setEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 2
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.88.2"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 3
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:05"))
		.setIngressPort(OFPort.of(1))
		.setEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 3
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.88.3"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 4
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:06"))
		.setIngressPort(OFPort.of(1))
		.setEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 4
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.88.4"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 5
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:07"))
		.setIngressPort(OFPort.of(1))
		.setEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 5
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.1.1"))
		.setPublicIP(IPv4Address.of("130.127.88.5"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		Aggregate clemson = new Aggregate.AggregateBuilder()
		.addServer(server)
		.setEgressGateways(egws)
		.addIngressGateway(ingress_gw)
		.addRootSwitch(root_ovs)
		.setSwitches(ovss)
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
		int tcpPort = 31000;
		int udpPort = 32000;

		/*
		 * Do the Ingress GW (only one of these, so can use 'server' directly)
		 */
		for (int i = 0; i < 100; i++) {
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

		/*
		 * Do the Egress GWs' (X of these)
		 */
		for (Gateway egw : clemson.getEgressGateways()) {
			udpPort = 32000;
			tcpPort = 33000;
			int limit = 100;
			for (int i = 0; i < limit; i++) {
				vsb.setIP(egw.getPublicIP())
				.setPort(TransportPort.of(tcpPort++)) // will need to set this port
				.setProtocol(IpProtocol.TCP);
				pubSock = vsb.build();
				vsb.setIP(egw.getPrivateIP())
				.setPort(TransportPort.of(udpPort++)) 
				.setProtocol(IpProtocol.UDP);
				privSock = vsb.build();

				vlcssb.setIngress(privSock)
				.setEgress(pubSock);
				vlcStreamsPerEgressGateway.get(egw).add(vlcssb.build());
			}
			numAvailableVlcStreamsPerEgressGateway.put(egw, limit);
		}

		/*
		 * Lastly, start the garbage collector timer.
		 */
		clientGarbageCollectorExecutor = new ScheduledThreadPoolExecutor(1);
		clientGarbageCollector = new StreamGarbageCollector();
		clientGarbageCollectorExecutor.scheduleAtFixedRate(clientGarbageCollector, 1800, 
				1800, TimeUnit.SECONDS);

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
		if (msg.getType() ==  OFType.PACKET_IN) {
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

		log.debug("Switch {} connected. Adding FLOOD flods if it's in the root tree.", switchId.toString());
		initializeRootSwitch(switchService.getSwitch(switchId));

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
	public synchronized ArrayList<Map<String, String>> getChannels() {
		ArrayList<Map<String, String>> response = new ArrayList<Map<String, String>>();

		for (Aggregate aggregate : aggregates) {
			ArrayList<Channel> channels = channelsPerAggregate.get(aggregate.getName());
			if (channels == null) {
				// no-op; skip to next aggregate
			} else {
				for (Channel channel : channels) {
					if (channel.getLive()) {
						Map<String, String> channelInfo = new HashMap<String, String>();
						channelInfo.put(JsonStrings.Query.Response.channel_id, String.valueOf(channel.getId()));
						channelInfo.put(JsonStrings.Query.Response.description, channel.getDescription());
						channelInfo.put(JsonStrings.Query.Response.name, channel.getName());
						channelInfo.put(JsonStrings.Query.Response.aggregate_name, aggregate.getName());
						response.add(channelInfo);
					}
				}
			}
		}

		Map<String, String> result = new HashMap<String, String>();
		if (response.isEmpty()) {
			result.put(JsonStrings.Query.Response.result, "1");
			result.put(JsonStrings.Query.Response.result_message, "There are no Channels available at this time. Please check back later.");
			response.add(result);
		} else {
			result.put(JsonStrings.Query.Response.result, "0");
			result.put(JsonStrings.Query.Response.result_message, "There are Channels available for viewing. See the returned Channel information to make your selection.");
			response.add(result);
		}

		return response;
	}

	@Override
	public synchronized Map<String, String> clientKeepAlive(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<String, String> clientDisconnect(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Map<String, String> addChannel(String json, ClientInfo clientInfo) {
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

		Map<String, String> response = new HashMap<String, String>();

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
					cb.setDescription(jp.getText().trim());
					reqFields++;
					break;
				case JsonStrings.Add.Request.name:
					cb.setName(jp.getText().trim());
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
		 */
		if (reqFields < 4) {
			log.error("Did not receive all expected JSON fields in Add request! Only got {} matches. CHANNEL NOT ADDED.", reqFields);
			response.put(JsonStrings.Add.Response.result, "1");
			response.put(JsonStrings.Add.Response.result_message, "Did not receive all expected JSON fields in Add request.");
			return response;
		}

		/*
		 * Find an adequate Egress Gateway based on client
		 * location and resource availability.
		 */
		IPv4Address clientIPconverted;
		try {
			clientIPconverted = IPv4Address.of(clientIP);
		} catch (Exception e) {
			log.error("Could not parse client IP in JSON request to ADD a Channel.");
			response.put(JsonStrings.Add.Response.result, "6");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not parse client IP to ADD a Channel. Client IP " + clientIP);
			return response;
		}

		Gateway ingressGW = findBestIngressGateway(clientIPconverted);		
		if (ingressGW == null) {
			log.error("Could not find an available GENI Cinema Ingress Gateway for the client with IP {}", clientIP);
			response.put(JsonStrings.Add.Response.result, "2");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not find an available GENI Cinema Ingress Gateway for the client with IP " + clientIP);
			return response;
		}

		/*
		 * Check to see if there are any VLC servers with sockets available
		 * that are reachable from the Gateway.
		 */
		Server hostServer = findBestHostServer(ingressGW); // TODO possibly return ArrayList of reachable Servers (to then search for a good one)

		VLCStreamServer hostServerVLCSS = getVLCSSOnHostServer(hostServer);		
		if (hostServerVLCSS ==  null) {
			log.error("Could not find an available VLCStreamServer (i.e. an available VLC listen socket) for the client to stream to.");
			response.put(JsonStrings.Add.Response.result, "3");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not allocate a new ingress stream to the GENI Cinema Service. Please try again and contact the admins if the problem persists.");
			return response;
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

		IngressStream theStream = isb.build();

		/* Update Manager with new IngressStream */
		if (!addIngressStream(theStream)) {
			log.error("Could not add new IngressStream to the Manager!");
			response.put(JsonStrings.Add.Response.result, "4");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not add the allocated ingress stream to the GENI Cinema Service. Please try again and contact the admins if the problem persists.");
			return response;
		}

		Channel theChannel = 
				cb.setGroup(OFGroup.ZERO)
				.setHostVLCStreamServer(hostServerVLCSS)
				.setHostServer(hostServer)
				.setLive(true)
				.setId(generateChannelId())
				.build();

		/*
		 * Update Manager with new Channel
		 */
		if (!addChannel(theChannel)) {
			log.error("Could not add new Channel to the Manager!");
			response.put(JsonStrings.Add.Response.result, "5");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not add the allocated channel to the GENI Cinema Service. Please try again and contact the admins if the problem persists.");
			return response;
		}

		insertNewChannelDropFlows(theChannel);

		response.put(JsonStrings.Add.Response.result, "0");
		response.put(JsonStrings.Add.Response.result_message, 
				"Channel has been successfully added to the GENI Cinema Service. Initiate your stream to make the channel available to viewers.");
		response.put(JsonStrings.Add.Response.admin_password, theChannel.getAdminPassword());
		response.put(JsonStrings.Add.Response.channel_id, Integer.toString(theChannel.getId()));
		response.put(JsonStrings.Add.Response.description, theChannel.getDescription());
		response.put(JsonStrings.Add.Response.gateway_ip, theStream.getGateway().getPublicIP().toString());
		response.put(JsonStrings.Add.Response.gateway_port, theStream.getServer().getIngress().getPort().toString());
		response.put(JsonStrings.Add.Response.name, theChannel.getName());
		response.put(JsonStrings.Add.Response.view_password, theChannel.getViewPassword());
		return response;

	}

	@Override
	public synchronized Map<String, String> removeChannel(String json, ClientInfo clientInfo) {
		String clientIP = clientInfo.getAddress(); 

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		String password = "";
		int channelId = -1;

		Map<String, String> response = new HashMap<String, String>();

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

				/*
				 * Parse values from all expected JSON fields.
				 */
				switch (n) {
				case JsonStrings.Remove.Request.admin_password:
					password = jp.getText();
					reqFields++;
					break;
				case JsonStrings.Remove.Request.channel_id:
					channelId = Integer.parseInt(jp.getText());
					reqFields++;
					break;
				default:
					log.error("Got unmatched JSON string in Remove-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Remove request. If not, bail out.
		 */
		if (reqFields < 2) {
			log.error("Did not receive all expected JSON fields in Remove request! Only got {} matches. CHANNEL NOT REMOVED.", reqFields);
			response.put(JsonStrings.Add.Response.result, "1");
			response.put(JsonStrings.Add.Response.result_message, "Did not receive all expected JSON fields in Remove request.");
			return response;
		}

		/*
		 * Now do something here to check for a valid video and remove it.
		 */


		return null;
	}

	@Override
	public synchronized Map<String, String> watchChannel(String json, ClientInfo clientInfo) {

		String clientIP = clientInfo.getAddress(); 

		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		int reqFields = 0;
		String json_clientId = "";
		String json_channelId = "";
		String json_viewPass = "";

		Map<String, String> response = new HashMap<String, String>();

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
		if (reqFields < 3) {
			log.error("Did not receive all expected JSON fields in Add request! Only got {} matches. CHANNEL NOT ADDED.", reqFields);
			response.put(JsonStrings.Add.Response.result, "1");
			response.put(JsonStrings.Add.Response.result_message, "Did not receive all expected JSON fields in Watch request.");
			return response;
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
			response.put(JsonStrings.Add.Response.result, "2");
			response.put(JsonStrings.Add.Response.result_message, 
					"Could not parse specified Channel ID. Please provide a positive, integer Channel ID.");
			return response;
		}

		Channel channel = lookupChannel(requestedChannelAsInt);
		if (channel == null) {
			log.error("Could not locate specified Channel ID '{}'.", requestedChannelAsInt);
			response.put(JsonStrings.Add.Response.result, "3");
			response.put(JsonStrings.Add.Response.result_message, "The Channel ID specified is not available as this time.");
			return response;
		}

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
				response.put(JsonStrings.Add.Response.result, "4");
				response.put(JsonStrings.Add.Response.result_message, "A client ID was provided, but it could not be parsed. Please check your client ID and try again.");
				return response;
			}
		}

		EgressStream existingStream;
		if (requestedClientAsInt != -1) {
			existingStream = lookupClient(requestedClientAsInt); // TODO this will return null even if we specify a junk client ID. Should probably see if "" CID --> null
		} else {
			existingStream = null;
		}

		/*
		 * This is a new client.
		 * (1) 	Find an egress Gateway based on our algorithm of choice.
		 * (2) 	Check if it has any available VLCSS's.
		 * (2a)	If not, try another egress Gateway and repeat (2).
		 * (2b)	If it does, lookup corresponding sort OVS Node and goto (3).
		 * (2c)	If all VLCSS's occupied, we're full, so return error msg and stop.
		 * (3)	Resources are available.
		 * (3a)	Get a client ID.
		 * (3b)	Add the new client ID and its sort Node to the Channel.
		 *		This give the Channel the client's info for processing in a bit.
		 * (3c)	Create the VideoSocket and add it to the EgressStream.
		 * (3d)	Insert flows and update the manager.
		 */
		if (existingStream == null) {
			/*
			 * If the client erroneously presented a client ID (and we don't have it,
			 * which is the case if we get inside here), reset it.
			 */
			requestedClientAsInt = -1;

			/*
			 * Determine a Gateway where the client can attach and watch.
			 */
			Gateway egressGW = findBestEgressGateway(IPv4Address.of(clientIP)); // TODO should base on where the video is located AND the client, not just the client...
			if (egressGW == null) {
				log.error("Could not locate a suitable egress Gateway for client IP {}", clientIP);
				response.put(JsonStrings.Add.Response.result, "5");
				response.put(JsonStrings.Add.Response.result_message, 
						"The GENI Cinema Service could not find a suitable gateway for you to attach to. The service might be overloaded. Please try again later or contact the admins if the problem persists.");
				return response;
			}
			log.debug("Found a suitable egress Gateway: {}", egressGW);

			/*
			 * Allocate a new VLCStreamServer on the egress Gateway.
			 */
			VLCStreamServer vlcss = getVLCSSOnGateway(egressGW);
			if (vlcss == null) {
				log.error("Could not allocate a VLCStreamServer on Gateway {}", egressGW.toString());
				response.put(JsonStrings.Add.Response.result, "6");
				response.put(JsonStrings.Add.Response.result_message, 
						"The GENI Cinema Service could not allocate a connection for you at your local gateway. The service might be overloaded. Please try again later or contact the admins if the problem persists.");
				return response;
			}
			log.debug("On egress Gateway {}, found an available VLCSS: {}", egressGW, vlcss);

			/*
			 * Resources are available. It's a home run from here. Lock down the OVS and egress gateway.
			 */
			if (requestedClientAsInt == -1) {
				requestedClientAsInt = generateClientId();
			}

			Node assignedNode = getSortSwitchForEgressGateway(egressGW);

			/*
			 * If there is not a sort node assigned, allocate one. This is merely here for debug purposes.
			 */
			if (!channel.sortNodeExists(assignedNode)) {
				log.debug("Client {} triggered adding new sort Node {} on this Channel.", requestedChannelAsInt, assignedNode.getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, assignedNode);
			} else {
				log.debug("Client {} added to existing sort Node {} on this Channel.", requestedChannelAsInt, assignedNode.getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, assignedNode);
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
			.setId(requestedClientAsInt)
			.setClient(vsb.build());
			EgressStream es = esb.build();
			log.debug("Pushing flows for EgressStream.");
			insertEgressStreamFlows(es, null);

			addEgressStream(es, egressGW);
			log.debug("All resources allocated for NEW EgressStream. EgressStream set:", es);

			response.put(JsonStrings.Add.Response.result, "0"); // success, but not really
			response.put(JsonStrings.Add.Response.result_message, 
					"Thanks for tuning in! Your initial Channel selection is " + es.getChannel().getId());
			response.put(JsonStrings.Watch.Response.channel_id, String.valueOf(es.getChannel().getId()));
			response.put(JsonStrings.Watch.Response.client_id, String.valueOf(es.getId()));
			response.put(JsonStrings.Watch.Response.description, es.getChannel().getDescription());
			response.put(JsonStrings.Watch.Response.gateway_ip, es.getVLCSSAtGateway().getEgress().getIP().toString());
			response.put(JsonStrings.Watch.Response.gateway_port, es.getVLCSSAtGateway().getEgress().getPort().toString());
			response.put(JsonStrings.Watch.Response.gateway_protocol, es.getVLCSSAtGateway().getEgress().getProtocol().toString());
			response.put(JsonStrings.Watch.Response.name, es.getChannel().getName());
			return response;

			/*
			 * Check if we are wanting to change Channels to the one we're watching already.
			 * If so, we can simply do nothing and return.
			 */
		} else if (existingStream.getChannel().getId() == requestedChannelAsInt) {
			log.debug("Client {} tried to change Channels to same Channel {}. Leaving same configuration.", existingStream.getId(), existingStream.getChannel().getId());
			response.put(JsonStrings.Add.Response.result, "0"); // success, but not really
			response.put(JsonStrings.Add.Response.result_message, 
					"The Channel specified is the Channel you are currently watching.");
			response.put(JsonStrings.Watch.Response.channel_id, String.valueOf(existingStream.getChannel().getId()));
			response.put(JsonStrings.Watch.Response.client_id, String.valueOf(existingStream.getId()));
			response.put(JsonStrings.Watch.Response.description, existingStream.getChannel().getDescription());
			response.put(JsonStrings.Watch.Response.gateway_ip, existingStream.getVLCSSAtGateway().getEgress().getIP().toString());
			response.put(JsonStrings.Watch.Response.gateway_port, existingStream.getVLCSSAtGateway().getEgress().getPort().toString());
			response.put(JsonStrings.Watch.Response.gateway_protocol, existingStream.getVLCSSAtGateway().getEgress().getProtocol().toString());
			response.put(JsonStrings.Watch.Response.name, existingStream.getChannel().getName());
			return response;

			/*
			 * Otherwise, the client is currently watching a Channel and would like to switch.
			 * (1) 	Use current egress GW and sort Node (as to preserve client socket).
			 * (2)	Add the client's ID and its sort Node to the new Channel.
			 *		This gives the Channel the client's info for processing in a bit.
			 * (3)	The previous Channel still has the client right now.
			 * (3a)	Insert new flows specifying both new and old EgressStreams.
			 * (3b)	Old Channel will be updated, removing the client after the flows are modified.
			 */
		} else {
			/*
			 * Check the existing Channel's sort Node assigned to this client. We will reuse it here, so
			 * check if it exists already in the new Channel (for another client perhaps). If this sort Node is not 
			 * present in the new Channel selected, assign it to the new Channel. This is merely here for debug purposes.
			 */
			if (!channel.sortNodeExists(existingStream.getChannel().getSortNode(existingStream.getId()))) {
				log.debug("Client {} triggered adding new sort Node {} on this Channel.", requestedChannelAsInt, 
						existingStream.getChannel().getSortNode(existingStream.getId()).getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, existingStream.getChannel().getSortNode(existingStream.getId()));
			} else {
				log.debug("Client {} added to existing sort Node {} on this Channel.", requestedChannelAsInt, 
						existingStream.getChannel().getSortNode(existingStream.getId()).getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, existingStream.getChannel().getSortNode(existingStream.getId()));
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

			response.put(JsonStrings.Add.Response.result, "0"); // success, but not really
			response.put(JsonStrings.Add.Response.result_message, 
					"You are now watching Channel " + existingStream.getChannel().getId());
			response.put(JsonStrings.Watch.Response.channel_id, String.valueOf(existingStream.getChannel().getId()));
			response.put(JsonStrings.Watch.Response.client_id, String.valueOf(existingStream.getId()));
			response.put(JsonStrings.Watch.Response.description, existingStream.getChannel().getDescription());
			response.put(JsonStrings.Watch.Response.gateway_ip, existingStream.getVLCSSAtGateway().getEgress().getIP().toString());
			response.put(JsonStrings.Watch.Response.gateway_port, existingStream.getVLCSSAtGateway().getEgress().getPort().toString());
			response.put(JsonStrings.Watch.Response.gateway_protocol, existingStream.getVLCSSAtGateway().getEgress().getProtocol().toString());
			response.put(JsonStrings.Watch.Response.name, existingStream.getChannel().getName());
			return response;
		}
	}

	@Override
	public synchronized String editChannel(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return "";
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

	private Node getSortSwitchForEgressGateway(Gateway egw) {
		for (Entry<Node, Gateway> entry : switchToEgressGatewayBindings.entrySet()) {
			if (entry.getValue().equals(egw)) {
				return entry.getKey();
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
	 * Takes an existing Channel and removes it from the Manager appropriately.
	 * 
	 * @param oldChannel, The Channel to remove
	 * @return, true upon success; false upon failure
	 */
	private boolean removeChannel(Channel oldChannel) {
		Aggregate theAggregate = lookupAggregate(oldChannel.getHostServer());

		if (theAggregate == null) {
			log.error("Could not find Aggregate for this Channel: {}", oldChannel.getId());
			return false;
		}

		/*
		 * Remove the Channel from the list.
		 */
		if (channelsPerAggregate.get(theAggregate.getName()).contains(oldChannel)) {
			oldChannel.getHostVLCStreamServer().setNotInUse();
			//`
			channelsPerAggregate.get(theAggregate.getName()).remove(oldChannel);
			return true;
		} else {
			/* 
			 * Not found. Keep the resources if there are any.
			 */
			return false;
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
	private boolean addEgressStream(EgressStream newEgressStream, Gateway egressGateway) {
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
			numAvailableVlcStreamsPerEgressGateway.put(egressGateway, numAvailableVlcStreamsPerEgressGateway.get(egressGateway).intValue() - 1);
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
			ArrayList<Gateway> egs = aggregate.getEgressGateways();
			if (!egs.isEmpty()) {
				/*
				 * An aggregate is in one physical location.
				 * Use the first public IP as a reference point.
				 */
				score = geoLocateScore(egs.get(0).getPublicIP(), clientIP); 
				if (score < bestScore) {
					bestScore = score;
					/*
					 * Now, determine the best GW at this aggregate.
					 */
					int mostAvailable = 0;
					for (Gateway eg : egs) {
						if (numAvailableVlcStreamsPerEgressGateway.get(eg).intValue() > mostAvailable) {
							mostAvailable = numAvailableVlcStreamsPerEgressGateway.get(eg).intValue();
							bestGW = eg;
						}
					}
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
					log.debug("Could be a race condition here... got groups={}, DPID={}", groups, sw.getId().toString());
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

	private void initializeRootSwitch(IOFSwitch sw) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<Node> aggRootSwitches = aggregate.getRootSwitches();
			for (Node node : aggRootSwitches) {
				if (node.getSwitchDpid().equals(sw.getId())) {
					log.debug("Found Root Tree Node with matching DPID {} in Aggregate {}. Adding FLOOD flows for switch.", sw.getId().toString(), aggregate.getName());
					OFFactory factory = sw.getOFFactory();
					ArrayList<OFInstruction> instructions = new ArrayList<OFInstruction>(1);
					ArrayList<OFAction> actions = new ArrayList<OFAction>(1);
					actions.add(factory.actions().buildOutput()
							.setMaxLen(0xffFFffFF)
							.setPort(OFPort.FLOOD)
							.build());
					instructions.add(factory.instructions().buildApplyActions()
							.setActions(actions)
							.build());
					OFFlowAdd floodIP = factory.buildFlowAdd()
							.setBufferId(OFBufferId.NO_BUFFER)
							.setPriority(FlowModUtils.PRIORITY_LOW)
							.setMatch(factory.buildMatch() // leave out input port to allow bidirectional flooding
									.setExact(MatchField.ETH_TYPE, EthType.IPv4)
									.build())
									.setInstructions(instructions)
									.setOutPort(OFPort.FLOOD)
									.build();
					sw.write(floodIP);
				}
			}
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

	private void insertNewChannelDropFlows(Channel newChannel) {
		if (!newChannel.getDemand()) { // check just to be safe
			OFFactory factory = switchService.getSwitch(newChannel.getHostServer().getOVSNode().getSwitchDpid()).getOFFactory();
			/*
			 * Construct the Match for UDP source port of the Channel.
			 * Build the OFFlowDelete with the Match and List<OFInstruction> (from above).
			 */
			OFFlowAdd disableFlow = factory.buildFlowAdd()
					.setMatch(
							factory.buildMatch()
							.setExact(MatchField.ETH_TYPE, EthType.IPv4)
							.setExact(MatchField.IP_PROTO, newChannel.getHostVLCStreamServer().getEgress().getProtocol())
							.setExact(MatchField.UDP_DST, newChannel.getHostVLCStreamServer().getEgress().getPort())
							.setExact(MatchField.IN_PORT, newChannel.getHostServer().getOVSNode().getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.build(); /* Do not set any actions --> DROP */

			log.debug("Writing OFFlowAdd to disable/drop Channel {} on UDP port {} out of the VLCS: " + disableFlow.toString(),
					newChannel.getId(), newChannel.getHostVLCStreamServer().getEgress().getPort().toString());

			switchService.getSwitch(newChannel.getHostServer().getOVSNode().getSwitchDpid()).write(disableFlow);
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
		ArrayList<OFAction> actionList;
		ArrayList<OFInstruction> instructionList;
		OFInstructionApplyActions applyActionsInstruction;

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
			actionList = new ArrayList<OFAction>(1);
			actionList.add(output);
			applyActionsInstruction = factory.instructions().buildApplyActions()
					.setActions(actionList)
					.build();
			instructionList = new ArrayList<OFInstruction>(1);
			instructionList.add(applyActionsInstruction);

			/*
			 * Construct the Match for UDP destination port of the Channel.
			 * Build the OFFlowAdd with the Match and List<OFInstruction> (from above).
			 */
			OFFlowModify enableFlow = factory.buildFlowModify()
					.setMatch(
							factory.buildMatch()
							.setExact(MatchField.ETH_TYPE, EthType.IPv4)
							.setExact(MatchField.IP_PROTO, newStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
							.setExact(MatchField.UDP_DST, newStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
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

			/*
			 * We used to have the flow to forward to the group here as well, but with multiple
			 * sort Nodes, we needs to insert this depending on a Channel's demand per sort node
			 * (which can be determined by the presence or absence of clients/buckets per Node).
			 */

		} // END IF DEMAND == FALSE (initial setup for Channel)

		if (newStream.getChannel().getDemand(newStream.getChannel().getSortNode(newStream.getId()))) {
			/* ********************************************
			 * NEXT, THE SORT OVS NODE'S DEFAULT-TABLE FLOW
			 * This should send the UDP-dst packet to the
			 * correct OFGroup, which will then duplicate.
			 * ********************************************/

			if (newStream.getChannel().getGroup() == OFGroup.ZERO || newStream.getChannel().getGroup() == null) {
				newStream.getChannel().setGroup(generateOFGroup());
			}

			factory = switchService.getSwitch(newStream.getChannel().getSortNode(newStream.getId()).getSwitchDpid()).getOFFactory();
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
							.setExact(MatchField.UDP_DST, newStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
							.setExact(MatchField.IN_PORT, newStream.getChannel().getSortNode(newStream.getId()).getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.setOutGroup(newStream.getChannel().getGroup())
							.setPriority(FlowModUtils.PRIORITY_MAX)
							.setInstructions(instructionList)
							.build(); /* Don't set the table-id --> will use default table */

			log.debug("Writing OFFlowAdd to send Channel {} on UDP port {} to OFGroup #" + newStream.getChannel().getGroup().getGroupNumber() + " : " + toGroupFlow.toString(),
					newStream.getChannel().getId(), newStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

			switchService.getSwitch(newStream.getChannel().getSortNode(newStream.getId()).getSwitchDpid()).write(toGroupFlow);
		}
		/* ***************************************
		 * NOW, CONFIGURE THE OFGROUP'S OFBUCKET'S
		 * via an OFGroupMod, which will allow a
		 * new bucket list to be added to the 
		 * OFGroup.
		 * **************************************/

		factory = switchService.getSwitch(newStream.getChannel().getSortNode(newStream.getId()).getSwitchDpid()).getOFFactory();

		//TODO assume right now that the VLCS already sent with the correct MAC and IP for the GCGW
		ArrayList<OFAction> bucketActions = new ArrayList<OFAction>(2);
		bucketActions.add(factory.actions().setField(factory.oxms().udpDst(newStream.getVLCSSAtGateway().getIngress().getPort()))); 
		bucketActions.add(factory.actions().output(newStream.getChannel().getSortNode(newStream.getId()).getEgressPort(), Integer.MAX_VALUE));

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
				.setBuckets(newStream.getChannel().getBucketList(newStream.getChannel().getSortNode(newStream.getId())))
				.build();

		log.debug("Writing OFGroupMod to send Channel {} on UDP port {} from OFGroup #" + newStream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString(),
				newStream.getChannel().getId(), newStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

		switchService.getSwitch(newStream.getChannel().getSortNode(newStream.getId()).getSwitchDpid()).write(groupMod);

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
			factory = switchService.getSwitch(oldStream.getChannel().getSortNode(oldStream.getId()).getSwitchDpid()).getOFFactory();

			/*
			 * Remember the sort Node. The new EgressStream's Channel's
			 * Node will actually be the same, since a client's attachment point never
			 * changes, but for clarity, we won't use the new EgressStream reference
			 * anywhere below.
			 */
			Node oldSortNode = oldStream.getChannel().getSortNode(oldStream.getId());
			oldStream.getChannel().removeClient(oldStream.getId());

			groupMod = factory.buildGroupModify()
					.setGroup(oldStream.getChannel().getGroup())
					.setGroupType(OFGroupType.ALL)
					.setBuckets(oldStream.getChannel().getBucketList(oldSortNode))
					.build();

			log.debug("Writing OFGroupMod to remove client {}'s OFBucket from OFGroup {}", 
					newStream.getChannel().getId(), oldStream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString());

			switchService.getSwitch(oldSortNode.getSwitchDpid()).write(groupMod);

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
								.setExact(MatchField.UDP_DST, oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
								.setExact(MatchField.IN_PORT, oldStream.getChannel().getHostServer().getOVSNode().getIngressPort())
								.build())
								.setBufferId(OFBufferId.NO_BUFFER)
								.build(); /* Do not set any actions --> DROP */

				log.debug("Writing OFFlowModify to disable/drop Channel {} on UDP port {} out of the VLCS: " + disableFlow.toString(),
						oldStream.getChannel().getId(), oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

				switchService.getSwitch(oldStream.getChannel().getHostServer().getOVSNode().getSwitchDpid()).write(disableFlow);

				/*
				 * Return the OFGroup to the pool.
				 */
				// TODO
				//oldStream.getChannel().setGroup(OFGroup.ZERO); 
				// this will force the Channel to ask for a new group next time, but it will not return it
				// we need a way for each sort switch to maintain a list of OFgroups applicable to it and
				// return the group to the list it was checked out from.
			}
			
			/*
			 * Now repeat for the specific host sort OVS Node
			 */
			if (!oldStream.getChannel().getDemand(oldSortNode)) {
				factory = switchService.getSwitch(oldSortNode.getSwitchDpid()).getOFFactory();

				OFFlowModify disableFlow = factory.buildFlowModify()
						.setMatch(
								factory.buildMatch()
								.setExact(MatchField.ETH_TYPE, EthType.IPv4)
								.setExact(MatchField.IP_PROTO, oldStream.getChannel().getHostVLCStreamServer().getEgress().getProtocol())
								.setExact(MatchField.UDP_DST, oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort())
								.setExact(MatchField.IN_PORT, oldSortNode.getIngressPort())
								.build())
								.setBufferId(OFBufferId.NO_BUFFER)
								.build(); /* Do not set any actions --> DROP */

				log.debug("Writing OFFlowModify to disable/drop Channel {} on UDP dst port {} out of the sort OVS Node: " + disableFlow.toString(),
						oldStream.getChannel().getId(), oldStream.getChannel().getHostVLCStreamServer().getEgress().getPort().toString());

				switchService.getSwitch(oldSortNode.getSwitchDpid()).write(disableFlow);
			}
			
			/*
			 * After using a potentially empty bucket list for composing the bucket mod,
			 * remove the Node from the Channel if it was this was the last client watching
			 * on that particular sort Node.
			 */
			oldStream.getChannel().removeSortNodeIfHasNoClients(oldSortNode);
		}
	}

	/**
	 * Used by the StreamGarbageCollector to get a handle on the manager. This
	 * isn't a true singleton, since the default constructor must be public for
	 * the ModuleLoader to instantiate the GENICinemaManager module.
	 * 
	 * @return The pseudo-singleton instance of the manager.
	 */
	protected static GENICinemaManager getInstance() {
		return instance;
	}

	/**
	 * Every client has a time field of when it was last modified. If the client
	 * has been dormant for X amount of time, return the client's resources to the
	 * pool of available resources so that another client can connect and use them.
	 * This is greedy and does not account for clients 
	 */
	protected synchronized void cleanUpOldClients() {

	}
}
