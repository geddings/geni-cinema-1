package net.floodlightcontroller.genicinema;


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
	
	private EgressStream(Channel channel, int clientId,
			VLCStreamServer gateway, VideoSocket client) {
		this.channel = channel;
		this.clientId = clientId;
		this.gateway = gateway;
		this.client = client;
	}
	
	public Channel getChannel() {
		return this.channel;
	}
	
	public int getId() {
		return this.clientId;
	}
	
	public VLCStreamServer getGateway() {
		return this.gateway;
	}
	
	public VideoSocket getClient() {
		return this.client;
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
	public boolean equals(Object egressStream) {
		if (egressStream == null) return false;
		if (!(egressStream instanceof EgressStream)) return false;
		EgressStream that = (EgressStream) egressStream;
		if (!this.channel.equals(that.channel)) return false;
		if (this.clientId != that.clientId) return false;
		if (!this.gateway.equals(that.gateway)) return false;
		if (!this.client.equals(that.client)) return false;
		return true;
	}
	
	public static class EgressStreamBuilder{
		private Channel b_channel;
		private int b_clientId;
		private VLCStreamServer b_gateway;
		private VideoSocket b_client;
		
		public EgressStreamBuilder() {
			this.b_channel = null;
			this.b_clientId = -1;
			this.b_gateway = null;
			this.b_client = null;
		}
		
		private EgressStreamBuilder(EgressStream egressStream) {
			this.b_channel = egressStream.channel.createBuilder().build();
			this.b_clientId = egressStream.clientId;
			this.b_gateway = egressStream.gateway.createBuilder().build();
			this.b_client = egressStream.client.createBuilder().build();
		}
		
		public EgressStreamBuilder setChannel(Channel channel) {
			this.b_channel = channel.createBuilder().build();
			return this;
		}
		
		public EgressStreamBuilder setId(int clientId) {
			this.b_clientId = clientId;
			return this;
		}
		
		public EgressStreamBuilder setGateway(VLCStreamServer gateway) {
			this.b_gateway = gateway.createBuilder().build();
			return this;
		}
		
		public EgressStreamBuilder setClient(VideoSocket client) {
			this.b_client = client.createBuilder().build();
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
			return new EgressStream(this.b_channel, this.b_clientId, this.b_gateway, this.b_client);
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
			.toString();
		}
	}
}
