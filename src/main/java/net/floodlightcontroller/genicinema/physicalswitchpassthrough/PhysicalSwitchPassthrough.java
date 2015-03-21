package net.floodlightcontroller.genicinema.physicalswitchpassthrough;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
 * If we're using the GENI infrastructure with raw PCs, we need
 * to OpenFlow-enable the physical switches in order to bypass
 * the MAC learning that will take place otherwise.
 * 
 * This will allow the GENI Cinema UDP packets to traverse the
 * network with identical source IP/MACs going both directions
 * on a single link (that would normally confuse a learning
 * switch and cause it to shut down the port(s)).
 * 
 * The patch is simple -- for each physical switch, install flows
 * to forward all packets from one port to the other (minus LOCAL).
 * This assumes the GENI LAN is a point-to-point LAN and does not
 * have more than two "physical" ports.
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 *
 */
public class PhysicalSwitchPassthrough implements IFloodlightModule, IOFSwitchListener {
	private static Logger log;
	private IOFSwitchService switchService;
	private static ArrayList<DatapathId> physicalSwitches;

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
		log = LoggerFactory.getLogger(PhysicalSwitchPassthrough.class);

		/* Add in our physical switches... should probably use a config file eventually */
		physicalSwitches = new ArrayList<DatapathId>(14);
		physicalSwitches.add(DatapathId.of("0e:9f:84:34:97:d5:62:00")); //1
		physicalSwitches.add(DatapathId.of("0e:8f:84:34:97:c6:c9:00")); //2
		physicalSwitches.add(DatapathId.of("0d:de:6c:3b:e5:6c:c5:00")); //3
		physicalSwitches.add(DatapathId.of("0d:c9:6c:3b:e5:66:6b:00")); //4
		physicalSwitches.add(DatapathId.of("06:68:6c:3b:e5:63:21:00")); //5
		physicalSwitches.add(DatapathId.of("06:0f:84:34:97:d4:6c:00")); //6
		physicalSwitches.add(DatapathId.of("04:9f:84:34:97:d5:c5:00")); //7
		physicalSwitches.add(DatapathId.of("02:91:84:34:97:d6:0d:00")); //8
		physicalSwitches.add(DatapathId.of("01:04:84:34:97:d5:62:00")); //9
		physicalSwitches.add(DatapathId.of("01:04:6c:3b:e5:66:6b:00")); //10
		physicalSwitches.add(DatapathId.of("01:03:6c:3b:e5:63:21:00")); //11
		physicalSwitches.add(DatapathId.of("01:02:84:34:97:d6:0d:00")); //12
		physicalSwitches.add(DatapathId.of("01:02:84:34:97:d4:6c:00")); //13
		physicalSwitches.add(DatapathId.of("01:02:6c:3b:e5:6c:c5:00")); //14	
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService.addOFSwitchListener(this);
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		if (physicalSwitches.contains(switchId)) {
			IOFSwitch sw = switchService.getSwitch(switchId);
			OFFactory factory = sw.getOFFactory();
			OFFlowAdd.Builder fb = factory.buildFlowAdd();
			Match.Builder mb = factory.buildMatch();
			ArrayList<OFAction> actionList = new ArrayList<OFAction>();
			OFActionOutput.Builder aob = factory.actions().buildOutput();
			OFPort left = null;
			OFPort right = null;
			Collection<OFPort> ports = sw.getEnabledPortNumbers();
			if (!ports.isEmpty() && ports.size() == 3) { /* assume 2 physical and one LOCAL */
				Iterator<OFPort> itr = ports.iterator();
				OFPort tmp = itr.next();
				if (!tmp.equals(OFPort.LOCAL)) { /* got a hit for the first one */
					left = tmp; /* use tmp as left */
					tmp = itr.next(); /* poll next to see if it's the right */
					if (!tmp.equals(OFPort.LOCAL)) {
						right = tmp;
					} else {
						right = itr.next(); /* right must be the last one then */
					}
				} else { /* got a miss for the first one --> 2 and 3 are valid */
					left = itr.next();
					right = itr.next();
				}
			} else {
				log.debug("Switch {} has an empty list of enabled ports.", sw.getId().toString());
				return;
			}

			/* left to right */
			mb.setExact(MatchField.IN_PORT, left);
			aob.setPort(right);
			aob.setMaxLen(0xFFffFFff);
			actionList.add(aob.build());
			fb.setCookie(U64.of(0));
			fb.setBufferId(OFBufferId.NO_BUFFER);
			fb.setOutPort(OFPort.ANY);
			fb.setActions(actionList);
			fb.setMatch(mb.build());
			fb.setPriority(FlowModUtils.PRIORITY_MAX); // max priority
			fb.setHardTimeout(0); // should be infinite timeouts (i.e. as long as the switch is connected)
			fb.setIdleTimeout(0);
			sw.write(fb.build());
			log.debug("Writing flow on switch {} to from port {} to port " + left.toString() + ".", sw.getId().toString(), right.toString());
			log.debug("Flow: {}", fb.toString());
			actionList.clear();	

			/* right to left */
			mb.setExact(MatchField.IN_PORT, right);
			aob.setPort(left);
			aob.setMaxLen(0xFFffFFff);
			actionList.add(aob.build());
			fb.setCookie(U64.of(0));
			fb.setBufferId(OFBufferId.NO_BUFFER);
			fb.setOutPort(OFPort.ANY);
			fb.setActions(actionList);
			fb.setMatch(mb.build());
			fb.setPriority(FlowModUtils.PRIORITY_MAX); // max priority
			fb.setHardTimeout(0); // should be infinite timeouts (i.e. as long as the switch is connected)
			fb.setIdleTimeout(0);
			sw.write(fb.build());
			log.debug("Writing flow on switch {} to from port {} to port " + right.toString() + ".", sw.getId().toString(), left.toString());
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
