package net.floodlightcontroller.genicinema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
	private volatile static Map<String, ArrayList<Channel>> channelsPerAggregate;

	/* 1-to-1 Mapping b/t Switch and Egress GW */
	private volatile static Map<Node, Gateway> switchToEgressGatewayBindings;

	/* Ongoing Egress Streams */
	private volatile static Map<String, ArrayList<EgressStream>> egressStreamsPerAggregate;

	/* Ongoing Ingress Streams */
	private volatile static Map<Channel, IngressStream> channelToIngressStreamBindings;

	/* All available VLCSS per Server/Gateway */
	private volatile static Map<Server, ArrayList<VLCStreamServer>> vlcStreamsPerServer;
	private volatile static Map<Server, Integer> numAvailableVlcStreamsPerServer;
	private volatile static Map<Gateway, ArrayList<VLCStreamServer>> vlcStreamsPerEgressGateway;
	private volatile static Map<Gateway, Integer> numAvailableVlcStreamsPerEgressGateway;

	/* 1-to-1 Mapping b/t Ingress GW and Server */
	private volatile static Map<Gateway, Server> ingressGatewayToServerBindings;

	/* All GENI resources. This has turned into a single item and not a list as the code evolved (due to stitching). */
	private volatile static ArrayList<Aggregate> aggregates;

	/* All clients (EgressStream's) and Channels will have a unique ID. 
	 * TODO Perhaps should use UUID or something stronger. */
	private volatile static int clientIdGenerator = 0;
	private volatile static int channelIdGenerator = -1; /* Start at -1 so that the first "default" Channel is 0 */
	private volatile static int groupIDGenerator = 0;
	
	/* Only add the default channel once, even if something strange happens and a switch disconnects and reconnects. */
	private volatile static boolean defaultChannelAdded = false;

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
		aggregates = new ArrayList<Aggregate>(2);
		channelsPerAggregate = new ConcurrentHashMap<String, ArrayList<Channel>>(255); // for initial test, start of with a predefined two channels
		channelToIngressStreamBindings = new ConcurrentHashMap<Channel, IngressStream>(255); // --> need two ingress streams
		egressStreamsPerAggregate = new ConcurrentHashMap<String, ArrayList<EgressStream>>(1000); // "large" number of clients possible
		vlcStreamsPerServer = new ConcurrentHashMap<Server, ArrayList<VLCStreamServer>>(100);
		numAvailableVlcStreamsPerServer = new ConcurrentHashMap<Server, Integer>(100);
		vlcStreamsPerEgressGateway = new ConcurrentHashMap<Gateway, ArrayList<VLCStreamServer>>(100);
		numAvailableVlcStreamsPerEgressGateway = new ConcurrentHashMap<Gateway, Integer>(100);
		switchToEgressGatewayBindings = new ConcurrentHashMap<Node, Gateway>(5); // this is for telling which OVS corresponds to which egress GW and vice versa
		ingressGatewayToServerBindings = new ConcurrentHashMap<Gateway, Server>(2); // we assume a 1-to-1 mapping

		/*
		 * Set up the topology. TODO We should have a way to define this in an XML file or something...
		 */

		ArrayList<Gateway> igws = new ArrayList<Gateway>(2);
		ArrayList<Server> servers = new ArrayList<Server>(2);

		/*
		 * Ingress GW 1
		 */
		Gateway ingress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.1"))
		.setPublicIP(IPv4Address.of("130.127.215.170"))
		.build();
		igws.add(ingress_gw);

		/*
		 * Server 1
		 */
		Node server_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:11")) 
		.addIngressPort(OFPort.LOCAL)
		.addEgressPort(OFPort.of(1)) // should be correct now
		.build();
		Server server = new Server.ServerBuilder()
		.setPrivateIP(ingress_gw.getPrivateIP()) // for initial test, server will use public IP as ingress GW
		.setPublicIP(ingress_gw.getPublicIP())
		.setOVSNode(server_ovs)
		.build();
		servers.add(server);
		vlcStreamsPerServer.put(server, new ArrayList<VLCStreamServer>());
		ingressGatewayToServerBindings.put(ingress_gw, server);

		/*
		 * Ingress GW 2
		 *
		ingress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.1"))
		.setPublicIP(IPv4Address.of("130.127.215.171"))
		.build();
		igws.add(ingress_gw);

		/*
		 * Server 2
		 *
		server_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:22")) 
		.addIngressPort(OFPort.LOCAL)
		.addEgressPort(OFPort.of(1)) // should be correct now
		.build();
		server = new Server.ServerBuilder()
		.setPrivateIP(ingress_gw.getPrivateIP()) // for initial test, server will use public IP as ingress GW
		.setPublicIP(ingress_gw.getPublicIP())
		.setOVSNode(server_ovs)
		.build();
		servers.add(server);
		vlcStreamsPerServer.put(server, new ArrayList<VLCStreamServer>());
		ingressGatewayToServerBindings.put(ingress_gw, server);

		/*
		 * This is the root OVS.
		 * It will input from 1 VLC Server and output to all ports.
		 * Just FLOOD all IP and ARP packets.
		 */
		Node root_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:11:11"))
		.addIngressPort(OFPort.of(1)) 
		//.addIngressPort(OFPort.of(2))
		.addEgressPort(OFPort.of(3))
		.addEgressPort(OFPort.of(4))
		.addEgressPort(OFPort.of(5))
		.addEgressPort(OFPort.of(6))
		.addEgressPort(OFPort.of(7))
		.build();

		/*
		 * Switch 1
		 */
		ArrayList<Node> ovss = new ArrayList<Node>(5);
		Node ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:22:11"))
		.addIngressPort(OFPort.of(1))
		.addEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 1
		 */
		ArrayList<Gateway> egws = new ArrayList<Gateway>(5);
		Gateway egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.1"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 2
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:22:22"))
		.addIngressPort(OFPort.of(1))
		.addEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 2
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.2"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 3
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:22:33"))
		.addIngressPort(OFPort.of(1))
		.addEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 3
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.3"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 4
		 */
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:22:44"))
		.addIngressPort(OFPort.of(1))
		.addEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 4
		 */
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.4"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw);

		/*
		 * Switch 5
		 *
		ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:22:55"))
		.addIngressPort(OFPort.of(1))
		.addEgressPort(OFPort.of(2))
		.build();
		ovss.add(ovs_switch);
		/*
		 * Egress GW 5
		 *
		egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.10.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.5"))
		.build();
		egws.add(egress_gw);
		vlcStreamsPerEgressGateway.put(egress_gw, new ArrayList<VLCStreamServer>());
		switchToEgressGatewayBindings.put(ovs_switch, egress_gw); */

		Aggregate clemson = new Aggregate.AggregateBuilder()
		.addServers(servers)
		.setIngressGateways(igws)
		.setEgressGateways(egws)
		.addRootSwitch(root_ovs)
		.setSwitches(ovss)
		.setName("Clemson")
		.setDescription("The GENI Cinema resources.")
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
		int allowedSockets = 100;

		/*
		 * Do the Ingress GWs' (Y of these)
		 */
		for (Server s : clemson.getServers()) {
			for (int i = 0; i < allowedSockets; i++) {
				vsb.setIP(s.getPublicIP())
				.setPort(TransportPort.of(tcpPort++)) // will need to set this port
				.setProtocol(IpProtocol.TCP);
				pubSock = vsb.build();
				vsb.setIP(s.getPrivateIP())
				.setPort(TransportPort.of(udpPort++)) 
				.setProtocol(IpProtocol.UDP);
				privSock = vsb.build();

				vlcssb.setIngress(pubSock)
				.setEgress(privSock);
				vlcStreamsPerServer.get(s).add(vlcssb.build());
			}
			tcpPort += 100;
			udpPort += 100;
			numAvailableVlcStreamsPerServer.put(s, allowedSockets);
		}

		/*
		 * Do the Egress GWs' (X of these)
		 */
		for (Gateway egw : clemson.getEgressGateways()) {
			udpPort = 32000;
			tcpPort = 33000;
			for (int i = 0; i < allowedSockets; i++) {
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
			numAvailableVlcStreamsPerEgressGateway.put(egw, allowedSockets);
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
		log.trace("Switch {} connected. Checking if it's a sort switch.", switchId.toString());
		initializeSortSwitchOFGroups(switchService.getSwitch(switchId));

		log.trace("Switch {} connected. Removing any existing UDP flows if it's a GENI Cinema switch.", switchId.toString());
		removeExistingUDPFlows(switchService.getSwitch(switchId));

		log.trace("Switch {} connected. Adding FLOOD flows if it's in the root tree.", switchId.toString());
		initializeRootSwitch(switchService.getSwitch(switchId));

		/*
		 * For all aggregates, find the switch and set it as connected.
		 * switchConnected(DatapathId) will set the switch as connected if
		 * it exists in the Aggregate's configuration (lists of sort and 
		 * enable switches). If the switch does not exist, no change will
		 * be made.
		 */
		for (Aggregate aggregate : aggregates) {
			boolean result = aggregate.switchConnected(switchId);
			if (result) {
				log.debug("Switch {} connected in Aggregate {}.", switchId.toString(), aggregate.getName());
			} else {
				log.debug("Switch {} does not belong to Aggregate {}.", switchId.toString(), aggregate.getName());
			}
		}

		boolean readyToRock = true;
		for (Aggregate aggregate : aggregates) {
			if (aggregate.allSwitchesConnected() == false) {
				readyToRock = false;
				break;
			}
		}

		if (readyToRock && !defaultChannelAdded) {
			log.info("All switches connected. Ready to rock!");
			/*
			 * Lastly, setup the default splash screen Channel.
			 * A null ClientInfo implies it's the default Channel.
			 */
			defaultChannelAdded = true;
			addChannel("{\"" + JsonStrings.Add.Request.name + "\":\"Default\"," +
					"\"" + JsonStrings.Add.Request.description + "\":\"The GENI Cinema Splash Screen.\"," +
					"\"" + JsonStrings.Add.Request.view_password + "\":\"\"," + /* set a password so that people can't mess with it */
					"\"" + JsonStrings.Add.Request.admin_password + "\":\"g3n1-r0ck5!!\"}", null);
		}	
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		for (Aggregate aggregate : aggregates) {
			boolean result = aggregate.switchDisconnected(switchId);
			if (result) {
				log.debug("SWITCH {} DISCONNECTED IN AGGREGATE {}. This is not good. Is the control network okay?", switchId.toString(), aggregate.getName());
			} else {
				log.debug("Switch {} disconnected, but does not belong to Aggregate {}.", switchId.toString(), aggregate.getName());
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
			if (!aggregate.allSwitchesConnected()) {
				Map<String, String> keyValue = new HashMap<String, String>(2);
				keyValue.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				keyValue.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				response.add(keyValue);
				return response;
			}
			
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
						channelInfo.put(JsonStrings.Query.Response.demand, String.valueOf(channel.getDemandCount()));
						response.add(channelInfo);
					}
				}
			}
		}

		Map<String, String> result = new HashMap<String, String>();
		if (response.isEmpty()) {
			result.put(JsonStrings.Result.result_code, JsonStrings.Result.NoChannelsAvailable.code);
			result.put(JsonStrings.Result.result_message, JsonStrings.Result.NoChannelsAvailable.message);
			response.add(result);
		} else {
			result.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
			result.put(JsonStrings.Result.result_message, JsonStrings.Result.Complete.message);
			response.add(result);
		}

		return response;
	}

	@Override
	public synchronized Map<String, ArrayList<String>> getGatewayInfo() {
		Map<String, ArrayList<String>> response = new HashMap<String, ArrayList<String>>();
		ArrayList<String> ingressIPs = new ArrayList<String>();
		ArrayList<String> egressIPs = new ArrayList<String>();

		Set<Gateway> ingressGateways = ingressGatewayToServerBindings.keySet();
		Set<Gateway> egressGateways = vlcStreamsPerEgressGateway.keySet();

		/*
		 * List all possible ingress IPs.
		 */
		for (Gateway gw : ingressGateways) {
			ingressIPs.add(gw.getPublicIP().toString()); /* will format in dotted-decimal */
		}
		/*
		 * List all possible egress IPs.
		 */
		for (Gateway gw : egressGateways) {
			egressIPs.add(gw.getPublicIP().toString()); /* will format in dotted-decimal */
		}

		response.put(JsonStrings.GatewayInfo.Response.ingress_gateways, ingressIPs);
		response.put(JsonStrings.GatewayInfo.Response.egress_gateways, egressIPs);

		return response;
	}

	@Override
	public synchronized Map<String, String> clientDisconnect(String json, ClientInfo clientInfo) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		int reqFields = 0;
		String json_clientId = "";

		Map<String, String> response = new HashMap<String, String>();
		
		/*
		 * Verify the switches are connected to the controller.
		 */
		for (Aggregate aggregate : aggregates) {
			if (!aggregate.allSwitchesConnected()) {
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				return response;
			}
		}

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
				case JsonStrings.Disconnect.Request.client_id:
					json_clientId = jp.getText().trim();
					reqFields++;
					break;
				default:
					log.error("Got unmatched JSON string in Watch-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException | NullPointerException e) { /* a malformed POST might have no string at all */
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Add request. If not, bail out.
		 */
		if (reqFields < 1) {
			log.error("Did not receive all expected JSON fields in Disconnect request! Only got {} matches. CLIENT STILL CONNECTED.", reqFields);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IncorrectJsonFields.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IncorrectJsonFields.message);
			return response;
		}

		/*
		 * Lookup the client to get it's EgressStream.
		 */
		int requestedClientAsInt = -1;
		try {
			requestedClientAsInt = Integer.parseInt(json_clientId);
		} catch (NumberFormatException e) {
			log.error("Could not parse specified Client ID '{}'.", json_clientId);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIdParseError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIdParseError.message);
			return response;
		}

		EgressStream existingStream = lookupClient(requestedClientAsInt);
		if (existingStream == null) {
			log.error("Could not locate client information for specified client ID {}.", json_clientId);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIdNotFound.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIdNotFound.message);
			return response;
		} else {
			/*
			 * Remove client by removing its bucket from the group for the Channel.
			 * This is essentially the same as a Channel switch, but the new Channel
			 * is never added.
			 */
			log.debug("Removing all client flows from Channel {} upon Client {} disconnect", existingStream.getChannel().getId(), existingStream.getId());
			removeEgressStreamFlows(existingStream);

			//TODO this is messy, but lookup the EgressGateway given the client's VLCStreamServer
			Gateway egressGateway = null;
			for (Entry<Gateway, ArrayList<VLCStreamServer>> entry: vlcStreamsPerEgressGateway.entrySet()) {
				if (entry.getValue().contains(existingStream.getVLCSSAtGateway())) {
					egressGateway = entry.getKey();
					break;
				}
			}
			if (egressGateway == null) {
				log.error("Could not lookup egress Gateway from VLCStreamServer. Is GC in an inconsistent state?");
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.EgressGatewayNotFound.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.EgressGatewayNotFound.message);
				return response;
			} else {
				/*
				 * Now, restore resources to the manager.
				 */
				removeEgressStream(existingStream, egressGateway);

				response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.Complete.message);
				return response;
			}			
		}
	}

	@Override
	public synchronized Map<String, String> addChannel(String json, ClientInfo clientInfo) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		ChannelBuilder cb = new ChannelBuilder();

		Map<String, String> response = new HashMap<String, String>();
		
		/*
		 * Verify the switches are connected to the controller.
		 */
		for (Aggregate aggregate : aggregates) {
			if (!aggregate.allSwitchesConnected()) {
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				return response;
			}
		}

		/* 
		 * This will presumably get us the client IP of the most recent hop.
		 * e.g. If NAT is involved between us and the client, this should
		 * give us the public-facing IP of the NAT server, which is what we
		 * will directly communicate with over the Internet.
		 */
		String clientIP;
		if (clientInfo == null) {
			clientIP = "0.0.0.0";
			log.debug("ADDING DEFAULT CHANNEL / SPLASH SCREEN.");
		} else {
			clientIP = clientInfo.getAddress(); 
			if (clientIP.equals("0.0.0.0")) {
				log.error("RECEIVED A CLIENT_INFO IP OF 0.0.0.0. This should NEVER happen and might mess up the default Channel! Aborting Channel ADD.");
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIpAllZeros.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIpAllZeros.message);
				return response;
			}
		}

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
		} catch (IOException | NullPointerException e) { /* a malformed POST might have no string at all */
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Add request. If not, bail out.
		 */
		if (reqFields < 4) {
			log.error("Did not receive all expected JSON fields in Add request! Only got {} matches. CHANNEL NOT ADDED.", reqFields);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IncorrectJsonFields.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IncorrectJsonFields.message);
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
			log.error("Could not parse client IP {} in JSON request to ADD a Channel.", clientIP);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIpParseError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIpParseError.message);
			return response;
		}

		Gateway ingressGW = findBestIngressGateway(clientIPconverted);		
		if (ingressGW == null) {
			log.error("Could not find an available GENI Cinema Ingress Gateway for the client with IP {}", clientIP);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IngressGatewayUnavailable.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IngressGatewayUnavailable.message);
			return response;
		}

		/*
		 * Check to see if there are any VLC servers with sockets available
		 * that are reachable from the Gateway.
		 */
		Server hostServer = findBestHostServer(ingressGW);

		VLCStreamServer hostServerVLCSS = getVLCSSOnHostServer(hostServer);		
		if (hostServerVLCSS ==  null) {
			log.error("Could not find an available VLCStreamServer (i.e. an available VLC listen socket) for the client to stream to.");
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IngressVLCStreamServerUnavailable.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IngressVLCStreamServerUnavailable.message);
			return response;
		}

		/*
		 * Create the video-producing client.
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
		if (!addChannel(theChannel, theStream)) {
			log.error("Could not add new Channel to the Manager!");
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelAddError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelAddError.message);
			return response;
		}

		insertNewChannelDropFlows(theChannel);

		response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
		response.put(JsonStrings.Result.result_message, 
				"Channel has been successfully added to the GENI Cinema Service. Initiate your stream to make the channel available to viewers.");
		response.put(JsonStrings.Add.Response.admin_password, theChannel.getAdminPassword());
		response.put(JsonStrings.Add.Response.channel_id, Integer.toString(theChannel.getId()));
		response.put(JsonStrings.Add.Response.description, theChannel.getDescription());
		response.put(JsonStrings.Add.Response.gateway_ip, theStream.getGateway().getPublicIP().toString());
		response.put(JsonStrings.Add.Response.gateway_port, theStream.getVLCSServer().getIngress().getPort().toString());
		response.put(JsonStrings.Add.Response.name, theChannel.getName());
		response.put(JsonStrings.Add.Response.view_password, theChannel.getViewPassword());
		return response;

	}

	@Override
	public synchronized Map<String, String> removeChannel(String json, ClientInfo clientInfo) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		String json_adminPass = "";
		String json_channelId = "";

		Map<String, String> response = new HashMap<String, String>();
		
		/*
		 * Verify the switches are connected to the controller.
		 */
		for (Aggregate aggregate : aggregates) {
			if (!aggregate.allSwitchesConnected()) {
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				return response;
			}
		}

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
					json_adminPass = jp.getText();
					reqFields++;
					break;
				case JsonStrings.Remove.Request.channel_id:
					json_channelId = jp.getText().trim();
					reqFields++;
					break;
				default:
					log.error("Got unmatched JSON string in Remove-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException | NullPointerException e) { /* a malformed POST might have no string at all */
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Remove request. If not, bail out.
		 */
		if (reqFields < 2) {
			log.error("Did not receive all expected JSON fields in Remove request! Only got {} matches. CHANNEL NOT REMOVED.", reqFields);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IncorrectJsonFields.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IncorrectJsonFields.message);
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
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdParseError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdParseError.message);
			return response;
		}

		Channel channel = lookupChannel(requestedChannelAsInt);
		if (channel == null) {
			log.error("Could not locate specified Channel ID '{}'.", requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdUnavailable.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdUnavailable.message);
			return response;
		}

		/*
		 * Verify the client/producer has provided the correct administrative password.
		 */
		if (!json_adminPass.equals(channel.getAdminPassword())) {
			log.error("Admin password '{}' is incorrect for Channel ID '{}' with AP of '" + channel.getAdminPassword() + "'.", 
					json_adminPass, requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.AdminPasswordIncorrect.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.AdminPasswordIncorrect.message);
			return response;
		}

		/*
		 * Remove all clients from the Channel we're removing.
		 * We will automatically switch them over to a default "not available" Channel.
		 * (Hopefully they wont get too upset...)
		 * 
		 * TODO this will modify the list of buckets for that Channel many times...can we do all at once maybe?
		 */
		Aggregate aggregate = lookupAggregate(channel);
		Channel defaultChannel = lookupChannel(0);
		for (EgressStream es : egressStreamsPerAggregate.get(aggregate.getName())) {
			if (es.getChannel().equals(channel)) {
				/*
				 * Found a Client we need to move to the default Channel.
				 */
				watchChannel("{\"" + JsonStrings.Watch.Request.client_id + "\":\"" + es.getId() + "\"" +
						",\"" + JsonStrings.Watch.Request.view_password + "\":\"" + defaultChannel.getViewPassword() + "\"" +
						",\"" + JsonStrings.Watch.Request.channel_id + "\":\"" + defaultChannel.getId() + "\"}", null);
			}
		}

		/*
		 * Update the manager after we've switched over all the clients.
		 * Removing the Channel will also remove the associated IngressStream
		 */
		removeChannel(channel);

		response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
		response.put(JsonStrings.Result.result_message, JsonStrings.Result.Complete.message);
		return response;
	}

	@Override
	public synchronized Map<String, String> watchChannel(String json, ClientInfo clientInfo) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		int reqFields = 0;
		String json_clientId = "";
		String json_channelId = "";
		String json_viewPass = "";

		Map<String, String> response = new HashMap<String, String>();
		
		/*
		 * Verify the switches are connected to the controller.
		 */
		for (Aggregate aggregate : aggregates) {
			if (!aggregate.allSwitchesConnected()) {
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				return response;
			}
		}

		String clientIP;
		if (clientInfo == null) {
			clientIP = "0.0.0.0";
			log.debug("Automatically switching Client to splash Channel.");
		} else {
			clientIP = clientInfo.getAddress(); 
			if (clientIP.equals("0.0.0.0")) {
				log.error("RECEIVED A CLIENT_INFO IP OF 0.0.0.0. This should NEVER happen and might mess up the default Channel! Aborting Channel WATCH.");
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIpAllZeros.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIpAllZeros.message);
				return response;
			}
		}

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
		} catch (IOException | NullPointerException e) { /* a malformed POST might have no string at all */
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Add request. If not, bail out.
		 */
		if (reqFields < 3) {
			log.error("Did not receive all expected JSON fields in Watch request! Only got {} matches. CHANNEL NOT CHANGED.", reqFields);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IncorrectJsonFields.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IncorrectJsonFields.message);
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
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdParseError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdParseError.message);
			return response;
		}

		Channel channel = lookupChannel(requestedChannelAsInt);
		if (channel == null) {
			log.error("Could not locate specified Channel ID '{}'.", requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdUnavailable.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdUnavailable.message);
			return response;
		}

		/*
		 * Verify the client has provided the correct password.
		 */
		if (!json_viewPass.equals(channel.getViewPassword())) {
			log.error("View password '{}' is incorrect for Channel ID '{}' with VP of '" + channel.getViewPassword() + "'.", 
					json_viewPass, requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ViewPasswordIncorrect.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ViewPasswordIncorrect.message);
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
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIdParseError.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIdParseError.message);
				return response;
			}
		}

		EgressStream existingStream;
		if (requestedClientAsInt != -1) {
			existingStream = lookupClient(requestedClientAsInt);
		} else {
			existingStream = null;
		}
		
		/*
		 * If client ID was provided but the client was not found,
		 * report an error, since the web server is in an inconsistent
		 * state.
		 */
		if (requestedClientAsInt != -1 && existingStream == null) {
			log.error("Cannot select new Channel because Client ID '{}' does not exist.", json_clientId);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ClientIdNotFound.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ClientIdNotFound.message);
			return response;
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
			Gateway egressGW = findBestEgressGateway(IPv4Address.of(clientIP));
			if (egressGW == null) {
				log.error("Could not locate a suitable egress Gateway for client IP {}", clientIP);
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.EgressGatewayUnavailable.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.EgressGatewayUnavailable.message);
				return response;
			}
			log.debug("Found a suitable egress Gateway: {}", egressGW);

			/*
			 * Allocate a new VLCStreamServer on the egress Gateway.
			 */
			VLCStreamServer vlcss = getVLCSSOnGateway(egressGW);
			if (vlcss == null) {
				log.error("Could not allocate a VLCStreamServer on Gateway {}", egressGW.toString());
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.EgressVLCStreamServerUnavailable.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.EgressVLCStreamServerUnavailable.message);
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
				log.debug("Client {} triggered adding new sort Node {} on this Channel.", requestedClientAsInt, assignedNode.getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, assignedNode);
			} else {
				log.debug("Client {} added to existing sort Node {} on this Channel.", requestedClientAsInt, assignedNode.getSwitchDpid().toString());
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

			response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
			response.put(JsonStrings.Result.result_message, "Thanks for tuning in! Your initial Channel selection is " + es.getChannel().getId());
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
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
			response.put(JsonStrings.Result.result_message, "The Channel specified is the Channel you are currently watching.");
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
				log.debug("Client {} triggered adding new sort Node {} on this Channel.", requestedClientAsInt, 
						existingStream.getChannel().getSortNode(existingStream.getId()).getSwitchDpid().toString());
				channel.addClient(requestedClientAsInt, existingStream.getChannel().getSortNode(existingStream.getId()));
			} else {
				log.debug("Client {} added to existing sort Node {} on this Channel.", requestedClientAsInt, 
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

			response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
			response.put(JsonStrings.Result.result_message, 
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
	public synchronized Map<String, String> editChannel(String json, ClientInfo clientInfo) {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;

		String json_adminPass = "";
		String json_adminPassNew = "";
		String json_viewPass = "";
		String json_description = "";
		String json_name = "";
		String json_channelId = "";
		
		boolean gotAdminPass = false;
		boolean gotChannelId = false;
		boolean gotNewAdminPass = false;
		boolean gotNewDescription = false;
		boolean gotNewName = false;
		boolean gotNewViewPass = false;

		Map<String, String> response = new HashMap<String, String>();
		
		/*
		 * Verify the switches are connected to the controller.
		 */
		for (Aggregate aggregate : aggregates) {
			if (!aggregate.allSwitchesConnected()) {
				response.put(JsonStrings.Result.result_code, JsonStrings.Result.SwitchesNotReady.code);
				response.put(JsonStrings.Result.result_message, JsonStrings.Result.SwitchesNotReady.message);
				return response;
			}
		}

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
				case JsonStrings.Modify.Request.admin_password:
					json_adminPass = jp.getText();
					gotAdminPass = true;
					break;
				case JsonStrings.Modify.Request.new_admin_password:
					json_adminPassNew = jp.getText();
					gotNewAdminPass = true;
					break;
				case JsonStrings.Modify.Request.new_view_password:
					json_viewPass = jp.getText();
					gotNewViewPass = true;
					break;
				case JsonStrings.Modify.Request.new_name:
					json_name = jp.getText();
					gotNewName = true;
					break;
				case JsonStrings.Modify.Request.new_description:
					json_description = jp.getText();
					gotNewDescription = true;
					break;
				case JsonStrings.Modify.Request.channel_id:
					json_channelId = jp.getText().trim();
					gotChannelId = true;
					break;
				default:
					log.error("Got unmatched JSON string in Remove-Channel input: {} : {}", n, jp.getText());
				}
			}
		} catch (IOException | NullPointerException e) { /* a malformed POST might have no string at all */
			log.error(e.getMessage());
		}

		/*
		 * Check to make sure all expected/required JSON fields 
		 * were received in the Modify request. If not, bail out.
		 */
		if (!gotAdminPass || !gotChannelId) {
			log.error("Did not receive expected JSON fields (admin pass and channel ID) in Modify request! CHANNEL NOT MODIFIED.");
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.IncorrectJsonFields.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.IncorrectJsonFields.message);
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
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdParseError.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdParseError.message);
			return response;
		}

		Channel channel = lookupChannel(requestedChannelAsInt);
		if (channel == null) {
			log.error("Could not locate specified Channel ID '{}'.", requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.ChannelIdUnavailable.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.ChannelIdUnavailable.message);
			return response;
		}

		/*
		 * Verify the client/producer has provided the correct administrative password.
		 */
		if (!json_adminPass.equals(channel.getAdminPassword())) {
			log.error("Admin password '{}' is incorrect for Channel ID '{}' with AP of '" + channel.getAdminPassword() + "'.", 
					json_adminPass, requestedChannelAsInt);
			response.put(JsonStrings.Result.result_code, JsonStrings.Result.AdminPasswordIncorrect.code);
			response.put(JsonStrings.Result.result_message, JsonStrings.Result.AdminPasswordIncorrect.message);
			return response;
		}

		/*
		 * Make requested modifications.
		 */
		if (gotNewAdminPass) {
			log.debug("Resetting admin password of Channel {} with '{}'.", json_channelId, json_adminPassNew);
			channel.resetAdminPassword(json_adminPassNew);
		}
		if (gotNewViewPass) {
			log.debug("Resetting view password of Channel {} with '{}'.", json_channelId, json_viewPass);
			channel.resetViewPassword(json_viewPass);
		}
		if (gotNewDescription) {
			log.debug("Resetting description of Channel {} with '{}'.", json_channelId, json_description);
			channel.resetDescription(json_description);
		}
		if (gotNewName) {
			log.debug("Resetting name of Channel {} with '{}'.", json_channelId, json_name);
			channel.resetName(json_name);
		}

		response.put(JsonStrings.Modify.Response.channel_id, String.valueOf(channel.getId()));
		response.put(JsonStrings.Modify.Response.new_admin_password, channel.getAdminPassword());
		response.put(JsonStrings.Modify.Response.new_description, channel.getDescription());
		response.put(JsonStrings.Modify.Response.new_name, channel.getName());
		response.put(JsonStrings.Modify.Response.new_view_password, channel.getViewPassword());
		response.put(JsonStrings.Result.result_code, JsonStrings.Result.Complete.code);
		response.put(JsonStrings.Result.result_message, JsonStrings.Result.Complete.message);
		return response;
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

	/*private Aggregate lookupAggregate(IngressStream whereAmI) {
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
	}*/

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
	private boolean addChannel(Channel newChannel, IngressStream newIngressStream) {
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
			channelToIngressStreamBindings.put(newChannel, newIngressStream);
			numAvailableVlcStreamsPerServer.put(newChannel.getHostServer(), numAvailableVlcStreamsPerServer.get(newChannel.getHostServer()).intValue() - 1);
			return true;
		}
	}

	/**
	 * Takes an existing Channel and removes it from the Manager appropriately.
	 * 
	 * This will not disconnect all clients, so be sure to remove their flows
	 * linked to this Channel prior to calling this function.
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
			channelsPerAggregate.get(theAggregate.getName()).remove(oldChannel);
			channelToIngressStreamBindings.remove(oldChannel);
			numAvailableVlcStreamsPerServer.put(oldChannel.getHostServer(), numAvailableVlcStreamsPerServer.get(oldChannel.getHostServer()).intValue() + 1);
			return true;
		} else {
			/* 
			 * Not found. Keep the resources if there are any.
			 */
			return false;
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
	 * Takes an expired EgressStream and updates the Manager appropriately.
	 * 
	 * @param oldEgressStream, The recently-removed EgressStream
	 * @return, true upon success; false upon failure
	 */
	private boolean removeEgressStream(EgressStream oldEgressStream, Gateway egressGateway) {
		Aggregate theAggregate = lookupAggregate(oldEgressStream.getVLCSSAtGateway());

		/*
		 * Remove the EgressStream from the list.
		 */
		if (!egressStreamsPerAggregate.containsKey(theAggregate.getName())) {
			return false;
		} else if (!egressStreamsPerAggregate.get(theAggregate.getName()).contains(oldEgressStream)) {
			return false;
		} else {
			/*
			 * Give back resources.
			 */
			oldEgressStream.getVLCSSAtGateway().setNotInUse();
			egressStreamsPerAggregate.get(theAggregate.getName()).remove(oldEgressStream);
			numAvailableVlcStreamsPerEgressGateway.put(egressGateway, numAvailableVlcStreamsPerEgressGateway.get(egressGateway).intValue() + 1);
			return true;
		}
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

		return commonBits.getInt();
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
		Gateway closestGW = null;
		Gateway lightestGW = null;
		int score;
		int mostAvailable = 0;

		/*
		 * For simplicity, always place the splash/default Channel
		 * on the 0th aggregate and it's 0th ingress Gateway.
		 */
		if (clientIP.compareTo(IPv4Address.FULL_MASK) == 0) {
			return aggregates.get(0).getIngressGateways().get(0);
		}

		for (Aggregate aggregate : aggregates) {
			ArrayList<Gateway> igs = aggregate.getIngressGateways();
			for (Gateway ig : igs) {
				score = geoLocateScore(ig.getPublicIP(), clientIP);
				if (score < bestScore) {
					bestScore = score;
					closestGW = ig;
				}
				Server server = ingressGatewayToServerBindings.get(ig);
				if (numAvailableVlcStreamsPerServer.get(server).intValue() > mostAvailable) {
					mostAvailable = numAvailableVlcStreamsPerServer.get(server).intValue();
					lightestGW = ig;
				}
			}

		}

		/*
		 * TODO/FIXME How to best:
		 * (1) determine the quickest/best/closest route to an ingress GW?
		 * (2) balance between the closest ingress GW and the most lightly-loaded?
		 */
		
		return lightestGW;
	}

	/**
	 * Based on the ingress Gateway, determine which Server the video feed
	 * should be routed to and hosted on.
	 * 
	 * This just returns the associated 1-to-1 mapped Server.
	 * 
	 * @param ingressGW, The Gateway serving as the entry-point in the GC network.
	 * @return The Server the IngressStream should be stored on, or null upon failure.
	 */
	private Server findBestHostServer(Gateway ingressGW) {
		return ingressGatewayToServerBindings.get(ingressGW);
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

	/**
	 * For use when connecting a new client. Will always
	 * return a "new" integer that has not been assigned
	 * to a client (EgressStream). The assumption is that we 
	 * will never run long enough for or ever reach the max
	 * value of an integer.
	 * 
	 * TODO Similar the the groups, a pool should be created
	 * to allocate and return EgressStream IDs.
	 * 
	 * @return The next integer for a client. Will be unique (within reason).
	 */
	private int generateClientId() {
		return ++clientIdGenerator;
	}

	/**
	 * For use when connecting a new Channel. Will always
	 * return a "new" integer that has not been assigned
	 * to a Channel. The assumption is that we will never
	 * run long enough for or ever reach the max value of
	 * an integer.
	 * 
	 * TODO Similar the the groups, a pool should be created
	 * to allocate and return Channel IDs.
	 * 
	 * @return The next integer for a Channel. Will be unique.
	 */
	private int generateChannelId() {
		return ++channelIdGenerator;
	}

	/**
	 * For use when connecting a new Channel. Will always
	 * return a new OFGroup that has not been assigned
	 * to a Channel.
	 * 
	 * FIXME The fatal flaw here is that if the controller
	 * runs long enough, we will reach the group limit, 
	 * from 1 to 255 I believe. We need to turn this into
	 * a stack/dequeue to allow pushing and popping groups.
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
					log.debug("Found Node with matching DPID {} in Aggregate {}. Creating OFGroupAdd's for switch.", sw.getId().toString(), aggregate.getName());

					for (OFGroup group : groups) {
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
	 * The GENI Cinema network topology assumes a central root switch
	 * serving as the bridge between the ingress Gateway(s) and the
	 * egress Gateway(s). This switch actually acts like a hub and simply
	 * rebroadcasts the video streams.
	 * TODO need to insert flows here to reduce needless videos entering each
	 * branch, which could be detrimental to performance given a large number
	 * of IngressStreams with demand.
	 * 
	 * @param sw, the switch serving as as root switch.
	 */
	private void initializeRootSwitch(IOFSwitch sw) {
		for (Aggregate aggregate : aggregates) {
			ArrayList<Node> aggRootSwitches = aggregate.getRootSwitches();
			for (Node node : aggRootSwitches) {
				if (node.getSwitchDpid().equals(sw.getId())) {
					log.debug("Found Root Tree Node with matching DPID {} in Aggregate {}. Adding FLOOD flows for switch.", sw.getId().toString(), aggregate.getName());
					OFFactory factory = sw.getOFFactory();
					ArrayList<OFInstruction> instructions = new ArrayList<OFInstruction>(1);
					ArrayList<OFAction> actions = new ArrayList<OFAction>(1);
					for (OFPort egressPort : node.getEgressPorts()) {
						actions.add(factory.actions().buildOutput()
								.setMaxLen(0xffFFffFF)
								.setPort(egressPort)
								.build());
					}
					instructions.add(factory.instructions().buildApplyActions()
							.setActions(actions)
							.build());

					for (OFPort ingressPort : node.getIngressPorts()) {
						OFFlowAdd floodIP = factory.buildFlowAdd()
								.setBufferId(OFBufferId.NO_BUFFER)
								.setPriority(FlowModUtils.PRIORITY_LOW)
								.setMatch(factory.buildMatch()
										.setExact(MatchField.IN_PORT, ingressPort)
										.setExact(MatchField.ETH_TYPE, EthType.IPv4)
										.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
										.build())
										.setInstructions(instructions)
										.setOutPort(OFPort.FLOOD)
										.build();
						sw.write(floodIP);
					}
				}
			}
		}
	}

	/**
	 * For all switches in the GENI Cinema service, remove any
	 * pre-existing UDP-match flows. These might conflict with
	 * the new runtime configuration.
	 * 
	 * If the controller just connected to the switches though,
	 * the default behavior of the controller's handshake handler
	 * should be to clear the flow and group tables.
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
		if (!newChannel.getDemand()) { /* check just to be safe */
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

		/*
		 * FIXME Assume right now that the VLCS already sent the UDP stream with the correct dst MAC and IP for the egress GW.
		 *
		 * We will actually "cheat" and set all egress GWs with the same private IPs and MACs to cut our rewrite fields by 2/3.
		 * If this is not the assumption, then this will need to be changed to also rewrite to the correct dst IP and MAC.
		 * There should be a way to turn this part on and off without adding and removing the code, although arguably it should
		 * never be turned on for efficiency's sake. (The topology *should* be set up with the spoofed IPs and MACs.)
		 */
		ArrayList<OFAction> bucketActions = new ArrayList<OFAction>(2);
		bucketActions.add(factory.actions().setField(factory.oxms().udpDst(newStream.getVLCSSAtGateway().getIngress().getPort()))); 
		bucketActions.add(factory.actions().output(newStream.getChannel().getSortNode(newStream.getId()).getEgressPort(), Integer.MAX_VALUE));

		OFBucket bucket = factory.buildBucket()
				.setActions(bucketActions)
				.setWatchGroup(OFGroup.ANY) 	/* MUST EXPLICITLY SET OFGroup and OFPort as ANY, */
				.setWatchPort(OFPort.ANY)   	/* even if OFGroupType=ALL --> watch doesn't even matter */
				.build();						

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
				 * Hold onto the OFGroup. If we accept a Channel into the service, we should
				 * guarantee that Channel will always have an OFGroup. There's no sense in
				 * returning the OFGroup to the pool until the Channel is officially done
				 * (not just no demand).
				 */
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

	private void removeEgressStreamFlows(EgressStream oldStream) {

		OFFactory factory;

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

			OFGroupModify groupMod = factory.buildGroupModify()
					.setGroup(oldStream.getChannel().getGroup())
					.setGroupType(OFGroupType.ALL)
					.setBuckets(oldStream.getChannel().getBucketList(oldSortNode))
					.build();

			log.debug("Writing OFGroupMod to remove client {}'s OFBucket from OFGroup {}", 
					oldStream.getChannel().getId(), oldStream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString());

			switchService.getSwitch(oldSortNode.getSwitchDpid()).write(groupMod);

			/*
			 * If there is no longer any demand for a particular Channel,
			 * disable it at the Server-side OVS.
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
				 * For same reason as when switching Channels, let's hold onto the OFGroup
				 * until the Channel is explicitly removed. This guarantees QoS for the Channel.
				 */
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
}