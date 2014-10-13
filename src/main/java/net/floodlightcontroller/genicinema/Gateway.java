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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((privateIP == null) ? 0 : privateIP.hashCode());
		result = prime * result
				+ ((publicIP == null) ? 0 : publicIP.hashCode());
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
		Gateway other = (Gateway) obj;
		if (privateIP == null) {
			if (other.privateIP != null)
				return false;
		} else if (!privateIP.equals(other.privateIP))
			return false;
		if (publicIP == null) {
			if (other.publicIP != null)
				return false;
		} else if (!publicIP.equals(other.publicIP))
			return false;
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
			this.b_publicIP = gateway.publicIP;
			this.b_privateIP = gateway.privateIP;
		}
		
		public GatewayBuilder setPublicIP(IPv4Address ip) {
			this.b_publicIP = ip;
			return this;
		}
		
		public GatewayBuilder setPrivateIP(IPv4Address ip) {
			this.b_privateIP = ip;
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
