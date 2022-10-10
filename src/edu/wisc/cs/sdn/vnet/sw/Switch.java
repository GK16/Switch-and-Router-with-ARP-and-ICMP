package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	/**
	 * forward table which contains learnt record
	 * key: MAC Address
	 * value: Interface
	 */
	private HashMap<MACAddress, Iface> table = new HashMap<>();

	/**
	 * time table which contains time limit
	 * key: MAC Address
	 * long: time left
	 */
	private HashMap<MACAddress, Long> timeLimits = new HashMap<>();

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
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

		// 1. update the forward table and expired time
		MACAddress macAddress = etherPacket.getSourceMAC();
		table.put(macAddress, inIface);
		timeLimits.put(macAddress, System.currentTimeMillis());

		// 2. find MAC Address of the destination
		MACAddress destAddress = etherPacket.getDestinationMAC();

		// 2.1. if found in the table
		if(table.containsKey(destAddress) && timeLimits.containsKey(destAddress)){
			long timeSince = System.currentTimeMillis() - timeLimits.get(destAddress);
			// if the record is still valid, send it.
			if(timeSince < 15000){
				Iface destIface = table.get(destAddress);
				this.sendPacket(etherPacket, destIface);
				System.out.println("Found in Table. "+ String.valueOf(timeSince) +" ms since it updated.");
				System.out.println("Sent packet to " + destAddress.toString());
				return;
			}
			// if he record expired, remove the record.
			table.remove(destAddress);
			timeLimits.remove(destAddress);
			System.out.println("Found but expired.");
		}

		// 2.2. if not found in the table or expired, boadcast
		Collection<Iface> interfaces = this.getInterfaces().values();
		for (Iface i : interfaces) {
			if (!i.equals(inIface)) this.sendPacket(etherPacket, i);
		}
		System.out.println("Boadcasted.");

		
		/********************************************************************/
	}
}
