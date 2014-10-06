package net.floodlightcontroller.loadthis;

public class LinkAttribiutes {

	int bandwith;
	Medium medium;
	boolean autonegotation;
	boolean pause;
	boolean asymetricPause;
	
	
	public LinkAttribiutes() {
		this.bandwith = 0;
		this.medium = Medium.COOPER;
		this.autonegotation = false;
		this.pause = false;
		this.asymetricPause = false;
	}

	public LinkAttribiutes(int bandwith, Medium medium, boolean autonegotation,
			boolean pause, boolean asymetricPause) {
		this.bandwith = bandwith;
		this.medium = medium;
		this.autonegotation = autonegotation;
		this.pause = pause;
		this.asymetricPause = asymetricPause;
	}
	
	public int getBandwith() {
		return bandwith;
	}
	public void setBandwith(int bandwith) {
		this.bandwith = bandwith;
	}
	public Medium getMedium() {
		return medium;
	}
	public void setMedium(Medium medium) {
		this.medium = medium;
	}
	public boolean isAutonegotation() {
		return autonegotation;
	}
	public void setAutonegotation(boolean autonegotation) {
		this.autonegotation = autonegotation;
	}
	public boolean isPause() {
		return pause;
	}
	public void setPause(boolean pause) {
		this.pause = pause;
	}
	public boolean isAsymetricPause() {
		return asymetricPause;
	}
	public void setAsymetricPause(boolean asymetricPause) {
		this.asymetricPause = asymetricPause;
	}
	
	public boolean isFulFillRequirements(LinkAttribiutes requirements){	
		//TODO: When added more link att need to extend this function
		
		if(this.bandwith < requirements.bandwith){
			//Not enough bandwith 
			return false;
		}
		
		if((requirements.medium == Medium.FIBER) && (this.medium == Medium.COOPER)){
			//Require fiber. It is cooper
			return false;
		}
		
		if((requirements.autonegotation == true) && (this.autonegotation == false)){
			return false;
		}
		
		if((requirements.pause == true) && (this.pause == false)){
			return false;
		}
		
		if((requirements.asymetricPause == true) && (this.asymetricPause == false)){
			return false;
		}
		
		return true;
	}
	
	
}
