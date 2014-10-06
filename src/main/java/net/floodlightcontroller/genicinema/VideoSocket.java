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
	public boolean equals(Object videoSocket) {
		if (videoSocket == null) return false;
		if (!(videoSocket instanceof VideoSocket)) return false;
		VideoSocket that = (VideoSocket) videoSocket;
		if (!this.ip.equals(that.ip)) return false;
		if (!this.port.equals(that.port)) return false;
		if (!this.protocol.equals(that.protocol)) return false;
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
			this.b_ip = IPv4Address.of(videoSocket.ip.getInt());
			this.b_port = TransportPort.of(videoSocket.port.getPort());
			this.b_protocol = IpProtocol.of(videoSocket.protocol.getIpProtocolNumber());
		}
		
		public VideoSocketBuilder setIP(IPv4Address ip) {
			this.b_ip = IPv4Address.of(ip.getInt());
			return this;
		}
		
		public VideoSocketBuilder setPort(TransportPort port) {
			this.b_port = TransportPort.of(port.getPort());
			return this;
		}
		
		public VideoSocketBuilder setProtocol(IpProtocol protocol) {
			this.b_protocol = IpProtocol.of(protocol.getIpProtocolNumber());
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
