package net.floodlightcontroller.loadthis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class CPRDemonstrator extends CPRDemonstratorBase implements IFloodlightModule {

	protected static Logger log = LoggerFactory
			.getLogger(CPRDemonstrator.class);

	@Override
	public net.floodlightcontroller.core.IListener.Command processPacketInMessage(
			IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision,
			FloodlightContext cntx) {
		log.debug(
				"-----------PROCESSING PACKET_IN FROM SW: {}, PACKET_IN: {}---------------",
				sw.toString(), pi.toString());
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		// If a decision has been made we obey it
		// otherwise we just forward
		if (decision != null) {
			if (log.isTraceEnabled()) {
				log.trace("Forwaring decision={} was made for PacketIn={}",
						decision.getRoutingAction().toString(), pi);
			}

			switch (decision.getRoutingAction()) {
			case NONE:
				// don't do anything
				return Command.CONTINUE;
			case FORWARD_OR_FLOOD:
			case FORWARD:
				log.debug("-------------FORWARD WITH DECISION-------------");
				int decisionIdentifier = lookForDecisionIdentifier(eth);
				if (decisionIdentifier != 0xA000001) {
					// if(decisionIdentifier == 0){
					log.debug("-------------SIMPLE FORWARD-------------");
					doForwardFlow(sw, pi, cntx, false);
				} else {
					log.debug("-------------CREATING LINK REQUIREMENTS-------------");
					LinkAttribiutes requirements = createLinkRequirements(decisionIdentifier);
					log.debug("-------------SMART FORWARD-------------");
					doSmartForwardFlow(sw, pi, cntx, requirements);
				}
				return Command.CONTINUE;
			case MULTICAST:
				// treat as broadcast
				doFlood(sw, pi, cntx);
				log.debug("-------------FLOOD WITH DECISION-------------");
				return Command.CONTINUE;
			case DROP:
				// doDropFlow(sw, pi, decision, cntx);
				return Command.CONTINUE;
			default:
				log.error("Unexpected decision made for this packet-in={}", pi,
						decision.getRoutingAction());
				return Command.CONTINUE;
			}
		} else {
			if (log.isTraceEnabled()) {
				log.trace("No decision was made for PacketIn={}, forwarding",
						pi);
			}

			if (eth.isBroadcast() || eth.isMulticast()) {
				// For now we treat multicast as broadcast
				log.debug("-------------BROADCAST ETH PACKET-------------");
				doFlood(sw, pi, cntx);
			} else {

				int decisionIdentifier = lookForDecisionIdentifier(eth);
				if (decisionIdentifier != 0xA000001) {
					// if(decisionIdentifier == 0){
					log.debug("-------------SIMPLE FORWARD-------------");
					doForwardFlow(sw, pi, cntx, false);
				} else {
					log.debug("-------------CREATING LINK REQUIREMENTS-------------");
					LinkAttribiutes requirements = createLinkRequirements(decisionIdentifier);
					log.debug("-------------SMART FORWARD-------------");
					doSmartForwardFlow(sw, pi, cntx, requirements);
				}
			}
		}

		return Command.CONTINUE;
	}

	/**
	 * Look to IPv4 Header and search for Decision Identifier (DiffServ Field)
	 * 
	 * @param eth
	 *            Ethernet frame
	 * @return Value of Decision Identifier
	 */
	private int lookForDecisionIdentifier(Ethernet eth) {
		log.debug("-------------LOOKING FOR DECISION IDENTIFIER-------------");
		int decisionIdentifier = 0;
		if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
			IPv4 ipv4Pkt = (IPv4) eth.getPayload();
			// decisionIdentifier = ipv4Pkt.getDiffServ();
			IPv4Address address = ipv4Pkt.getDestinationAddress();
			decisionIdentifier = address.getInt();
		}
		log.debug("-------------DECISION IDENTIFIER = {}-------------",
				decisionIdentifier);
		return decisionIdentifier;
	}

	private LinkAttribiutes createLinkRequirements(int decisionIdentifier) {
		// TODO: implement this function
		LinkAttribiutes requirements = new LinkAttribiutes(100, Medium.COOPER,
				false, false, false);
		return requirements;
	}

	/**
	 * Creates a OFPacketOut with the OFPacketIn data that is flooded on all
	 * ports unless the port is blocked, in which case the packet will be
	 * dropped.
	 * 
	 * @param sw
	 *            The switch that receives the OFPacketIn
	 * @param pi
	 *            The OFPacketIn that came to the switch
	 * @param cntx
	 *            The FloodlightContext associated with this OFPacketIn
	 */
	@LogMessageDoc(level = "ERROR", message = "Failure writing PacketOut "
			+ "switch={switch} packet-in={packet-in} "
			+ "packet-out={packet-out}", explanation = "An I/O error occured while writing a packet "
			+ "out message to the switch", recommendation = LogMessageDoc.CHECK_SWITCH)
	protected void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		if (topologyService.isIncomingBroadcastAllowed(sw.getId(), (pi
				.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi.getInPort()
				: pi.getMatch().get(MatchField.IN_PORT))) == false) {
			if (log.isTraceEnabled()) {
				log.trace(
						"doFlood, drop broadcast packet, pi={}, "
								+ "from a blocked port, srcSwitch=[{},{}], linkInfo={}",
						new Object[] {
								pi,
								sw.getId(),
								(pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
										.getInPort() : pi.getMatch().get(
										MatchField.IN_PORT)) });
			}
			return;
		}

		// Set Action to flood
		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)) {
			actions.add(sw.getOFFactory().actions()
					.output(OFPort.FLOOD, Integer.MAX_VALUE)); // FLOOD is a
																// more
																// selective/efficient
																// version of
																// ALL
		} else {
			actions.add(sw.getOFFactory().actions()
					.output(OFPort.ALL, Integer.MAX_VALUE));
		}
		pob.setActions(actions);

		// set buffer-id, in-port and packet-data based on packet-in
		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort((pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));
		pob.setData(pi.getData());

		try {
			if (log.isTraceEnabled()) {
				log.trace(
						"Writing flood PacketOut switch={} packet-in={} packet-out={}",
						new Object[] { sw, pi, pob.build() });
			}
			messageDamper.write(sw, pob.build(), cntx);
		} catch (IOException e) {
			log.error(
					"Failure writing PacketOut switch={} packet-in={} packet-out={}",
					new Object[] { sw, pi, pob.build() }, e);
		}

		return;
	}

	protected void doForwardFlow(IOFSwitch sw, OFPacketIn pi,
			FloodlightContext cntx, boolean requestFlowRemovedNotifn) {
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		// Check if we have the location of the destination
		IDevice dstDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);

		if (dstDevice != null) {
			IDevice srcDevice = IDeviceService.fcStore.get(cntx,
					IDeviceService.CONTEXT_SRC_DEVICE);
			DatapathId srcIsland = topologyService.getL2DomainId(sw.getId());

			if (srcDevice == null) {
				log.debug("No device entry found for source device");
				return;
			}
			if (srcIsland == null) {
				log.debug(
						"No openflow island found for source {}/{}",
						sw.getId().toString(),
						(pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
								.getInPort() : pi.getMatch().get(
								MatchField.IN_PORT)));
				return;
			}

			// Validate that we have a destination known on the same island
			// Validate that the source and destination are not on the same
			// switchport
			boolean on_same_island = false;
			boolean on_same_if = false;
			for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
				DatapathId dstSwDpid = dstDap.getSwitchDPID();
				DatapathId dstIsland = topologyService.getL2DomainId(dstSwDpid);
				if ((dstIsland != null) && dstIsland.equals(srcIsland)) {
					on_same_island = true;
					if ((sw.getId().equals(dstSwDpid))
							&& ((pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
									.getInPort() : pi.getMatch().get(
									MatchField.IN_PORT)).equals(dstDap
									.getPort()))) {
						on_same_if = true;
					}
					break;
				}
			}

			if (!on_same_island) {
				// Flood since we don't know the dst device
				if (log.isTraceEnabled()) {
					log.trace("No first hop island found for destination "
							+ "device {}, Action = flooding", dstDevice);
				}
				doFlood(sw, pi, cntx);
				return;
			}

			if (on_same_if) {
				if (log.isTraceEnabled()) {
					log.trace(
							"Both source and destination are on the same "
									+ "switch/port {}/{}, Action = NOP",
							sw.toString(),
							(pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
									.getInPort() : pi.getMatch().get(
									MatchField.IN_PORT)));
				}
				return;
			}

			// Install all the routes where both src and dst have attachment
			// points. Since the lists are stored in sorted order we can
			// traverse the attachment points in O(m+n) time
			SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
			Arrays.sort(srcDaps, clusterIdComparator);
			SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
			Arrays.sort(dstDaps, clusterIdComparator);

			int iSrcDaps = 0, iDstDaps = 0;

			while ((iSrcDaps < srcDaps.length) && (iDstDaps < dstDaps.length)) {
				SwitchPort srcDap = srcDaps[iSrcDaps];
				SwitchPort dstDap = dstDaps[iDstDaps];

				// srcCluster and dstCluster here cannot be null as
				// every switch will be at least in its own L2 domain.
				DatapathId srcCluster = topologyService.getL2DomainId(srcDap
						.getSwitchDPID());
				DatapathId dstCluster = topologyService.getL2DomainId(dstDap
						.getSwitchDPID());

				int srcVsDest = srcCluster.compareTo(dstCluster);
				if (srcVsDest == 0) {
					if (!srcDap.equals(dstDap)) {
						Route route = routingEngineService.getRoute(
								srcDap.getSwitchDPID(), srcDap.getPort(),
								dstDap.getSwitchDPID(), dstDap.getPort(),
								U64.of(0)); // cookie = 0, i.e., default route
						if (route != null) {
							if (log.isTraceEnabled()) {
								log.trace("pushRoute inPort={} route={} "
										+ "destination={}:{}", new Object[] {
										inPort, route, dstDap.getSwitchDPID(),
										dstDap.getPort() });
							}
							U64 cookie = AppCookie.makeCookie(
									CPR_DEMONSTRATOR_APP_ID, 0);

							// if there is prior routing decision use route's
							// match
							Match routeMatch = null;
							IRoutingDecision decision = null;
							if (cntx != null) {
								decision = IRoutingDecision.rtStore.get(cntx,
										IRoutingDecision.CONTEXT_DECISION);
							}
							if (decision != null) {
								routeMatch = decision.getMatch();
							} else {
								// The packet in match will only contain the
								// port number.
								// We need to add in specifics for the hosts
								// we're routing between.
								Ethernet eth = IFloodlightProviderService.bcStore
										.get(cntx,
												IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
								VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
								MacAddress srcMac = eth.getSourceMACAddress();
								MacAddress dstMac = eth
										.getDestinationMACAddress();

								// A retentive builder will remember all
								// MatchFields of the parent the builder was
								// generated from
								// With a normal builder, all parent MatchFields
								// will be lost if any MatchFields are added,
								// mod, del
								Match.Builder mb = sw.getOFFactory()
										.buildMatch();
								mb.setExact(MatchField.IN_PORT, inPort)
										.setExact(MatchField.ETH_SRC, srcMac)
										.setExact(MatchField.ETH_DST, dstMac);

								if (!vlan.equals(VlanVid.ZERO)) {
									mb.setExact(MatchField.VLAN_VID,
											OFVlanVidMatch.ofVlanVid(vlan));
								}

								if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
									IPv4 ip = (IPv4) eth.getPayload();
									IPv4Address srcIp = ip.getSourceAddress();
									IPv4Address dstIp = ip
											.getDestinationAddress();
									mb.setExact(MatchField.IPV4_SRC, srcIp)
											.setExact(MatchField.IPV4_DST,
													dstIp)
											.setExact(MatchField.ETH_TYPE,
													EthType.IPv4);
								} else if (eth.getEtherType() == Ethernet.TYPE_ARP) {
									mb.setExact(MatchField.ETH_TYPE,
											EthType.ARP);
								} // TODO @Ryan should probably include other
									// ethertypes

								routeMatch = mb.build();
							}

							pushRoute(route, routeMatch, pi, sw.getId(),
									cookie, cntx, requestFlowRemovedNotifn,
									false, OFFlowModCommand.ADD);
						}
					}
					iSrcDaps++;
					iDstDaps++;
				} else if (srcVsDest < 0) {
					iSrcDaps++;
				} else {
					iDstDaps++;
				}
			}
		} else {
			// Flood since we don't know the dst device
			doFlood(sw, pi, cntx);
		}
	}

	/**
	 * Program flows on switches based on link attributes
	 * 
	 * @param sw
	 *            Switch which sends PACKET_IN message
	 * @param pi
	 *            PACKET_IN message
	 * @param cntx
	 *            Floodlight context
	 */
	private void doSmartForwardFlow(IOFSwitch sw, OFPacketIn pi,
			FloodlightContext cntx, LinkAttribiutes requirements) {

		// Calculate all paths between requester and server
		// switchesRoutes contain routes and each route contain switches DPID
		// which are on this route
		Map<Integer, LinkedList<Long>> switchesRoutes = calculateAllPaths(sw,
				cntx);
		if (switchesRoutes == null) {
			log.debug("-----------No Path beetween requester and server-----------");
			return;
		}
		log.debug("---------ALL PATHS: {} ----------",
				switchesRoutes.toString());

		// Check for routes which fulfill DI requirements
		log.debug("------------CHOOSING PATHS WHICH FULLFILL REQUIREMENTS-------------");
		Map<Integer, LinkedList<Long>> possiblesRoutes = choosePossibleRoutes(
				switchesRoutes, requirements, cntx);
		// Create one route from possible paths
		if (possiblesRoutes.isEmpty()) {
			log.debug("------------------No possibble routes between requester and server----------------");
			return;
		}
		log.debug("----------POSSIBLE ROUTES = {} -----------",
				possiblesRoutes.toString());
		Route route = createRoute(possiblesRoutes, cntx, pi, sw);
		log.debug("-------------ROUTE CREATED = {}-------------", route);

		// PROGRAM FLOWS ONLY FOR TESTS!!!!!!!!!!
		// IT IS NOT PROPER SOLUTION!!!!
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_13) < 0 ? pi
				.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		if (route != null) {
			
			U64 cookie = AppCookie.makeCookie(
					CPR_DEMONSTRATOR_APP_ID, 0);

			// if there is prior routing decision use route's
			// match
			Match routeMatch = null;
			IRoutingDecision decision = null;
			if (cntx != null) {
				decision = IRoutingDecision.rtStore.get(cntx,
						IRoutingDecision.CONTEXT_DECISION);
			}
			if (decision != null) {
				routeMatch = decision.getMatch();
			} else {
				// The packet in match will only contain the
				// port number.
				// We need to add in specifics for the hosts
				// we're routing between.
				Ethernet eth = IFloodlightProviderService.bcStore
						.get(cntx,
								IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
				VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
				MacAddress srcMac = eth.getSourceMACAddress();
				MacAddress dstMac = eth
						.getDestinationMACAddress();

				// A retentive builder will remember all
				// MatchFields of the parent the builder was
				// generated from
				// With a normal builder, all parent MatchFields
				// will be lost if any MatchFields are added,
				// mod, del
				Match.Builder mb = sw.getOFFactory()
						.buildMatch();
				mb.setExact(MatchField.IN_PORT, inPort)
						.setExact(MatchField.ETH_SRC, srcMac)
						.setExact(MatchField.ETH_DST, dstMac);

				if (!vlan.equals(VlanVid.ZERO)) {
					mb.setExact(MatchField.VLAN_VID,
							OFVlanVidMatch.ofVlanVid(vlan));
				}

				if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
					IPv4 ip = (IPv4) eth.getPayload();
					IPv4Address srcIp = ip.getSourceAddress();
					IPv4Address dstIp = ip
							.getDestinationAddress();
					mb.setExact(MatchField.IPV4_SRC, srcIp)
							.setExact(MatchField.IPV4_DST,
									dstIp)
							.setExact(MatchField.ETH_TYPE,
									EthType.IPv4);
				} else if (eth.getEtherType() == Ethernet.TYPE_ARP) {
					mb.setExact(MatchField.ETH_TYPE,
							EthType.ARP);
				} // TODO @Ryan should probably include other
					// ethertypes

				routeMatch = mb.build();
			}

			pushRoute(route, routeMatch, pi, sw.getId(),
					cookie, cntx, false,
					true, OFFlowModCommand.ADD);
		}

		// Store all possible paths

		// Program flows

		return;
	}

	/**
	 * Function calculate all paths between user and server.
	 * 
	 * @param sw
	 *            Switch which send PACKET_IN message
	 * @param cntx
	 *            FloodlightContext
	 * @return Map with path if paths are available null otherwise
	 */
	private Map<Integer, LinkedList<Long>> calculateAllPaths(IOFSwitch sw,
			FloodlightContext cntx) {

		log.debug("-------------CALCULATING ALL PATHS-------------");
		// Get destination device
		IDevice dstDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);

		// Generate Network Graph from links
		NetworkGraph networkGraph = new NetworkGraph();
		for (Link l : linkDiscovery.getLinks().keySet()) {
			networkGraph.addEdge(l.getSrc().getLong(), l.getDst().getLong());
		}

		// Get destination device attachment point (SW DPID and SW PORT)
		SwitchPort[] dstAP = dstDevice.getAttachmentPoints();

		// Add first node to visited nodes and set end node
		LinkedList<Long> visited = new LinkedList<Long>();
		visited.add(new Long(sw.getId().getLong()));
		long end = dstAP[0].getSwitchDPID().getLong();

		// First clear routes from previous calculate
		SearchAllPaths.clearSwitchesRoutes();

		// Calculate paths
		SearchAllPaths.breadthFirst(networkGraph, visited, end);

		Map<Integer, LinkedList<Long>> switchesRoutes = new HashMap<>(
				SearchAllPaths.switchesRoutes);
		SearchAllPaths.clearSwitchesRoutes();

		// Check if there are possible ways
		if (switchesRoutes.isEmpty()) {
			return null;
		}
		return switchesRoutes;
	}

	private Map<Integer, LinkedList<Long>> choosePossibleRoutes(
			Map<Integer, LinkedList<Long>> switchesRoutes,
			LinkAttribiutes requirements, FloodlightContext cntx) {
		// TODO: NOW it Looks 2 times for links. In this function and in
		// craeteRoute->findLinkBetweenNodes. It will be good to store set of
		// links from this function and use it in createRoute()
		Map<Integer, LinkedList<Long>> possibleRoutes = new HashMap<>();
		int possibleRouteNumber = 0;
		int switchRouteNumber = 0;
		LinkedList<Long> switchRoute = switchesRoutes.get(switchRouteNumber);
		do {
			ArrayList<Link> links = new ArrayList<Link>();
			boolean routeFullfillRequirements = true;
			for (int i = 0, j = 1; j < switchRoute.size(); i++, j++) {
				if (routeFullfillRequirements == false) {
					break;
				}

				// HOST->SWITCH
				if (i == 0) {
					IDevice srcDevice = IDeviceService.fcStore.get(cntx,
							IDeviceService.CONTEXT_SRC_DEVICE);
					SwitchPort[] srcPorts = srcDevice.getAttachmentPoints();
					IOFSwitch sw = switchService.getSwitch(srcPorts[0]
							.getSwitchDPID());
//					routeFullfillRequirements = hostLinkFulfillRequirements(
//							requirements, sw, srcPorts[0]);
					if (routeFullfillRequirements == false) {
						continue;
					}
				}

				if (j == (switchRoute.size() - 1)) {
					IDevice dstDevice = IDeviceService.fcStore.get(cntx,
							IDeviceService.CONTEXT_SRC_DEVICE);
					SwitchPort[] dstPorts = dstDevice.getAttachmentPoints();
					IOFSwitch sw = switchService.getSwitch(dstPorts[0]
							.getSwitchDPID());
//					routeFullfillRequirements = hostLinkFulfillRequirements(
//							requirements, sw, dstPorts[0]);
					if (routeFullfillRequirements == false) {
						continue;
					}
				}

				// SW<->SW links
				Long src = switchRoute.get(i);
				Long dst = switchRoute.get(j);
				for (Link l : linkDiscovery.getLinks().keySet()) {
					// System.out.println("LINK " + l.toString());
					if (l.getSrc().getLong() == src && l.getDst().getLong() == dst) {
						links.add(l);
						sendPortDescription(l);
//						routeFullfillRequirements = fulfillRequirements(l,
//								requirements);
						break;
					}
				}
			}
			if (routeFullfillRequirements == true) {
				possibleRoutes.put(possibleRouteNumber, switchRoute);
				possibleRouteNumber++;
			}
			switchRouteNumber++;
			switchRoute = switchesRoutes.get(switchRouteNumber);
		} while (switchRoute != null);

		return possibleRoutes;
	}

//	private boolean hostLinkFulfillRequirements(LinkAttribiutes requirements,
//			IOFSwitch sw, SwitchPort port) {
//		log.debug("------------LOOKING FOR HOST LINK FULFILLREQUIREMENTS-------------");
//		List<OFStatsReply> values = sendPortDescription(sw);
//		// while(values.isEmpty() == true){
//		// log.debug("----------------PORT_DESC_REPLAY is EMPTY!!!!! --------------");
//		// values = sendPortDescription(l);
//		// }
//
//		/**********
//		 * 
//		 * ONLY FOR TESTS!!!!!!!!!!
//		 * 
//		 ***************/
//		if (values.isEmpty() == true) {
//			log.debug("----------------PORT_DESC_REPLAY is EMPTY!!!!! --------------");
//			return true;
//		}
//
//		for (int i = 0; i < values.size(); i++) {
//			OFPortDescription desc = (OFPortDescription) values.get(i);
//			if (desc.getPort().getPortNumber() == port.getPort()) {
//				LinkAttribiutes linkAttribiutes = createLinkAttribiutes(desc);
//				boolean fulFill = linkAttribiutes
//						.isFulFillRequirements(requirements);
//				log.debug(
//						"------------------ HOST LINK: {}. FULFILL: {} -------------------",
//						port, fulFill);
//				return fulFill;
//			}
//		}
//		log.debug("-------------IT SHOULD NOT HAPPENED!!!!-------------");
//		return false;
//	}
//
//	private boolean fulfillRequirements(Link l, LinkAttribiutes requirements) {
//		log.debug("------------LOOKING FOR FULFILLREQUIREMENTS-------------");
//		List<OFStatsReply> values = sendPortDescription(l);
//		// while(values.isEmpty() == true){
//		// log.debug("----------------PORT_DESC_REPLAY is EMPTY!!!!! --------------");
//		// values = sendPortDescription(l);
//		// }
//
//		/**********
//		 * 
//		 * ONLY FOR TESTS!!!!!!!!!!
//		 * 
//		 ***************/
//		if (values.isEmpty() == true) {
//			log.debug("----------------PORT_DESC_REPLAY is EMPTY!!!!! --------------");
//			return true;
//		}
//
//		for (int i = 0; i < values.size(); i++) {
////			OFPortDescription desc = (OFPortDescription) values.get(i);
//			if (desc.getPort().getPortNumber() == l.getDstPort()) {
//				LinkAttribiutes linkAttribiutes = createLinkAttribiutes(desc);
//				boolean fulFill = linkAttribiutes
//						.isFulFillRequirements(requirements);
//				log.debug(
//						"------------------ LINK: {}. FULFILL: {} -------------------",
//						l, fulFill);
//				return fulFill;
//			}
//		}
//		log.debug("-------------IT SHOULD NOT HAPPENED!!!!-------------");
//		return false;
//	}

	/**
	 * Sends PORT_DESCRIPTION to DST (do not know why not work when send to src)
	 * of link and return list of ports description
	 * 
	 * @param l
	 *            Link which description will be return
	 * @return List of ports descriptions. Can be empty if request is not done.
	 */
	private List<OFStatsReply> sendPortDescription(Link l) {
		log.debug("----------SENDING PORT DESCRIPTION--------------");
		IOFSwitch sw = switchService.getSwitch(l.getDst());
		ListenableFuture<?> future;
//		Future<List<OFStatistics>> future;
		List<OFStatsReply> values = null;
		OFStatsRequest<?> req = null;
//		OFStatisticsRequest req = new OFStatisticsRequest();
//		req.setStatisticsType(OFStatisticsType.PORT_DESC);

		req = sw.getOFFactory().buildPortDescStatsRequest().build();
		try {
			future = sw.writeStatsRequest(req);
			// TODO: calculate time?
			values = (List<OFStatsReply>) future.get(150, TimeUnit.MILLISECONDS);
			log.debug("----------PORT_DESC_REPLAY STATUS: {} --------------",
					future.isDone());
		} catch (Exception e) {
			log.error("Failure retrieving statistics from switch " + sw, e);
		}

		OFPortDesc desc = (OFPortDesc) values.get(0);
		return values;

	}

//	private List<OFStatsReply> sendPortDescription(IOFSwitch sw) {
//		log.debug("----------SENDING PORT DESCRIPTION--------------");
//		Future<List<OFStatistics>> future;
//		List<OFStatistics> values = null;
//		OFStatisticsRequest req = new OFStatisticsRequest();
//		req.setStatisticsType(OFStatisticsType.PORT_DESC);
//		try {
//			future = sw.queryStatistics(req);
//			// TODO: calculate time?
//			values = future.get(150, TimeUnit.MILLISECONDS);
//			log.debug("----------PORT_DESC_REPLAY STATUS: {} --------------",
//					future.isDone());
//		} catch (Exception e) {
//			log.error("Failure retrieving statistics from switch " + sw, e);
//		}
//
//		return values;
//
//	}

//	private LinkAttribiutes createLinkAttribiutes(OFPortDescription desc) {
//		log.debug("---------------CREATING LINK ATTRIBIUTES FROM DESCRITPION-------------------");
//		LinkAttribiutes linkAttribiutes = new LinkAttribiutes();
//		linkAttribiutes.setBandwith(desc.getPort().getCurrSpeed());
//		int currentFeatures = desc.getPort().getCurrentFeatures();
//
//		// Getting medium
//		if ((currentFeatures & (1 << 11)) != 0) {
//			linkAttribiutes.setMedium(Medium.COOPER);
//		} else if ((currentFeatures & (1 << 12)) != 0) {
//			linkAttribiutes.setMedium(Medium.FIBER);
//		} else {
//			log.debug("------------MEDIUM NOT SET FROM DESC. DEFAULT = COPPER --------------");
//		}
//
//		// Getting AutoNegotiation
//		if ((currentFeatures & (1 << 13)) != 0) {
//			linkAttribiutes.setAutonegotation(true);
//		}
//
//		// Getting Pause
//		if ((currentFeatures & (1 << 14)) != 0) {
//			linkAttribiutes.setPause(true);
//		}
//
//		// Getting PAUSE_ASYM
//		if ((currentFeatures & (1 << 15)) != 0) {
//			linkAttribiutes.setAsymetricPause(true);
//			;
//		}
//
//		return linkAttribiutes;
//	}

	/**
	 * Create Route from possible routes. Now get the first possible route.
	 * 
	 * @param possiblesRoutes
	 * @param cntx
	 * @param pi
	 * @param sw
	 * @return
	 */
	private Route createRoute(Map<Integer, LinkedList<Long>> possiblesRoutes,
			FloodlightContext cntx, OFPacketIn pi, IOFSwitch sw) {
		log.debug("-------------CREATING ROUTE-------------");
		// Get first possible route
		// TODO: Maybe some algorithm to get the best way?
		LinkedList<Long> firstPossibleRoute = possiblesRoutes.get(0);

		// Generate Route ID which containt src and dst Switches IDs
		RouteId id = new RouteId(DatapathId.of(firstPossibleRoute.getFirst()),
				DatapathId.of(firstPossibleRoute.getLast()));

		// Get links between each nodes in route
		ArrayList<Link> links = findLinksBetweenNodes(firstPossibleRoute);

		// Get switch ports to build a route
		ArrayList<NodePortTuple> switchPorts = createSwitchPortsFromLinks(
				links, cntx, pi, sw, firstPossibleRoute);

		// Create a route form RouteID and SwitchPorts
		Route r = new Route(id, switchPorts);

		return r;
	}

	/**
	 * Return links between nodes in possible route (without links from hosts to
	 * switches)
	 * 
	 * @param nodes
	 *            List of nodes in route
	 * @return List of links
	 */
	private ArrayList<Link> findLinksBetweenNodes(LinkedList<Long> nodes) {
		log.debug("-------------GENERATE LINKS FROM NODES (WITHOUT HOST->SWITCH LINKS)-------------");
		ArrayList<Link> links = new ArrayList<Link>();
		for (int i = 0, j = 1; j < nodes.size(); i++, j++) {
			Long src = nodes.get(i);
			Long dst = nodes.get(j);
			for (Link l : linkDiscovery.getLinks().keySet()) {
				// System.out.println("LINK " + l.toString());
				if (l.getSrc().getLong() == src && l.getDst().getLong() == dst) {
					links.add(l);
					break;
				}
			}
		}

		return links;
	}

	/**
	 * Creates list of switch ports in way (with switchports connected to hosts)
	 * 
	 * @param links
	 *            Links in route (without links to hosts)
	 * @param cntx
	 *            Floodlight context
	 * @param pi
	 *            PACKET_IN message
	 * @param sw
	 *            Switch which sends PACEKT_IN message
	 * @param PossibleRoute
	 *            Choosed possible route
	 * @return List of Switch Ports
	 */
	private ArrayList<NodePortTuple> createSwitchPortsFromLinks(
			ArrayList<Link> links, FloodlightContext cntx, OFPacketIn pi,
			IOFSwitch sw, LinkedList<Long> PossibleRoute) {
		log.debug("-------------CREAING SWITCH PORTS-------------");
		ArrayList<NodePortTuple> switchPorts = new ArrayList<NodePortTuple>();

		// Get and add first port (host->switch)
		IDevice srcDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_SRC_DEVICE);

		SwitchPort[] srcPorts = srcDevice.getAttachmentPoints();
		for (int i = 0; i < srcPorts.length; i++) {
			SwitchPort port = srcPorts[i];
			if (port.getSwitchDPID() == sw.getId()
					&& port.getPort() == pi.getInPort()) {
				switchPorts.add(new NodePortTuple(port.getSwitchDPID(), port
						.getPort()));
				break;
			}
		}

		// Add ports from links
		for (int i = 0; i < links.size(); i++) {
			Link link = links.get(i);
			switchPorts
					.add(new NodePortTuple(link.getSrc(), link.getSrcPort()));
			switchPorts
					.add(new NodePortTuple(link.getDst(), link.getDstPort()));
		}

		// Add last link (switch->host)
		IDevice dstDevice = IDeviceService.fcStore.get(cntx,
				IDeviceService.CONTEXT_DST_DEVICE);

		SwitchPort[] dstPorts = dstDevice.getAttachmentPoints();
		for (int i = 0; i < dstPorts.length; i++) {
			SwitchPort port = dstPorts[i];
			if (port.getSwitchDPID().getLong() == PossibleRoute.getLast()) {
				switchPorts.add(new NodePortTuple(port.getSwitchDPID(), port
						.getPort()));
				break;
			}
		}
		return switchPorts;
	}

	/*
	 * 
	 * IFloodlightModule methods
	 */

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(ITopologyService.class);
		l.add(ILinkDiscoveryService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		super.init();
		this.floodlightProviderService = context
				.getServiceImpl(IFloodlightProviderService.class);
		this.deviceManagerService = context.getServiceImpl(IDeviceService.class);
		this.routingEngineService = context.getServiceImpl(IRoutingService.class);
		this.topologyService = context.getServiceImpl(ITopologyService.class);
		this.linkDiscovery = context
				.getServiceImpl(ILinkDiscoveryService.class);
		this.switchService = context.getServiceImpl(IOFSwitchService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		super.startUp();
	}

}
