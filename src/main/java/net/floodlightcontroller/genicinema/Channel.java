package net.floodlightcontroller.genicinema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.TransportPort;

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

	/* Where is it hosted? On which VLC backend server? */
	private IPv4Address ipSource;	
	/* And, on which port? (must be UDP) */
	private TransportPort udpSource;

	/* Is the source present and streaming? */
	private boolean live;
	/* Are there any clients viewing the stream? */
	private int demandCount;

	/* The OVS at the host server */
	private Node hostNode;

	/* The OVS in the path */
	private Node sortNode;

	/* The OFGroup assigned to this channel */
	private OFGroup group;

	/* All clients watching this Channel and their OFBuckets */
	private Map<Integer, OFBucket> bucketList;

	private Channel(String name, String description, int id,
			IPv4Address ipSource, TransportPort udpSource,
			boolean live, int demandCount,
			Node hostNode, Node sortNode, OFGroup group,
			String viewPassword, String adminPassword,
			Map<Integer, OFBucket> bucketList) {
		this.name = name;
		this.description = description;
		this.id = id;
		this.ipSource = ipSource;
		this.udpSource = udpSource;
		this.live = live;
		this.demandCount = demandCount;
		this.hostNode = hostNode;
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

	public IPv4Address getHostIP() {
		return this.ipSource;
	}

	public String getViewPassword() {
		return this.description;
	}

	public String getAdminPassword() {
		return this.description;
	}

	public TransportPort getHostUDP() {
		return this.udpSource;
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

	public Node getHostNode() {
		return this.hostNode;
	}

	public Node getSortNode() {
		return this.sortNode;
	}

	public OFGroup getGroup() {
		return this.group;
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
	}

	public void removeBucket(int clientId) {
		Integer key = new Integer(clientId);
		bucketList.remove(key); // if exists
	}

	public int decrementDemand() {
		if (this.demandCount <= 0) {
			return this.demandCount;
		} else {
			return --this.demandCount;
		}
	}

	public int incrementDemand() { // no bounds checking
		return ++this.demandCount;
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
				.append(", host-ip=")
				.append(this.ipSource.toString())
				.append(", host-udp=")
				.append(this.udpSource.toString())
				.append(", live=")
				.append(this.live)
				.append(", demand=")
				.append(this.demandCount)
				.append(", host-node=")
				.append(this.hostNode.toString())
				.append(", sort-node=")
				.append(this.sortNode.toString())
				.append(", group=")
				.append(this.group.toString())
				.append(", bucket-list=")
				.append(this.bucketList.toString())
				.toString();
	}

	@Override
	public boolean equals(Object channel) {
		if (channel == null) return false;
		if (!(channel instanceof Channel)) return false;
		Channel that = (Channel) channel;
		if (!this.name.equals(that.name)) return false;
		if (!this.description.equals(that.description)) return false;
		if (!this.viewPassword.equals(that.viewPassword)) return false;
		if (!this.adminPassword.equals(that.adminPassword)) return false;
		if (this.id != that.id) return false;
		if (!this.ipSource.equals(that.ipSource)) return false;
		if (!this.udpSource.equals(that.udpSource)) return false;
		if (this.live != that.live) return false;
		if (this.demandCount != that.demandCount) return false;
		if (!this.hostNode.equals(that.hostNode)) return false;
		if (!this.sortNode.equals(that.sortNode)) return false;
		if (!this.group.equals(that.group)) return false;
		if (!this.bucketList.equals(that.bucketList)) return false;
		return true;
	}

	public static class ChannelBuilder {
		private String b_name;
		private String b_description;
		private int b_id;
		private String b_viewPassword;
		private String b_adminPassword;
		private IPv4Address b_ipSource;	
		private TransportPort b_udpSource;
		private boolean b_live;
		private int b_demandCount;
		private Node b_hostNode;
		private Node b_sortNode;
		private OFGroup b_group;
		private Map<Integer, OFBucket> b_bucketList;

		public ChannelBuilder() {
			b_name = null;
			b_description = null;
			b_id = -1;
			b_viewPassword = "";
			b_adminPassword = "";
			b_ipSource = null;
			b_udpSource = null;
			b_live = false;
			b_demandCount = -1;
			b_hostNode = null;
			b_sortNode = null;
			b_group = null;
			b_bucketList = new HashMap<Integer, OFBucket>();
		}

		private ChannelBuilder(Channel channel) {
			b_name = new String(channel.name);
			b_description = new String(channel.description);
			b_id = channel.id;
			b_viewPassword = channel.viewPassword;
			b_adminPassword = channel.adminPassword;
			b_ipSource = IPv4Address.of(channel.ipSource.getInt());
			b_udpSource = TransportPort.of(channel.udpSource.getPort());
			b_live = channel.live;
			b_demandCount = channel.demandCount;
			b_hostNode = channel.hostNode.createBuilder().build();
			b_sortNode = channel.sortNode.createBuilder().build();
			b_group = OFGroup.of(channel.group.getGroupNumber());
			b_bucketList = new HashMap<Integer, OFBucket>(channel.bucketList);
		}

		public ChannelBuilder setName(String name) {
			this.b_name = new String(name);
			return this;
		}

		public ChannelBuilder setDescription(String description) {
			this.b_description = new String(description);
			return this;
		}

		public ChannelBuilder setId(int id) {
			this.b_id = id;
			return this;
		}

		public ChannelBuilder setViewPassword(String viewPassword) {
			this.b_viewPassword = new String(viewPassword);
			return this;
		}

		public ChannelBuilder setAdminPassword(String adminPassword) {
			this.b_adminPassword = new String(adminPassword);
			return this;
		}

		public ChannelBuilder setHostIP(IPv4Address host) {
			this.b_ipSource = IPv4Address.of(host.getInt());
			return this;
		}

		public ChannelBuilder setHostUDP(TransportPort udpPort) {
			this.b_udpSource = TransportPort.of(udpPort.getPort());
			return this;
		}

		public ChannelBuilder setLive(boolean live) {
			this.b_live = live;
			return this;
		}

		public ChannelBuilder setHostNode(Node host) {
			this.b_hostNode = host.createBuilder().build();
			return this;
		}

		public ChannelBuilder setSortNode(Node sort) {
			this.b_sortNode = sort.createBuilder().build();
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
					|| this.b_id == -1 || this.b_ipSource == null
					|| this.b_udpSource == null || this.b_hostNode == null
					/*|| this.b_sortNode == null || this.b_group == null  When Channel is created, a sort Node and OFGroup will not be assigned yet; only when a viewer connects */
					|| this.b_bucketList == null) {
				throw new BuilderException("All components of " + this.getClass().getSimpleName() + " must be non-null: " + this.toString());
			}
		}

		public Channel build() {
			checkAllSet(); // throw execption if Channel isn't complete
			return new Channel(this.b_name, this.b_description, this.b_id, this.b_ipSource, 
					this.b_udpSource, this.b_live, this.b_demandCount, this.b_hostNode, 
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
					.append(", host-ip=")
					.append(this.b_ipSource.toString())
					.append(", host-udp=")
					.append(this.b_udpSource.toString())
					.append(", live=")
					.append(this.b_live)
					.append(", demand=")
					.append(this.b_demandCount)
					.append(", host-node=")
					.append(this.b_hostNode.toString())
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
