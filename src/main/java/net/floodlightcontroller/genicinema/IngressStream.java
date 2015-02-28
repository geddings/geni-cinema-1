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
	
	public VLCStreamServer getVLCSServer() {
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((client == null) ? 0 : client.hashCode());
		result = prime * result + ((ingress == null) ? 0 : ingress.hashCode());
		result = prime * result + ((server == null) ? 0 : server.hashCode());
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
		IngressStream other = (IngressStream) obj;
		if (client == null) {
			if (other.client != null)
				return false;
		} else if (!client.equals(other.client))
			return false;
		if (ingress == null) {
			if (other.ingress != null)
				return false;
		} else if (!ingress.equals(other.ingress))
			return false;
		if (server == null) {
			if (other.server != null)
				return false;
		} else if (!server.equals(other.server))
			return false;
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
			this.b_client = ingressStream.client;
			this.b_ingress = ingressStream.ingress;
			this.b_server = ingressStream.server;
		}
		
		public IngressStreamBuilder setClient(VideoSocket client) {
			this.b_client = client;
			return this;
		}
		
		public IngressStreamBuilder setGateway(Gateway ingress) {
			this.b_ingress = ingress;
			return this;
		}
		
		public IngressStreamBuilder setServer(VLCStreamServer server) {
			this.b_server = server;
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
