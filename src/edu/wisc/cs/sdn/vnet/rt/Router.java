package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ICMP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */

		switch(etherPacket.getEtherType())
		{
			case Ethernet.TYPE_IPv4:
				this.handleIpPacket(etherPacket, inIface);
				break;
			// Ignore all other packet types, for now
		}

		/********************************************************************/
	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{
			System.out.println("TTL = 0");
			sendICMPPacket(11, 0, inIface, ipPacket);
			return;
		}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{
				// (1) If a TCP or UDP header comes after the IP header
				if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
					sendICMPPacket(3, 3, inIface, ipPacket);
					return;
				}
				// (2) If an ICMP header comes after the IP header
				if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP) {
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();
					//  If the ICMP message is an echo request (used by ping)
					if (icmpPacket.getIcmpType() == (byte) 8){
						// handle ICMP echo request
						handleIcmpEchoRequest(ipPacket, icmpPacket, inIface);
					}
					return;
				}
			}
		}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, send ICMP Packet;
		if (null == bestMatch)
		{
			System.out.println("no entry matched in the route table");
			sendICMPPacket(3, 0, inIface, ipPacket);
			return;
		}

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}

	private MACAddress findNextHopMACAddress(int DestIP){
		// 1. loop up the routeTable
		RouteEntry routeEntry = this.routeTable.lookup(DestIP);
		if(routeEntry == null){
			System.err.println("No match Dest IP in routeTable.");
			return null;
		}
		// 2. get the next hop IP address
		int nextHopIP = routeEntry.getGatewayAddress();
		if(nextHopIP == 0){
			nextHopIP = DestIP;
		}
		// 3. find next hop MAC address from arpCache
		ArpEntry arpEntry = this.arpCache.lookup(nextHopIP);
		if(arpEntry == null){
			System.err.println("ICMP: no nuch IP in arpCache");
			return null;
		};
		return arpEntry.getMac();
	}

	private void sendICMPPacket(int type, int code, Iface iface, IPv4 ipPacket){
		// 1. set Ethernet header
		Ethernet ether = new Ethernet();
		// 1.1. set EtherType
		ether.setEtherType(Ethernet.TYPE_IPv4);
		// 1.2. set Source MAC to the MAC address of the out interface
		ether.setSourceMACAddress(iface.getMacAddress().toBytes());

		// 1.3. set Destination MAC: set to the MAC address of the next hop
		int DestIP = ipPacket.getSourceAddress();
		MACAddress nextHopMacAddr = findNextHopMACAddress(DestIP);
		// 1.3.1. if the MAC address associated with an IP address cannot be resolved using ARP.
		//if(destMAC == null) {
		//	RouteEntry rEntry = routeTable.lookup(pktIn.getSourceAddress());
		//	/* Find the next hop IP Address */
		//	int nextHopIPAddress = rEntry.getGatewayAddress();
		//	if(nextHopIPAddress == 0){
		//		nextHopIPAddress = pktIn.getSourceAddress();
		//	}
		//	this.sendARPRequest(ether, inIface, rEntry.getInterface(), nextHopIPAddress);
		//	return;
		//}

		ether.setDestinationMACAddress(nextHopMacAddr.toBytes());
		System.out.println(nextHopMacAddr);

		// 2. set IP header
		IPv4 ip = generateIpPacket(IPv4.PROTOCOL_ICMP, iface.getIpAddress(), ipPacket.getSourceAddress());
		//IPv4 ip = new IPv4();
		// 2.1. TTL—setto64
		//ip.setTtl((byte) 64);
		// 2.2. Protocol — set to IPv4.PROTOCOL_ICMP
		//ip.setProtocol(IPv4.PROTOCOL_ICMP);
		// 2.3. Source IP — set to the IP address of the interface on which the original packet arrived
		//ip.setSourceAddress(iface.getIpAddress());
		// 2.4. Destination IP — set to the source IP of the original packet
		//ip.setDestinationAddress(ipPacket.getSourceAddress());

		// 3. set ICMP header
		ICMP icmp = generateIcmpPacket(type, code);
		//ICMP icmp = new ICMP();
		// 3.1. set ICMP type
		//icmp.setIcmpType((byte)type);
		// 3.2. set ICMP code
		//icmp.setIcmpCode((byte)code);

		// 4. assemble the ICMP payload
		Data data = new Data();
		// 4.1. construct byteArray
		int origialIPHeaderLength = ipPacket.getHeaderLength() * 4;
		byte[] byteArray = new byte[4 + origialIPHeaderLength + 8];
		// 4.2. copy bytes from IpPacket
		byte[] serializedIpPacket = ipPacket.serialize();
		int serializedIpPacketLen = serializedIpPacket.length;
		for(int i = 0; i < origialIPHeaderLength + 8; i++){
			if (i < serializedIpPacketLen) byteArray[4 + i] = serializedIpPacket[i];
			else break;
		}

		// 5. assemble the ICMP Packet
		data.setData(byteArray);
		icmp.setPayload(data);
		ip.setPayload(icmp);
		ether.setPayload(ip);

		// 6. send ICMP Packet
		this.sendPacket(ether, iface);
	}

	private void handleIcmpEchoRequest(IPv4 ipPacket, ICMP icmpPacket, Iface inIface){
		System.out.println("handling an ICMP echo request");
		Ethernet echoReply = getCommonEchoReply(ipPacket, icmpPacket, inIface);
		sendPacket(echoReply, inIface);
	}

	private Ethernet getCommonEchoReply(IPv4 ipPacket, ICMP icmpPacket, Iface inIface){
		// 1. set Ethernet header
		Ethernet ether = new Ethernet();
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		int nextHop = this.routeTable.lookup(ipPacket.getSourceAddress()).getGatewayAddress();
		if (nextHop == 0) {
			nextHop = ipPacket.getSourceAddress();
		}
		ether.setDestinationMACAddress(this.arpCache.lookup(nextHop).getMac().toBytes());

		// 2. set IP header
		IPv4 ip = generateIpPacket(IPv4.PROTOCOL_ICMP, ipPacket.getDestinationAddress(), ipPacket.getSourceAddress());

		// 3. set ICMP header
		ICMP icmp = generateIcmpPacket(0, 0);

		// 4. assemble the Packet
		ether.setPayload(ip);
		ip.setPayload(icmp);
		Data icmpRequestPayload = (Data) icmpPacket.getPayload();
		icmp.setPayload(icmpRequestPayload);
		return ether;
	}

	private IPv4 generateIpPacket(byte protocol, int sourceAddress, int destAddress){
		IPv4 ip = new IPv4();
		ip.setTtl((byte) 64);
		ip.setProtocol(protocol);
		ip.setSourceAddress(sourceAddress);
		ip.setDestinationAddress(destAddress);
		return ip;
	}

	private ICMP generateIcmpPacket(int type, int code){
		ICMP icmp = new ICMP();
		icmp.setIcmpType((byte) type);
		icmp.setIcmpCode((byte) code);
		return icmp;
	}
}
