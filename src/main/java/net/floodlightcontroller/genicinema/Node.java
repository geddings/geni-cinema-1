package net.floodlightcontroller.genicinema;

import java.util.ArrayList;

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
	boolean flowsCleared;
	private ArrayList<OFPort> ingressSwitchPort;
	private ArrayList<OFPort> egressSwitchPort;
	
	private Node(DatapathId dpid, ArrayList<OFPort> in, ArrayList<OFPort> out) {
		ofSwitch = dpid;
		ingressSwitchPort = in;
		egressSwitchPort = out;
	}
	
	public DatapathId getSwitchDpid() {
		return ofSwitch;
	}
		
	public OFPort getIngressPort() {
		return ingressSwitchPort.get(0);
	}
	
	public OFPort getEgressPort() {
		return egressSwitchPort.get(0);
	}
	
	public ArrayList<OFPort> getIngressPorts() {
		return new ArrayList<OFPort>(ingressSwitchPort);
	}
	
	public ArrayList<OFPort> getEgressPorts() {
		return new ArrayList<OFPort>(egressSwitchPort);
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((egressSwitchPort == null) ? 0 : egressSwitchPort.hashCode());
		result = prime
				* result
				+ ((ingressSwitchPort == null) ? 0 : ingressSwitchPort
						.hashCode());
		result = prime * result
				+ ((ofSwitch == null) ? 0 : ofSwitch.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Node other = (Node) obj;
		if (egressSwitchPort == null) {
			if (other.egressSwitchPort != null)
				return false;
		} else if (!egressSwitchPort.equals(other.egressSwitchPort))
			return false;
		if (ingressSwitchPort == null) {
			if (other.ingressSwitchPort != null)
				return false;
		} else if (!ingressSwitchPort.equals(other.ingressSwitchPort))
			return false;
		if (ofSwitch == null) {
			if (other.ofSwitch != null)
				return false;
		} else if (!ofSwitch.equals(other.ofSwitch))
			return false;
		return true;
	}
	
	public static class NodeBuilder { // static allows instantiation independent of an existing Node object
		private DatapathId b_ofSwitch;
		private ArrayList<OFPort> b_ingressSwitchPort;
		private ArrayList<OFPort> b_egressSwitchPort;
		
		public NodeBuilder() {
			this.b_ofSwitch = null;
			this.b_ingressSwitchPort = new ArrayList<OFPort>();
			this.b_egressSwitchPort = new ArrayList<OFPort>();
		}
		
		private NodeBuilder(Node node) {
			this.b_ofSwitch = node.ofSwitch;
			this.b_ingressSwitchPort = new ArrayList<OFPort>(node.ingressSwitchPort);
			this.b_egressSwitchPort = new ArrayList<OFPort>(node.egressSwitchPort);
		}
		
		public NodeBuilder setSwitchDpid(DatapathId dpid) {
			this.b_ofSwitch = dpid;
			return this;
		}
		
		public NodeBuilder addIngressPort(OFPort ingress) {
			this.b_ingressSwitchPort.add(ingress);
			return this;
		}
		
		public NodeBuilder addEgressPort(OFPort egress) {
			this.b_egressSwitchPort.add(egress);
			return this;
		}
		
		public NodeBuilder setIngressPorts(ArrayList<OFPort> ingress) {
			this.b_ingressSwitchPort = ingress;
			return this;
		}
		
		public NodeBuilder setEgressPorts(ArrayList<OFPort> egress) {
			this.b_egressSwitchPort = egress;
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
