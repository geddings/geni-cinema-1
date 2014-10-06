package net.floodlightcontroller.genicinema;

import org.projectfloodlight.openflow.types.IPv4Address;

public class Server {
	private IPv4Address ingressIP;
	private IPv4Address egressIP;
	private Node ovs;
		
	private Server(IPv4Address ingressIP, IPv4Address egressIP, Node ovs) {
		this.ingressIP = ingressIP;
		this.egressIP = egressIP;
		this.ovs = ovs;
	}
	
	public IPv4Address getPublicIP() {
		return this.ingressIP;
	}
	
	public IPv4Address getPrivateIP() {
		return this.egressIP;
	}
	
	public Node getOVSNode() {
		return this.ovs;
	}
	
	public ServerBuilder createBuilder() {
		return new ServerBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("ingress-ip=")
		.append(this.ingressIP.toString())
		.append(", egress-ip=")
		.append(this.egressIP.toString())
		.append(", node-ovs=")
		.append(this.ovs.toString())
		.toString();
	}
	
	@Override
	public boolean equals(Object server) {
		if (server == null) return false;
		if (!(server instanceof Server)) return false;
		Server that = (Server) server;
		if (!this.ingressIP.equals(that.ingressIP)) return false;
		if (!this.egressIP.equals(that.egressIP)) return false;
		if (!this.ovs.equals(that.ovs)) return false;
		return true;
	}
	
	public static class ServerBuilder {
		private IPv4Address b_ingressIP;
		private IPv4Address b_egressIP;
		private Node b_ovs;
		
		public ServerBuilder() {
			this.b_ingressIP = null;
			this.b_egressIP = null;
			this.b_ovs = null;
		}
		
		private ServerBuilder(Server server) {
			this.b_ingressIP = IPv4Address.of(server.ingressIP.getInt());
			this.b_egressIP = IPv4Address.of(server.egressIP.getInt());
			this.b_ovs = server.ovs.createBuilder().build();
		}
		
		public ServerBuilder setPublicIP(IPv4Address ip) {
			this.b_ingressIP = IPv4Address.of(ip.getInt());
			return this;
		}
		
		public ServerBuilder setPrivateIP(IPv4Address ip) {
			this.b_egressIP = IPv4Address.of(ip.getInt());
			return this;
		}
		
		public ServerBuilder setOVSNode(Node ovs) {
			this.b_ovs = ovs.createBuilder().build();
			return this;
		}
		
		private void checkAllSet() {
			if (this.b_ingressIP == null || this.b_egressIP == null || this.b_ovs == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public Server build() {
			checkAllSet();
			return new Server(this.b_ingressIP, this.b_egressIP, this.b_ovs);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("ingress-ip=")
			.append(this.b_ingressIP.toString())
			.append(", egress-ip=")
			.append(this.b_egressIP.toString())
			.append(", node-ovs=")
			.append(this.b_ovs.toString())
			.toString();
		}
	}
}
