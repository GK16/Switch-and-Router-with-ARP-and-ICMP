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
	private HashMap<MACAddress, Iface> table = new HashMap<>();

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

		// 1. update the forword table
		MACAddress macAddress = etherPacket.getSourceMAC();
		table.put(macAddress, inIface);

		// 2. find MAC Address of the destination
		MACAddress destAddress = etherPacket.getDestinationMAC();

		// 2.1. if found in the table, forward
		if(table.containsKey(destAddress)){
			Iface destIface = table.get(destAddress);
			this.sendPacket(etherPacket, destIface);
			System.out.println("Found in Table. Sent packet to " + destAddress.toString());
		}

		// 2.2. if not found in the table, boadcast
		else {
			Collection<Iface> interfaces = this.getInterfaces().values();
			for (Iface i : interfaces) {
				if (!i.equals(inIface)) this.sendPacket(etherPacket, i);
			}
			System.out.println("did not find in table. Boadcast.");
		}
		
		/********************************************************************/
	}
}
