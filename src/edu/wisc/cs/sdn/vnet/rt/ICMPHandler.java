package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

public class ICMPHandler {
    public static Ethernet generateIcmpPacket(int type, int code, Iface iface, Ethernet etherPacket){
        // 1. set Ethernet header
        Ethernet ether = new Ethernet();
        // 1.1. set EtherType
        ether.setEtherType(Ethernet.TYPE_IPv4);
        // 1.2. set Source MAC to the MAC address of the out interface
        ether.setSourceMACAddress(iface.getMacAddress().toBytes())
        // 1.3. set Destination MAC: set to the MAC address of the next hop


        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);
    }
}

