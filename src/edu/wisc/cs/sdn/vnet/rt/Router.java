package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
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
		IPv4 header = (IPv4) etherPacket.getPayload();

		// 3. check the checksum
		short prevCksum = header.getChecksum();
		header.resetChecksum();
		// 3.1. borrow code from the serialize() method in the IPv4 class to compute the checksum.
		//      if checksum == 0, serialize() will compute checksum
		header.serialize();
		short newCksum = header.getChecksum();
		// 3.2. check
		if(prevCksum != newCksum){
			System.out.println("checksum did not match, drop it");
			return;
		}

		// 4. handle Time-to-Live
		byte oldTtl = header.getTtl();
		header.setTtl((byte) (oldTtl - 1)); // decrement
		if(header.getTtl() == 0){
			System.out.println("TTL equals 0, time to die");
			return;
		}
		// 4.1. update checkout after TTL decrement
		header.resetChecksum();
		header.serialize();

		// 5. determine whether the packet is destined for one of the router’s interfaces.
		Collection<Iface> ifaces = this.getInterfaces().values();
		for(Iface i: ifaces){
			if(i.getIpAddress() == header.getDestinationAddress()){
				System.out.println("packet’s destination IP address exactly matches one of the interface’s IP addresses, drop it");
				return;
			}
		}

		// 6. Forward the packet
		RouteEntry route = routeTable.lookup(header.getDestinationAddress());
		// 6.1 check if route exists
		if(route == null){
			Systcem.out.println("No matching route, drop it");
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

	private
}
