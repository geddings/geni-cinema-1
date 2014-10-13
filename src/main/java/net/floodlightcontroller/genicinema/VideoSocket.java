package net.floodlightcontroller.genicinema;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.TransportPort;

public class VideoSocket {
	private IPv4Address ip;
	private TransportPort port;
	private IpProtocol protocol;
	
	private VideoSocket(IPv4Address ip, TransportPort port, IpProtocol protocol) {
		this.ip = ip;
		this.port = port;
		this.protocol = protocol;
	}
	
	public IPv4Address getIP() {
		return this.ip;
	}
	
	public TransportPort getPort() {
		return this.port;
	}
	
	public IpProtocol getProtocol() {
		return this.protocol;
	}
	
	public VideoSocketBuilder createBuilder() {
		return new VideoSocketBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("ip=")
		.append(this.ip.toString())
		.append(", port=")
		.append(this.port.toString())
		.append(", protocol=")
		.append(this.protocol.toString())
		.toString();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());
		result = prime * result
				+ ((protocol == null) ? 0 : protocol.hashCode());
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
		VideoSocket other = (VideoSocket) obj;
		if (ip == null) {
			if (other.ip != null)
				return false;
		} else if (!ip.equals(other.ip))
			return false;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;
		if (protocol == null) {
			if (other.protocol != null)
				return false;
		} else if (!protocol.equals(other.protocol))
			return false;
		return true;
	}
	
	public static class VideoSocketBuilder {
		private IPv4Address b_ip;
		private TransportPort b_port;
		private IpProtocol b_protocol; 
		
		public VideoSocketBuilder() {
			this.b_ip = null;
			this.b_port = null;
			this.b_protocol = null;
		}
		
		private VideoSocketBuilder(VideoSocket videoSocket) {
			this.b_ip = videoSocket.ip;
			this.b_port = videoSocket.port;
			this.b_protocol = videoSocket.protocol;
		}
		
		public VideoSocketBuilder setIP(IPv4Address ip) {
			this.b_ip = ip;
			return this;
		}
		
		public VideoSocketBuilder setPort(TransportPort port) {
			this.b_port = port;
			return this;
		}
		
		public VideoSocketBuilder setProtocol(IpProtocol protocol) {
			this.b_protocol = protocol;
			return this;
		}
		
		private void checkAllSet() throws BuilderException {
			if (this.b_ip == null || this.b_port == null || this.b_protocol == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public VideoSocket build() {
			checkAllSet();
			return new VideoSocket(this.b_ip, this.b_port, this.b_protocol);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("ip=")
			.append(this.b_ip.toString())
			.append(", port=")
			.append(this.b_port.toString())
			.append(", protocol=")
			.append(this.b_protocol.toString())
			.toString();
		}
	}
}
