package net.floodlightcontroller.genicinema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple class that is instantiated and invoked as a runnable thread. 
 * The objective is to clean up dormant GENI Cinema clients and free up
 * the resources they were using.
 * 
 * @author Ryan Izard, rizard@g.clemson.edu
 *
 */
public class StreamGarbageCollector implements Runnable {
	private Logger log = LoggerFactory.getLogger(StreamGarbageCollector.class);

	@Override
	public void run() {
		GENICinemaManager gcm = GENICinemaManager.getInstance();
		if (gcm == null) {
			log.error("Manager instance variable was not set properly.");
			return;
		}
		log.debug("Cleaning up all GENI Cinema Clients...");
		gcm.cleanUpOldClients();
	}
}
