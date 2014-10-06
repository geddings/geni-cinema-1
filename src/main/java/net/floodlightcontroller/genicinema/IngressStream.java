package net.floodlightcontroller.genicinema;

/**
 * A representation of the information relevant to a inbound
 * GENI Cinema video stream.
 * 
 * @author ryan
 *
 */
public class IngressStream {
	/*
	 * Who is the client?
	 */
	private VideoSocket client;
	
	/* 
	 * Represents the gateway to receive
	 * the stream. The stream will simply
	 * pass through this gateway and no
	 * VLCS should be on this ingress
	 * gateway
	 */
	private Gateway ingress;
	
	/*
	 * Where will the stream be stored
	 * and how can it be fetched? 
	 */
	private VLCStreamServer server;
	
	private IngressStream(VideoSocket client, Gateway ingress, VLCStreamServer server) {
		this.client = client;
		this.ingress = ingress;
		this.server = server;
	}
	
	public VideoSocket getClient() {
		return this.client;
	}
	
	public Gateway getGateway() {
		return this.ingress;
	}
	
	public VLCStreamServer getServer() {
		return this.server;
	}
	
	public IngressStreamBuilder createBuilder() {
		return new IngressStreamBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("client=")
		.append(this.client.toString())
		.append(", gateway=")
		.append(this.ingress.toString())
		.append(", server=")
		.append(this.server.toString())
		.toString();
	}
	
	@Override
	public boolean equals(Object ingressStream) {
		if (ingressStream == null) return false;
		if (!(ingressStream instanceof IngressStream)) return false;
		IngressStream that = (IngressStream) ingressStream;
		if (!this.client.equals(that.client)) return false;
		if (!this.ingress.equals(that.ingress)) return false;
		if (!this.server.equals(that.server)) return false;
		return true;
	}
	
	public static class IngressStreamBuilder{
		private VideoSocket b_client;
		private Gateway b_ingress;
		private VLCStreamServer b_server;
		
		public IngressStreamBuilder() {
			this.b_client = null;
			this.b_ingress = null;
			this.b_server = null;
		}
		
		private IngressStreamBuilder(IngressStream ingressStream) {
			this.b_client = ingressStream.client.createBuilder().build();
			this.b_ingress = ingressStream.ingress.createBuilder().build();
			this.b_server = ingressStream.server.createBuilder().build();
		}
		
		public IngressStreamBuilder setClient(VideoSocket client) {
			this.b_client = client.createBuilder().build();
			return this;
		}
		
		public IngressStreamBuilder setGateway(Gateway ingress) {
			this.b_ingress = ingress;
			return this;
		}
		
		public IngressStreamBuilder setServer(VLCStreamServer server) {
			this.b_server = server.createBuilder().build();
			return this;
		}
		
		
		private void checkAllSet() throws BuilderException {
			if (this.b_client == null || this.b_ingress == null
					|| this.b_server == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}
		
		public IngressStream build() {
			checkAllSet();
			return new IngressStream(this.b_client, this.b_ingress, this.b_server);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("client=")
			.append(this.b_client.toString())
			.append(", gateway=")
			.append(this.b_ingress.toString())
			.append(", server=")
			.append(this.b_server.toString())
			.toString();
		}
	}
}
