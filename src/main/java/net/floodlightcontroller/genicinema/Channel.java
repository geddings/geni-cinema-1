package net.floodlightcontroller.genicinema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.types.OFGroup;

/**
 * An abstraction for the components that make up
 * a particular video channel. Immutable except for
 * counts and live-ness, which can be inc/dec and 
 * toggled respectively.
 * 
 * @author ryan, rizard@g.clemson.edu
 *
 */
public class Channel {
	/* The name of the program */
	private String name;
	/* What's the program about? */
	private String description;
	/* The identifier of the channel */
	private int id;
	/* The view password */
	private String viewPassword;
	/* The admin password */
	private String adminPassword;

	private VLCStreamServer hostServer;
	private Server hostPhysServer;

	/* Is the source present and streaming? */
	private boolean live;
	/* Are there any clients viewing the stream? */
	private int demandCount;

	/* The OVS in the path */
	private Node sortNode;

	/* The OFGroup assigned to this channel */
	private OFGroup group;

	/* All clients watching this Channel and their OFBuckets */
	private Map<Integer, OFBucket> bucketList;

	private Channel(String name, String description, int id,
			VLCStreamServer hostServer,
			Server hostPhysServer,
			boolean live, int demandCount,
			Node sortNode, OFGroup group,
			String viewPassword, String adminPassword,
			Map<Integer, OFBucket> bucketList) {
		this.name = name;
		this.description = description;
		this.id = id;
		this.hostServer = hostServer;
		this.hostPhysServer = hostPhysServer;
		this.live = live;
		this.demandCount = demandCount;
		this.sortNode = sortNode;
		this.group = group;
		this.viewPassword = viewPassword;
		this.adminPassword = adminPassword;
		this.bucketList = bucketList;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public int getId() {
		return this.id;
	}

	public VLCStreamServer getHostVLCStreamServer() {
		return this.hostServer;
	}
	
	public Server getHostServer() {
		return this.hostPhysServer;
	}

	public String getViewPassword() {
		return this.viewPassword;
	}

	public String getAdminPassword() {
		return this.adminPassword;
	}

	public boolean getLive() {
		return this.live;
	}

	public boolean getDemand() {
		return (this.demandCount > 0);
	}

	public int getDemandCount() {
		return this.demandCount;
	}

	public Node getSortNode() {
		return this.sortNode;
	}
	
	public void setSortNode(Node sort) {
		this.sortNode = sort;
	}

	public OFGroup getGroup() {
		return this.group;
	}
	
	public void setGroup(OFGroup group) {
		this.group = group;
	}

	public void turnOff() {
		this.live = false;
	}

	public void turnOn() {
		this.live = true;
	}

	public ArrayList<OFBucket> getBucketList() {
		return new ArrayList<OFBucket>(this.bucketList.values());
	}

	public void addBucket(int clientId, OFBucket bucketToAdd) {
		Integer key = new Integer(clientId);
		bucketList.put(key, bucketToAdd); // add/replace
		
		incrementDemand();
	}

	public void removeBucket(int clientId) {
		Integer key = new Integer(clientId);
		bucketList.remove(key); // if exists
		
		decrementDemand();
	}

	private void decrementDemand() {
		if (this.demandCount <= 0) {
			//no-op
		} else {
			this.demandCount = this.demandCount - 1;
		}
	}

	private void incrementDemand() { // no bounds checking
		this.demandCount = this.demandCount + 1;
	}

	public ChannelBuilder createBuilder() {
		return new ChannelBuilder(this);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		return sb.append("name=")
				.append(this.name)
				.append(", description=")
				.append(this.description)
				.append(", view-password=")
				.append(this.viewPassword)
				.append(", admin-password=")
				.append(this.adminPassword)
				.append(", id=")
				.append(this.id)
				.append(", host-vlcs-server=")
				.append(this.hostServer.toString())
				.append(", host-server=")
				.append(this.hostPhysServer.toString())
				.append(", live=")
				.append(this.live)
				.append(", demand=")
				.append(this.demandCount)
				.append(", sort-node=")
				.append(this.sortNode.toString())
				.append(", group=")
				.append(this.group.toString())
				.append(", bucket-list=")
				.append(this.bucketList.toString())
				.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((adminPassword == null) ? 0 : adminPassword.hashCode());
		result = prime * result
				+ ((bucketList == null) ? 0 : bucketList.hashCode());
		result = prime * result + demandCount;
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((group == null) ? 0 : group.hashCode());
		result = prime * result
				+ ((hostPhysServer == null) ? 0 : hostPhysServer.hashCode());
		result = prime * result
				+ ((hostServer == null) ? 0 : hostServer.hashCode());
		result = prime * result + id;
		result = prime * result + (live ? 1231 : 1237);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result
				+ ((sortNode == null) ? 0 : sortNode.hashCode());
		result = prime * result
				+ ((viewPassword == null) ? 0 : viewPassword.hashCode());
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
		Channel other = (Channel) obj;
		if (adminPassword == null) {
			if (other.adminPassword != null)
				return false;
		} else if (!adminPassword.equals(other.adminPassword))
			return false;
		if (bucketList == null) {
			if (other.bucketList != null)
				return false;
		} else if (!bucketList.equals(other.bucketList))
			return false;
		if (demandCount != other.demandCount)
			return false;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (group == null) {
			if (other.group != null)
				return false;
		} else if (!group.equals(other.group))
			return false;
		if (hostPhysServer == null) {
			if (other.hostPhysServer != null)
				return false;
		} else if (!hostPhysServer.equals(other.hostPhysServer))
			return false;
		if (hostServer == null) {
			if (other.hostServer != null)
				return false;
		} else if (!hostServer.equals(other.hostServer))
			return false;
		if (id != other.id)
			return false;
		if (live != other.live)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (sortNode == null) {
			if (other.sortNode != null)
				return false;
		} else if (!sortNode.equals(other.sortNode))
			return false;
		if (viewPassword == null) {
			if (other.viewPassword != null)
				return false;
		} else if (!viewPassword.equals(other.viewPassword))
			return false;
		return true;
	}

	public static class ChannelBuilder {
		private String b_name;
		private String b_description;
		private int b_id;
		private String b_viewPassword;
		private String b_adminPassword;
		private VLCStreamServer b_hostServer;
		private Server b_hostPhysServer;
		private boolean b_live;
		private int b_demandCount;
		private Node b_sortNode;
		private OFGroup b_group;
		private Map<Integer, OFBucket> b_bucketList;

		public ChannelBuilder() {
			b_name = null;
			b_description = null;
			b_id = -1;
			b_viewPassword = "";
			b_adminPassword = "";
			b_hostServer = null;
			b_hostPhysServer = null;
			b_live = false;
			b_demandCount = 0;
			b_sortNode = null;
			b_group = null;
			b_bucketList = new HashMap<Integer, OFBucket>();
		}

		private ChannelBuilder(Channel channel) {
			b_name = channel.name;
			b_description = channel.description;
			b_id = channel.id;
			b_viewPassword = channel.viewPassword;
			b_adminPassword = channel.adminPassword;
			b_hostServer = channel.hostServer;
			b_hostPhysServer = channel.hostPhysServer;
			b_live = channel.live;
			b_demandCount = channel.demandCount;
			b_sortNode = channel.sortNode;
			b_group = OFGroup.of(channel.group.getGroupNumber());
			b_bucketList = new HashMap<Integer, OFBucket>(channel.bucketList);
		}

		public ChannelBuilder setName(String name) {
			this.b_name = name;
			return this;
		}

		public ChannelBuilder setDescription(String description) {
			this.b_description = description;
			return this;
		}

		public ChannelBuilder setId(int id) {
			this.b_id = id;
			return this;
		}

		public ChannelBuilder setViewPassword(String viewPassword) {
			this.b_viewPassword = viewPassword;
			return this;
		}

		public ChannelBuilder setAdminPassword(String adminPassword) {
			this.b_adminPassword = adminPassword;
			return this;
		}

		public ChannelBuilder setHostVLCStreamServer(VLCStreamServer hostServer) {
			this.b_hostServer = hostServer;
			return this;
		}
		
		public ChannelBuilder setHostServer(Server hostPhysServer) {
			this.b_hostPhysServer = hostPhysServer;
			return this;
		}

		public ChannelBuilder setLive(boolean live) {
			this.b_live = live;
			return this;
		}

		public ChannelBuilder setSortNode(Node sort) {
			if (sort != null) {
				this.b_sortNode = sort;
			} else {
				this.b_sortNode = null;
			}
			return this;
		}
		
		public ChannelBuilder setGroup(OFGroup group) {
			this.b_group = OFGroup.of(group.getGroupNumber());
			return this;
		}
		
		public ChannelBuilder addBucket(int clientId, OFBucket bucket) {
			Integer key = new Integer(clientId);
			this.b_bucketList.put(key, bucket); // add/replace
			return this;
		}

		private void checkAllSet() throws BuilderException {
			if (this.b_name == null || this.b_description == null
					|| this.b_viewPassword == null || this.b_adminPassword == null
					|| this.b_id == -1 || this.b_hostServer == null
					|| this.b_hostPhysServer == null
					/*|| this.b_sortNode == null || this.b_group == null  When Channel is created, a sort Node and OFGroup will not be assigned yet; only when a viewer connects */
					|| this.b_bucketList == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}

		public Channel build() {
			checkAllSet(); // throw execption if Channel isn't complete
			return new Channel(this.b_name, this.b_description, this.b_id, this.b_hostServer, 
					this.b_hostPhysServer, this.b_live, this.b_demandCount, 
					this.b_sortNode, this.b_group,
					this.b_viewPassword, this.b_adminPassword,
					this.b_bucketList);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb.append("name=")
					.append(this.b_name)
					.append(", description=")
					.append(this.b_description)
					.append(", id=")
					.append(this.b_id)
					.append(", view-password=")
					.append(this.b_viewPassword)
					.append(", admin-password=")
					.append(this.b_adminPassword)
					.append(", host-vlcs-server=")
					.append(this.b_hostServer.toString())
					.append(", host-server=")
					.append(this.b_hostPhysServer.toString())
					.append(", live=")
					.append(this.b_live)
					.append(", demand=")
					.append(this.b_demandCount)
					.append(", sort-node=")
					.append(this.b_sortNode.toString())
					.append(", group=")
					.append(this.b_group.toString())
					.append(", bucket-list=")
					.append(this.b_bucketList.toString())
					.toString();
		}
	} // END CHANNEL BUILDER CLASS
} // END CHANNEL CLASS
