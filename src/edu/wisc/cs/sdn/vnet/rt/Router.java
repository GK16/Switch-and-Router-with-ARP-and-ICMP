package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ICMP;
import java.util.*;

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

		// 1. check Ethernet frame type
		if(etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
			System.out.println("not a IPV4 packet, drop it");
			return;
		}

		// 2. get IPv4 packet payload
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();

		// 3. check the checksum
		short prevCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		// 3.1. borrow code from the serialize() method in the IPv4 class to compute the checksum.
		//      if checksum == 0, serialize() will compute checksum
		ipPacket.serialize();
		short newCksum = ipPacket.getChecksum();
		// 3.2. check
		if(prevCksum != newCksum){
			System.out.println("checksum did not match, drop it");
			return;
		}

		// 4. handle Time-to-Live
		byte oldTtl = ipPacket.getTtl();
		ipPacket.setTtl((byte) (oldTtl - 1)); // decrement
		if(ipPacket.getTtl() == 0){
			System.out.println("TTL equals 0, time to die");
			sendICMPPacket(11, 0, inIface, ipPacket)
			return;
		}
		// 4.1. update checkout after TTL decrement
		ipPacket.resetChecksum();
		ipPacket.serialize();

		// 5. determine whether the packet is destined for one of the router’s interfaces.
		Collection<Iface> ifaces = this.getInterfaces().values();
		for(Iface i: ifaces){
			if(i.getIpAddress() == ipPacket.getDestinationAddress()){
				System.out.println("packet’s destination IP address exactly matches one of the interface’s IP addresses, drop it");
				return;
			}
		}

		// 6. Forward the packet
		RouteEntry route = routeTable.lookup(ipPacket.getDestinationAddress());
		// 6.1 check if route exists
		if(route == null){
			System.out.println("No matching route, drop it");
			return;
		}
		// 6.2 obtain & set the new MAC address
		ArpEntry arpEntry = arpCache.lookup(route.getDestinationAddress());
		MACAddress newMacAddr = arpEntry.getMac();
		MACAddress sourMacAddr = route.getInterface().getMacAddress();
		etherPacket.setSourceMACAddress(sourMacAddr.toString());
		etherPacket.setDestinationMACAddress(newMacAddr.toString());
		// 6.3 send packet
		boolean res = this.sendPacket(etherPacket, route.getInterface());
		System.out.println("Forwarding " + (res ? "successed!" : "failed!"));
		
		/********************************************************************/
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
		ether.setDestinationMACAddress(nextHopMacAddr.toBytes());

		// 2. set IP header
		IPv4 ip = new IPv4();
		// 2.1. TTL—setto64
		ip.setTtl((byte) 64);
		// 2.2. Protocol — set to IPv4.PROTOCOL_ICMP
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		// 2.3. Source IP — set to the IP address of the interface on which the original packet arrived
		ip.setSourceAddress(iface.getIpAddress());
		// 2.4. Destination IP — set to the source IP of the original packet
		ip.setDestinationAddress(ipPacket.getSourceAddress());

		// 3. set ICMP header
		ICMP icmp = new ICMP();
		// 3.1. set ICMP type
		icmp.setIcmpType((byte)type);
		// 3.2. set ICMP code
		icmp.setIcmpCode((byte)code);

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

}
