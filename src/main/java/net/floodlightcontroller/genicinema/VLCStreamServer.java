package net.floodlightcontroller.genicinema;

public class VLCStreamServer {
	private VideoSocket ingress;
	private VideoSocket egress;
	private boolean isAvailable;
	
	private VLCStreamServer(VideoSocket ingress, VideoSocket egress, boolean isAvailable) {
		this.ingress = ingress;
		this.egress = egress;
		this.isAvailable = isAvailable;
	}
	
	public VideoSocket getIngress() {
		return this.ingress;
	}
	
	public VideoSocket getEgress() {
		return this.egress;
	}
	
	public boolean isAvailable() {
		return this.isAvailable;
	}
	
	public VLCStreamServerBuilder createBuilder() {
		return new VLCStreamServerBuilder(this);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("ingress=")
		.append(this.ingress.toString())
		.append(", egress=")
		.append(this.egress.toString())
		.append(", available=")
		.append(this.isAvailable)
		.toString();
	}
	
	@Override
	public boolean equals(Object vlcStreamServer) {
		if (vlcStreamServer == null) return false;
		if (!(vlcStreamServer instanceof VLCStreamServer)) return false;
		VLCStreamServer that = (VLCStreamServer) vlcStreamServer;
		if (!this.ingress.equals(that.ingress)) return false;
		if (!this.egress.equals(that.egress)) return false;
		if (this.isAvailable != that.isAvailable) return false;
		return true;
	}
	
	public static class VLCStreamServerBuilder {
		private VideoSocket b_ingress;
		private VideoSocket b_egress;
		private boolean b_isAvailable;
		
		public VLCStreamServerBuilder() {
			this.b_ingress = null;
			this.b_egress = null;
			this.b_isAvailable = true;
		}
		
		private VLCStreamServerBuilder(VLCStreamServer vlcStreamServer) {
			this.b_ingress = vlcStreamServer.ingress.createBuilder().build();
			this.b_egress = vlcStreamServer.egress.createBuilder().build();
			this.b_isAvailable = vlcStreamServer.isAvailable;
		}
		
		public VLCStreamServerBuilder setIngress(VideoSocket ingress) {
			this.b_ingress = ingress.createBuilder().build();
			return this;
		}
		
		public VLCStreamServerBuilder setEgress(VideoSocket egress) {
			this.b_egress = egress.createBuilder().build();
			return this;
		}
		
		public VLCStreamServerBuilder setAvailable(boolean isAvailable) {
			this.b_isAvailable = isAvailable;
			return this;
		}
		
		private void checkAllSet() throws BuilderException {
			if (this.b_ingress == null || this.b_egress == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}	
		}
		
		public VLCStreamServer build() {
			checkAllSet();
			return new VLCStreamServer(this.b_ingress, this.b_egress, this.b_isAvailable);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("ingress=")
			.append(this.b_ingress.toString())
			.append(", egress=")
			.append(this.b_egress.toString())
			.append(", egress=")
			.append(this.b_isAvailable)
			.toString();
		}
	}
}
