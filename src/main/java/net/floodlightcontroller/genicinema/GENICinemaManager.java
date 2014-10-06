package net.floodlightcontroller.genicinema;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFTable;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmUdpDst;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.restlet.data.ClientInfo;
import org.restlet.resource.Finder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.sun.corba.se.spi.ior.MakeImmutable;
import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;

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
import net.floodlightcontroller.firewall.IFirewallService;
import net.floodlightcontroller.genicinema.Channel.ChannelBuilder;
import net.floodlightcontroller.genicinema.EgressStream.EgressStreamBuilder;
import net.floodlightcontroller.genicinema.IngressStream.IngressStreamBuilder;
import net.floodlightcontroller.genicinema.VideoSocket.VideoSocketBuilder;
import net.floodlightcontroller.genicinema.web.GENICinemaWebRoutable;
import net.floodlightcontroller.genicinema.web.JsonStrings;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
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
	private static Map<String, Map<Server, Channel>> channelsPerAggregate;

	/* Ongoing Egress Streams */
	private static Map<String, ArrayList<EgressStream>> egressStreamsPerAggregate;

	/* Ongoing Ingress Streams */
	private static Map<String, ArrayList<IngressStream>> ingressStreamsPerAggregate;

	/* All Aggregates in the GENI world */
	private static ArrayList<Aggregate> aggregates;

	/* All clients (EgressStream's) and Channels will have a unique ID. 
	 * Perhaps should use UUID or something stronger. */
	private static int clientIdGenerator = 0;
	private static int channelIdGenerator = 0;

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

		/*
		 * Initialize all class variables.
		 */
		aggregates = new ArrayList<Aggregate>(1);
		channelsPerAggregate = new HashMap<String, Map<Server, Channel>>(2); // for initial test, start of with a predefined two channels
		ingressStreamsPerAggregate = new HashMap<String, ArrayList<IngressStream>>(2); // --> need two ingress streams
		egressStreamsPerAggregate = new HashMap<String, ArrayList<EgressStream>>(254); // "large" number of clients possible

		/*
		 * For now, let's fake an existing aggregate w/o discovery.
		 */
		Gateway ingress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.0.0.1"))
		.setPublicIP(IPv4Address.of("130.127.88.10")) // TODO will need to update these IPs (above and below)
		.build();

		Gateway egress_gw = new Gateway.GatewayBuilder()
		.setPrivateIP(IPv4Address.of("10.0.0.2"))
		.setPublicIP(IPv4Address.of("130.127.88.11"))
		.build();

		Node server_ovs = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:00:11"))
		.setIngressPort(OFPort.of(65534))
		.setEgressPort(OFPort.of(1)) // TODO might need to update this port
		.build();
		Server server = new Server.ServerBuilder()
		.setPrivateIP(ingress_gw.getPrivateIP()) // for initial test, server will use public IP as ingress GW
		.setPublicIP(ingress_gw.getPublicIP())
		.setOVSNode(server_ovs)
		.build();

		Node ovs_switch = new Node.NodeBuilder()
		.setSwitchDpid(DatapathId.of("00:00:00:00:00:00:00:00:22"))
		.setIngressPort(OFPort.of(1)) // TODO will need to update these ports
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

		aggregates.add(clemson);
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
			boolean result = aggregate.switchConnected(switchId);
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
		Server hostServer = findBestHostServer(ingressGW.getPrivateIP()); // TODO get VLC server based on private IP. Is this the best idea?

		VLCStreamServer hostServerVLCSS = allocateVLCSSServerResource(hostServer);		
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
				.setHostIP(hostServerVLCSS.getEgress().getIP())
				.setHostNode(hostServer.getOVSNode())
				.setHostUDP(hostServerVLCSS.getEgress().getPort())
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

		/*
		 * Determine a Gateway where the client can attach and watch.
		 */
		findBestEgressGateway(IPv4Address.of(clientInfo.getAddress())

				/*
				 * Insert required flows along the path we just determined (VLCS w/OVS, OVS Node, and VLCS @GCGW)
				 */
				insertEgressStreamFlows(stream);


		return null;
	}

	@Override
	public String editChannel(String json, ClientInfo clientInfo) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * GENICinemaManager helper functions
	 */

	/**
	 * Takes a new Channel and updates the Manager appropriately.
	 * 
	 * @param newChannel, The recently-added Channel
	 * @return, true upon success; false upon failure
	 */
	private boolean addChannel(Channel newChannel) {
		Aggregate theAggregate = lookupAggregate(newChannel);
		
		/*
		 * Add the Channel to the list.
		 */
		if (!channelsPerAggregate.get(theAggregate.getName()).contains(newChannel)) {
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
		Aggregate theAggregate = lookupAggregate(newIngressStream);
		
		/*
		 * Add the IngressStream to the list.
		 */
		if (!ingressStreamsPerAggregate.containsKey(theAggregate.getName()) {
			return false;
		} else if (ingressStreamsPerAggregate.get(theAggregate.getName()).contains(newIngressStream)) {
			return false;
		} else {
			ingressStreamsPerAggregate.get(theAggregate.getName()).add(newIngressStream);
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
		return null;
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
		return null;
	}

	/**
	 * Based on the Gateway or Server assigned, try to allocate in/out sockets
	 * on the VLCS. Supply the Gateway if the VLCStreamServer should be located
	 * on that Gateway. Alternatively, supply the Server if the VLCStreamServer
	 * should be located on the Server. In either case, supply the other
	 * parameter as null. If both Gateway and Server are non-null, this is an 
	 * error condition and null will be returned.
	 * 
	 * @param egressGateway, The output GCGW where the client would like to connect.
	 * @param hostServer, The host VLCS the ingress stream should be stored on.
	 * 
	 * @return non-null upon success; null if invalid parameters or no resources available.
	 */
	private VLCStreamServer allocateVLCSSResource(Gateway egressGW, Server hostServer) {
		return null;
	}

	/**
	 * a.k.a. allocateVLCSSResource(egressGW, null)
	 * Based on the Gateway assigned, try to allocate in/out sockets on the VLCS.
	 * 
	 * @param egressGW, The output GCGW where the client would like to connect.
	 * @return non-null upon success; null if no resources available.
	 */
	private VLCStreamServer allocateVLCSSGatewayResource(Gateway egressGW) {
		return allocateVLCSSResource(egressGW, null);
	}

	/**
	 * a.k.a. allocateVLCSSResource(null, hostServer)
	 * Based on the Server assigned, try to allocate in/out sockets on the VLCS.
	 * 
	 * @param hostServer, The host VLCS the ingress stream should be stored on.
	 * @return non-null upon success; null if no resources available.
	 */
	private VLCStreamServer allocateVLCSSServerResource(Server hostServer) {
		return allocateVLCSSResource(null, hostServer);
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
		return null;
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
						listOfGroupsToAdd.add(newGroup);
						found = true;
					}
					if (found) break;
				}
			}
			if (found) break; // not sure if the first break will break all loops or just the internal one...
		}

		if (!listOfGroupsToAdd.isEmpty()) {
			log.debug("Writing list of OFGroupAdd's of size {} to switch {}.", listOfGroupsToAdd.size(), sw.getId().toString());
			sw.write(listOfGroupsToAdd);
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
	 * @return
	 */
	private void insertEgressStreamFlows(EgressStream stream) {

		OFFactory factory;

		/*
		 * Only need to enable the Channel if someone isn't watching already.
		 * Also only need to assign it to an OFGroup if someone isn't watching.
		 */
		if (!stream.getChannel().getDemand()) {

			/* *******************************************
			 * FIRST THE SERVER OVS NODE'S ALLOW/DENY FLOW
			 * *******************************************/

			factory = switchService.getSwitch(stream.getChannel().getHostNode().getSwitchDpid()).getOFFactory();

			/*
			 * All this just for an output port...
			 */
			OFActionOutput output = factory.actions().buildOutput()
					.setMaxLen(Integer.MAX_VALUE)
					.setPort(stream.getChannel().getHostNode().getEgressPort())
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
							.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
							.setExact(MatchField.UDP_SRC, stream.getChannel().getHostUDP())
							.setExact(MatchField.IN_PORT, stream.getChannel().getHostNode().getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.setOutPort(stream.getChannel().getHostNode().getEgressPort())
							.setPriority(FlowModUtils.PRIORITY_MAX)
							.setInstructions(instructionList)
							.build();

			log.debug("Writing OFFlowAdd to enable Channel {} on UDP port {} out of the VLCS: " + enableFlow.toString(),
					stream.getChannel().getId(), stream.getChannel().getHostUDP().toString());

			switchService.getSwitch(stream.getChannel().getHostNode().getSwitchDpid()).write(enableFlow);

			/* ********************************************
			 * NEXT, THE SORT OVS NODE'S DEFAULT-TABLE FLOW
			 * This should send the UDP-src packet to the
			 * correct OFGroup, which will then duplicate.
			 * ********************************************/

			factory = switchService.getSwitch(stream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();
			OFActionGroup actionGotoGroup = factory.actions().buildGroup()
					.setGroup(stream.getChannel().getGroup())
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
							.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
							.setExact(MatchField.UDP_SRC, stream.getChannel().getHostUDP())
							.setExact(MatchField.IN_PORT, stream.getChannel().getSortNode().getIngressPort())
							.build())
							.setBufferId(OFBufferId.NO_BUFFER)
							.setOutGroup(stream.getChannel().getGroup())
							.setPriority(FlowModUtils.PRIORITY_MAX)
							.setInstructions(instructionList)
							.build(); /* Don't set the table-id --> will use default table */

			log.debug("Writing OFFlowAdd to send Channel {} on UDP port {} to OFGroup #" + stream.getChannel().getGroup().getGroupNumber() + " : " + toGroupFlow.toString(),
					stream.getChannel().getId(), stream.getChannel().getHostUDP().toString());

			switchService.getSwitch(stream.getChannel().getSortNode().getSwitchDpid()).write(enableFlow);

		} // END IF DEMAND == FALSE (initial setup for Channel)

		/* ***************************************
		 * NOW, CONFIGURE THE OFGROUP'S OFBUCKET'S
		 * via an OFGroupMod, which will allow a
		 * new bucket list to be added to the 
		 * OFGroup.
		 * **************************************/

		factory = switchService.getSwitch(stream.getChannel().getSortNode().getSwitchDpid()).getOFFactory();

		//TODO assume right now that the VLCS already sent with the correct MAC and IP for the GCGW
		ArrayList<OFAction> bucketActions = new ArrayList<OFAction>(2);
		bucketActions.add(factory.actions().setField(factory.oxms().udpDst(stream.getGateway().getIngress().getPort()))); 
		bucketActions.add(factory.actions().output(stream.getChannel().getSortNode().getEgressPort(), Integer.MAX_VALUE));

		OFBucket bucket = factory.buildBucket()
				.setActions(bucketActions)
				.build();

		/*
		 * Get the current list of Channel viewers/clients and add this client.
		 */
		stream.getChannel().addBucket(stream.getId(), bucket);

		OFGroupMod groupMod = factory.buildGroupModify()
				.setGroup(stream.getChannel().getGroup())
				.setGroupType(OFGroupType.ALL)
				.setBuckets(stream.getChannel().getBucketList())
				.build();

		log.debug("Writing OFGroupMod to send Channel {} on UDP port {} from OFGroup #" + stream.getChannel().getGroup().getGroupNumber() + " : " + groupMod.toString(),
				stream.getChannel().getId(), stream.getChannel().getHostUDP().toString());

		switchService.getSwitch(stream.getChannel().getSortNode().getSwitchDpid()).write(groupMod);
	}
}
