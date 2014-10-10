package net.floodlightcontroller.arprelay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.FlowModUtils;

/**
 * A simple and (IMHO) useful module for testing without the need to worry 
 * about ARP packets. When any switch connects, flows are inserted on the
 * switch that for every port, match on ARP packets, and send them out
 * FLOOD and LOCAL. Likewise, any ARP packet that is received on LOCAL
 * is FLOODed.
 * 
 * LOCAL has to be dealt with specifically, since FLOOD only accounts
 * for physical ports. When using OVS, it is oftentimes useful to allow
 * the localhost on which the OVS is running to answer ARP requests
 * originating on the OF network. The localhost is reachable on the
 * local network stack via the LOCAL port.
 * 
 * If you don't want LOCAL to participate, indicate so in the
 * boolean class variable below.
 * 
 * This will not play nicely if your network has loops, so beware!
 * 
 * No, this isn't as efficient as inserting a flow for ARP to the
 * specific port we know/learn the destination to be on; but, it's
 * mighty-useful for testing and debugging.
 * 
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 *
 */
public class ARPRelay implements IFloodlightModule, IOFSwitchListener {
	private static Logger log;
	private IOFSwitchService switchService;

	private static final boolean include_LOCAL = true;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);
		log = LoggerFactory.getLogger(ARPRelay.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService.addOFSwitchListener(this);
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		OFFactory factory = sw.getOFFactory();
		OFFlowAdd.Builder fb = factory.buildFlowAdd();
		Match.Builder mb = factory.buildMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput.Builder aob = factory.actions().buildOutput();

		/*
		 * Loop over all "physical" ports of the switch and add a FLOOD flow on that port
		 * for all ARP packets. "Physical" means any port that can have a device attached,
		 * I think.
		 */
		for (OFPortDesc port : sw.getPorts()) {
			mb.setExact(MatchField.IN_PORT, port.getPortNo());
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			aob.setPort(OFPort.FLOOD);
			aob.setMaxLen(Integer.MAX_VALUE);
			actionList.add(aob.build());
			if (include_LOCAL) {
				aob.setPort(OFPort.LOCAL);
				aob.setMaxLen(Integer.MAX_VALUE);
				actionList.add(aob.build());
			}
			fb.setCookie(U64.of(0));
			fb.setBufferId(OFBufferId.NO_BUFFER);
			fb.setOutPort(OFPort.FLOOD); // have to repeat this due to API limitation
			fb.setActions(actionList);
			fb.setMatch(mb.build());
			fb.setPriority(FlowModUtils.PRIORITY_MAX); // max priority
			fb.setHardTimeout(0); // should be infinite timeouts (i.e. as long as the switch is connected)
			fb.setIdleTimeout(0);
			sw.write(fb.build());
			log.debug("Writing flow on switch {} to FLOOD (LOCAL=" + include_LOCAL + ") from port {}.", sw.getId().toString(), port.getPortNo().toString());
			log.debug("Flow: {}", fb.toString());
			actionList.clear();			
		}
		/*
		 *  LOCAL is not a physical port, I don't think. So, handle it separately if need be. 
		 *  (Might be able to remove this if LOCAL is deemed a "physical" port.)
		 */
		if (include_LOCAL) {
			mb.setExact(MatchField.IN_PORT, OFPort.LOCAL);
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			aob.setPort(OFPort.FLOOD);
			aob.setMaxLen(Integer.MAX_VALUE);
			actionList.add(aob.build());
			fb.setCookie(U64.of(0));
			fb.setBufferId(OFBufferId.NO_BUFFER);
			fb.setOutPort(OFPort.FLOOD); // have to repeat this due to API limitation
			fb.setActions(actionList);
			fb.setMatch(mb.build());
			fb.setPriority(FlowModUtils.PRIORITY_MAX); // max priority
			fb.setHardTimeout(0); // should be infinite timeouts (i.e. as long as the switch is connected)
			fb.setIdleTimeout(0);
			sw.write(fb.build());
			log.debug("Writing flow on switch {} to FLOOD from port {}.", sw.getId().toString(), OFPort.FLOOD.toString());
			log.debug("Flow: {}", fb.toString());
		}
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
	}

	@Override
	public void switchActivated(DatapathId switchId) {
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {		
	}

	@Override
	public void switchChanged(DatapathId switchId) {		
	}

}
