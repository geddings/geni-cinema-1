package net.floodlightcontroller.genicinema;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * An immutable OpenFlow component of the stream path
 * within our control. Consists of an ingress
 * port, the switch itself, and an egress port.
 * 
 * @author ryan, rizard@g.clemson.edu
 *
 */
public class Node {
	private DatapathId ofSwitch;
	private OFPort ingressSwitchPort;
	private OFPort egressSwitchPort;
	
	private Node(DatapathId dpid, OFPort in, OFPort out) {
		ofSwitch = dpid;
		ingressSwitchPort = in;
		egressSwitchPort = out;
	}
	
	public DatapathId getSwitchDpid() {
		return ofSwitch;
	}
	
	public OFPort getIngressPort() {
		return ingressSwitchPort;
	}
	
	public OFPort getEgressPort() {
		return egressSwitchPort;
	}
	
	public NodeBuilder createBuilder() {
		return new NodeBuilder(this);
	}	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("dpid=")
		.append(this.ofSwitch.toString())
		.append(", ingress=")
		.append(this.ingressSwitchPort.toString())
		.append(", egress=")
		.append(this.egressSwitchPort.toString())
		.toString();
	}
	
	@Override
	public boolean equals(Object node) {
		if (node == null) return false;
		if (!(node instanceof Node)) return false;
		Node that = (Node) node;
		if (!this.ofSwitch.equals(that.ofSwitch)) return false;
		if (!this.ingressSwitchPort.equals(that.ingressSwitchPort)) return false;
		if (!this.egressSwitchPort.equals(that.egressSwitchPort)) return false;
		return true;
	}
	
	public static class NodeBuilder { // static allows instantiation independent of an existing Node object
		private DatapathId b_ofSwitch;
		private OFPort b_ingressSwitchPort;
		private OFPort b_egressSwitchPort;
		
		public NodeBuilder() {
			this.b_ofSwitch = null;
			this.b_ingressSwitchPort = null;
			this.b_egressSwitchPort = null;
		}
		
		private NodeBuilder(Node node) {
			this.b_ofSwitch = DatapathId.of(node.ofSwitch.getLong());
			this.b_ingressSwitchPort = OFPort.of(node.ingressSwitchPort.getPortNumber());
			this.b_egressSwitchPort = OFPort.of(node.egressSwitchPort.getPortNumber());
		}
		
		public NodeBuilder setSwitchDpid(DatapathId dpid) {
			this.b_ofSwitch = DatapathId.of(dpid.getLong());
			return this;
		}
		
		public NodeBuilder setIngressPort(OFPort ingress) {
			this.b_ingressSwitchPort = OFPort.of(ingress.getPortNumber());
			return this;
		}
		
		public NodeBuilder setEgressPort(OFPort egress) {
			this.b_egressSwitchPort = OFPort.of(egress.getPortNumber());
			return this;
		}
		
		private void checkAllSet() throws BuilderException {
			if (this.b_ofSwitch == null || this.b_ingressSwitchPort == null
					|| this.b_egressSwitchPort == null ) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public Node build() {
			checkAllSet(); // throw execption if Node isn't complete
			return new Node(this.b_ofSwitch, this.b_ingressSwitchPort, this.b_egressSwitchPort);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("dpid=")
			.append(this.b_ofSwitch.toString())
			.append(", ingress=")
			.append(this.b_ingressSwitchPort.toString())
			.append(", egress=")
			.append(this.b_egressSwitchPort.toString())
			.toString();
		}
	} // END NODE BUILDER CLASS
} // END NODE CLASS
