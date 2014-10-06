package net.floodlightcontroller.genicinema;

import org.projectfloodlight.openflow.types.IPv4Address;

public class Gateway {
	private IPv4Address publicIP;
	private IPv4Address privateIP;
		
	private Gateway(IPv4Address publicIP, IPv4Address privateIP) {
		this.publicIP = publicIP;
		this.privateIP = privateIP;
	}
	
	public IPv4Address getPublicIP() {
		return this.publicIP;
	}
	
	public IPv4Address getPrivateIP() {
		return this.privateIP;
	}
	
	public GatewayBuilder createBuilder() {
		return new GatewayBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("public-ip=")
		.append(this.publicIP.toString())
		.append(", private-ip=")
		.append(this.privateIP.toString())
		.toString();
	}
	
	@Override
	public boolean equals(Object gateway) {
		if (gateway == null) return false;
		if (!(gateway instanceof Gateway)) return false;
		Gateway that = (Gateway) gateway;
		if (!this.publicIP.equals(that.publicIP)) return false;
		if (!this.privateIP.equals(that.privateIP)) return false;
		return true;
	}
	
	public static class GatewayBuilder {
		private IPv4Address b_publicIP;
		private IPv4Address b_privateIP;
		
		public GatewayBuilder() {
			this.b_publicIP = null;
			this.b_privateIP = null;
		}
		
		private GatewayBuilder(Gateway gateway) {
			this.b_publicIP = IPv4Address.of(gateway.publicIP.getInt());
			this.b_privateIP = IPv4Address.of(gateway.privateIP.getInt());
		}
		
		public GatewayBuilder setPublicIP(IPv4Address ip) {
			this.b_publicIP = IPv4Address.of(ip.getInt());
			return this;
		}
		
		public GatewayBuilder setPrivateIP(IPv4Address ip) {
			this.b_privateIP = IPv4Address.of(ip.getInt());
			return this;
		}
		
		private void checkAllSet() {
			if (this.b_publicIP == null || this.b_privateIP == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public Gateway build() {
			checkAllSet();
			return new Gateway(this.b_publicIP, this.b_privateIP);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("public-ip=")
			.append(this.b_publicIP.toString())
			.append(", private-ip=")
			.append(this.b_privateIP.toString())
			.toString();
		}
	}
}
