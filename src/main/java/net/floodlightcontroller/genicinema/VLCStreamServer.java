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
	
	public void setInUse() {
		this.isAvailable = false;
	}
	
	public void setNotInUse() {
		this.isAvailable = false;
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((egress == null) ? 0 : egress.hashCode());
		result = prime * result + ((ingress == null) ? 0 : ingress.hashCode());
		result = prime * result + (isAvailable ? 1231 : 1237);
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
		VLCStreamServer other = (VLCStreamServer) obj;
		if (egress == null) {
			if (other.egress != null)
				return false;
		} else if (!egress.equals(other.egress))
			return false;
		if (ingress == null) {
			if (other.ingress != null)
				return false;
		} else if (!ingress.equals(other.ingress))
			return false;
		if (isAvailable != other.isAvailable)
			return false;
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
			this.b_ingress = vlcStreamServer.ingress;
			this.b_egress = vlcStreamServer.egress;
			this.b_isAvailable = vlcStreamServer.isAvailable;
		}
		
		public VLCStreamServerBuilder setIngress(VideoSocket ingress) {
			this.b_ingress = ingress;
			return this;
		}
		
		public VLCStreamServerBuilder setEgress(VideoSocket egress) {
			this.b_egress = egress;
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
