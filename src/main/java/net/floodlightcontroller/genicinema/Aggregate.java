package net.floodlightcontroller.genicinema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;

public class Aggregate {
	private String name;
	private String description; 

	private ArrayList<Gateway> ingressGateways;
	private ArrayList<Gateway> egressGateways;

	private ArrayList<Node> ovsVMs;
	private ArrayList<Server> vlcsVMs;

	private Map<Node, ArrayDeque<OFGroup>> availableOFGroupsPerSortNode;

	private Map<Node, Boolean> isNodeConnected;

	private static final int MAX_GROUPS = 10;

	private Aggregate(String name, String description, ArrayList<Gateway> ingressGateways, ArrayList<Gateway> egressGateways, 
			ArrayList<Node> ovsVMs, ArrayList<Server> vlcsVMs,
			Map<Node, ArrayDeque<OFGroup>> groupsPerSortNode,
			Map<Node, Boolean> isNodeConnected) {
		this.name = name;
		this.description = description;
		this.ingressGateways = ingressGateways;
		this.egressGateways = egressGateways;
		this.ovsVMs = ovsVMs;
		this.vlcsVMs = vlcsVMs;
		this.availableOFGroupsPerSortNode = groupsPerSortNode;
		this.isNodeConnected = isNodeConnected;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public ArrayList<Gateway> getIngressGateways() {
		return this.ingressGateways;
	}

	public ArrayList<Gateway> getEgressGateways() {
		return this.egressGateways;
	}

	public ArrayList<Node> getSwitches() {
		return this.ovsVMs;
	}

	public ArrayList<Server> getServers() {
		return this.vlcsVMs;
	}

	public OFGroup getAvailableOFGroup(Node node) {
		if (!ovsVMs.contains(node)) {
			return null;
		}

		ArrayDeque<OFGroup> groups = availableOFGroupsPerSortNode.get(node);

		if (groups.isEmpty()) {
			return null;
		}

		return groups.pop();
	}

	/**
	 * NOTE: This should ONLY be used for initializing the switch's
	 * group table where getting a complete list of all OFGroups
	 * is convenient and most efficient. Any time an OFGroup is 
	 * requested, use instead getAvailableOFGroup(Node node).
	 * 
	 * This function returns a new List, so even though use of this
	 * is discouraged outside initialization, you should not have to
	 * worry about modifying the OFGroups within the Aggregate. Any
	 * changes to the returned List will not impact the Aggregate's
	 * internal list.
	 * 
	 * @param node
	 * @return
	 */
	public ArrayList<OFGroup> peekOFGroups(Node node) {
		ArrayDeque<OFGroup> groups = availableOFGroupsPerSortNode.get(node);
		if (groups == null) {
			return null;
		}
		// OFGroup is immutable, so using the original OFGroup objects in the
		// new List is okay (b/c they cannot be changed...only the new List can).
		return new ArrayList<OFGroup>(groups); 
	}

	public void returnOFGroup(OFGroup group, Node node) {
		if (!ovsVMs.contains(node)) {
			return;
		}

		ArrayDeque<OFGroup> groups = availableOFGroupsPerSortNode.get(node);

		if (!groups.contains(group)) {
			groups.push(group);
		}
	}

	public boolean isConnected(DatapathId dpid) {
		Node foundNode = null;
		for (Node node : isNodeConnected.keySet()) {
			if (node.getSwitchDpid().equals(dpid)) {
				foundNode = node;
				break;
			}
		}

		if (foundNode == null) {
			return false;
		} else {
			return isNodeConnected.get(foundNode).booleanValue();
		}
	}

	public boolean switchConnected(DatapathId dpid) {
		Node foundNode = null;
		for (Node node : isNodeConnected.keySet()) {
			if (node.getSwitchDpid().equals(dpid)) {
				foundNode = node;
				break;
			}
		}

		if (foundNode == null) {
			return false;
		} else {
			isNodeConnected.put(foundNode, true);
			return true;
		}
	}
	
	public boolean switchDisconnected(DatapathId dpid) {
		Node foundNode = null;
		for (Node node : isNodeConnected.keySet()) {
			if (node.getSwitchDpid().equals(dpid)) {
				foundNode = node;
				break;
			}
		}

		if (foundNode == null) {
			return false;
		} else {
			isNodeConnected.put(foundNode, false);
			return true;
		}
	}

	public AggregateBuilder createBuilder() {
		return new AggregateBuilder(this);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("name=")
				.append(this.name.toString())
				.append(", description=")
				.append(this.description.toString())
				.append(", ingress-gw=")
				.append(this.ingressGateways.toString())
				.append(", egress-gw=")
				.append(this.egressGateways.toString())
				.append(", switches=")
				.append(this.ovsVMs.toString())
				.append(", servers=")
				.append(this.vlcsVMs.toString())
				.toString();
	}

	@Override
	public boolean equals(Object aggregate) {
		if (aggregate == null) return false;
		if (!(aggregate instanceof Aggregate)) return false;
		Aggregate that = (Aggregate) aggregate;
		if (!this.name.equals(that.name)) return false;
		if (!this.description.equals(that.description)) return false;
		if (!this.ingressGateways.equals(that.ingressGateways)) return false;
		if (!this.egressGateways.equals(that.egressGateways)) return false;
		if (!this.vlcsVMs.equals(that.vlcsVMs)) return false;
		if (!this.ovsVMs.equals(that.ovsVMs)) return false;
		if (!this.availableOFGroupsPerSortNode.equals(that.availableOFGroupsPerSortNode)) return false;
		if (!this.isNodeConnected.equals(that.isNodeConnected)) return false;
		return true;
	}

	public static class AggregateBuilder {
		private String b_name;
		private String b_description;

		private ArrayList<Gateway> b_ingressGateways;
		private ArrayList<Gateway> b_egressGateways;

		private ArrayList<Node> b_ovsVMs;
		private ArrayList<Server> b_vlcsVMs;

		private Map<Node, ArrayDeque<OFGroup>> b_availableOFGroupsPerSortNode;

		private Map<Node, Boolean> b_isNodeConnected;

		public AggregateBuilder() {
			this.b_name = null;
			this.b_description = null;
			this.b_ingressGateways = new ArrayList<Gateway>();
			this.b_egressGateways = new ArrayList<Gateway>();
			this.b_ovsVMs = new ArrayList<Node>();
			this.b_vlcsVMs = new ArrayList<Server>();
			this.b_availableOFGroupsPerSortNode = new HashMap<Node, ArrayDeque<OFGroup>>();
			this.b_isNodeConnected = new HashMap<Node, Boolean>();
		}

		public AggregateBuilder(Aggregate aggregate) {
			this.b_name = new String(aggregate.name);
			this.b_description = new String(aggregate.description);
			this.b_ingressGateways = new ArrayList<Gateway>(aggregate.ingressGateways);
			this.b_egressGateways = new ArrayList<Gateway>(aggregate.egressGateways);
			this.b_ovsVMs = new ArrayList<Node>(aggregate.ovsVMs);
			this.b_vlcsVMs = new ArrayList<Server>(aggregate.vlcsVMs);
			this.b_availableOFGroupsPerSortNode = new HashMap<Node, ArrayDeque<OFGroup>>(aggregate.availableOFGroupsPerSortNode);
			this.b_isNodeConnected = new HashMap<Node, Boolean>(aggregate.isNodeConnected);
		}

		public AggregateBuilder setName(String name) {
			this.b_name = name;
			return this;
		}

		public AggregateBuilder setDescription(String description) {
			this.b_description = description;
			return this;
		}

		public AggregateBuilder addIngressGateway(Gateway ingress) {
			if (!this.b_ingressGateways.contains(ingress)) {
				this.b_ingressGateways.add(ingress);
			}
			return this;
		}

		public AggregateBuilder addEgressGateway(Gateway egress) {
			if (!this.b_egressGateways.contains(egress)) {
				this.b_egressGateways.add(egress);
			}
			return this;
		}

		public AggregateBuilder addSwitch(Node node) {
			if (!this.b_ovsVMs.contains(node)) {
				this.b_ovsVMs.add(node);
			}

			if (!this.b_availableOFGroupsPerSortNode.containsKey(node)) {
				ArrayDeque<OFGroup> groups = new ArrayDeque<OFGroup>(MAX_GROUPS);
				for (int i = MAX_GROUPS; i > 0; i--) {
					groups.push(OFGroup.of(i));
				}
				this.b_availableOFGroupsPerSortNode.put(node, groups);
			}

			if (!this.b_isNodeConnected.containsKey(node)) {
				this.b_isNodeConnected.put(node, false);
			}

			return this;
		}

		public AggregateBuilder addServer(Server server) {
			if (!this.b_vlcsVMs.contains(server)) {
				this.b_vlcsVMs.add(server);
			}

			if (!this.b_isNodeConnected.containsKey(server.getOVSNode())) {
				this.b_isNodeConnected.put(server.getOVSNode(), false);
			}

			return this;
		}

		public AggregateBuilder setIngressGateways(ArrayList<Gateway> ingress) {
			this.b_ingressGateways = ingress;
			return this;
		}

		public AggregateBuilder setEgressGateways(ArrayList<Gateway> egress) {
			this.b_egressGateways = egress;
			return this;
		}

		public AggregateBuilder setSwitches(ArrayList<Node> nodes) {
			for (Node node : nodes) {
				this.addSwitch(node);
			}
			return this;
		}

		public AggregateBuilder addServers(ArrayList<Server> servers) {
			for (Server server : servers) {
				addServer(server);
			}
			return this;
		}

		private void checkAllSet() {
			if (this.b_description == null || this.b_egressGateways == null || this.b_ingressGateways == null
					|| this.b_name == null || this.b_ovsVMs == null || this.b_vlcsVMs == null
					|| this.b_availableOFGroupsPerSortNode == null
					|| this.b_isNodeConnected == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}

		public Aggregate build() {
			checkAllSet();
			return new Aggregate(this.b_name, this.b_description, this.b_ingressGateways, this.b_egressGateways, 
					this.b_ovsVMs, this.b_vlcsVMs, this.b_availableOFGroupsPerSortNode, this.b_isNodeConnected);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("name=")
					.append(this.b_name.toString())
					.append(", description=")
					.append(this.b_description.toString())
					.append(", ingress-gw=")
					.append(this.b_ingressGateways.toString())
					.append(", egress-gw=")
					.append(this.b_egressGateways.toString())
					.append(", switches=")
					.append(this.b_ovsVMs.toString())
					.append(", servers=")
					.append(this.b_vlcsVMs.toString())
					.append(", groups-per-switch=")
					.append(this.b_availableOFGroupsPerSortNode)
					.append(", is-node-connected=")
					.append(this.b_isNodeConnected)
					.toString();
		}
	}

}
