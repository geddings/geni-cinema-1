package net.floodlightcontroller.genicinema;

import java.util.Date;


/**
 * A representation of the information relevant to a outbound
 * GENI Cinema video stream.
 * 
 * @author ryan
 *
 */
public class EgressStream {
	/* 
	 * Info on the source video stream.
	 */
	private Channel channel;
	
	/* 
	 * A unique ID number assigned to the stream/client.
	 */
	private int clientId;
	
	/* 
	 * Represents the sockets at each side of
	 * the gateway, where the VLC server receives
	 * on the ingress e.g. via UDP, transcodes, 
	 * and streams again on the egress e.g. 
	 * in RTSP.
	 */
	private VLCStreamServer gateway;
	
	/*
	 * Who is the client?
	 */
	private VideoSocket client;
	
	/*
	 * Keep track of when the client was last active.
	 */
	private Date lastActive;
	
	private EgressStream(Channel channel, int clientId,
			VLCStreamServer gateway, VideoSocket client, Date lastActive) {
		this.channel = channel;
		this.clientId = clientId;
		this.gateway = gateway;
		this.client = client;
		this.lastActive = lastActive;
	}
	
	public Channel getChannel() {
		return this.channel;
	}
	
	public void changeChannel(Channel channel) {
		this.channel = channel;
	}
	
	public int getId() {
		return this.clientId;
	}
	
	public VLCStreamServer getVLCSSAtGateway() {
		return this.gateway;
	}
	
	public VideoSocket getClient() {
		return this.client;
	}
	
	public Date getActiveLast() {
		return this.lastActive;
	}
	
	public void setActiveNow() {
		this.lastActive.setTime(System.currentTimeMillis());
	}
	
	public EgressStreamBuilder createBuilder() {
		return new EgressStreamBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("channel=")
		.append(this.channel.toString())
		.append(", client-id=")
		.append(this.clientId)
		.append(", gateway=")
		.append(this.gateway.toString())
		.append(", client=")
		.append(this.client.toString())
		.toString();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((channel == null) ? 0 : channel.hashCode());
		result = prime * result + ((client == null) ? 0 : client.hashCode());
		result = prime * result + clientId;
		result = prime * result + ((gateway == null) ? 0 : gateway.hashCode());
		result = prime * result
				+ ((lastActive == null) ? 0 : lastActive.hashCode());
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
		EgressStream other = (EgressStream) obj;
		if (channel == null) {
			if (other.channel != null)
				return false;
		} else if (!channel.equals(other.channel))
			return false;
		if (client == null) {
			if (other.client != null)
				return false;
		} else if (!client.equals(other.client))
			return false;
		if (clientId != other.clientId)
			return false;
		if (gateway == null) {
			if (other.gateway != null)
				return false;
		} else if (!gateway.equals(other.gateway))
			return false;
		if (lastActive == null) {
			if (other.lastActive != null)
				return false;
		} else if (!lastActive.equals(other.lastActive))
			return false;
		return true;
	}
	
	public static class EgressStreamBuilder{
		private Channel b_channel;
		private int b_clientId;
		private VLCStreamServer b_gateway;
		private VideoSocket b_client;
		private Date b_lastActive;
		
		public EgressStreamBuilder() {
			this.b_channel = null;
			this.b_clientId = -1;
			this.b_gateway = null;
			this.b_client = null;
			this.b_lastActive = new Date();
		}
		
		private EgressStreamBuilder(EgressStream egressStream) {
			this.b_channel = egressStream.channel;
			this.b_clientId = egressStream.clientId;
			this.b_gateway = egressStream.gateway;
			this.b_client = egressStream.client;
			this.b_lastActive = egressStream.lastActive;
		}
		
		public EgressStreamBuilder setChannel(Channel channel) {
			this.b_channel = channel;
			return this;
		}
		
		public EgressStreamBuilder setId(int clientId) {
			this.b_clientId = clientId;
			return this;
		}
		
		public EgressStreamBuilder setGateway(VLCStreamServer gateway) {
			this.b_gateway = gateway;
			return this;
		}
		
		public EgressStreamBuilder setClient(VideoSocket client) {
			this.b_client = client;
			return this;
		}
		
		private void checkAllSet() throws BuilderException {
			if (this.b_channel == null || this.b_client == null
					|| this.b_clientId == -1 || this.b_gateway == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public EgressStream build() {
			checkAllSet();
			return new EgressStream(this.b_channel, this.b_clientId, this.b_gateway, this.b_client, b_lastActive);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("channel=")
			.append(this.b_channel.toString())
			.append(", client-id=")
			.append(this.b_clientId)
			.append(", gateway=")
			.append(this.b_gateway.toString())
			.append(", client=")
			.append(this.b_client.toString())
			.append(", last-active=")
			.append(this.b_lastActive.toString())
			.toString();
		}
	}
}
