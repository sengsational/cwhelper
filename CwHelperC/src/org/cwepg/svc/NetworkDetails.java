package org.cwepg.svc;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

public class NetworkDetails {

	private static NetworkDetails networkDetails = null;
	private static ArrayList<String> foundMachineAddresses = new ArrayList<>();
	private static long lastInquiry = 0;

	private NetworkDetails() {
	}
	
	private static NetworkDetails getInstance() {
		if (networkDetails == null) {
			networkDetails = new NetworkDetails();
		}
		return networkDetails;
	}
	
	public static boolean isOnMachineSubnet(String ip2, String subnetMask, boolean debug) throws UnknownHostException {
    	long msSinceLast = new Date().getTime() - lastInquiry;
		if (foundMachineAddresses.size() == 0 || msSinceLast > 100000) queryLan(debug); //only query if it's not been done yet, or has been a while.
		
		for (String address : foundMachineAddresses) {
			if(isSameSubnet(address, ip2, subnetMask, debug)) return true;
		}
		return false;
	}
	

    public static boolean isSameSubnet(String ip1, String ip2, String subnetMask, boolean debug) throws UnknownHostException {
			if (ip1.split("\\.").length == 4) {
				boolean allGood = true;
				if (debug) System.out.println("trying [" + ip1 + "]");
		        InetAddress inetAddress1 = InetAddress.getByName(ip1);
		        InetAddress inetAddress2 = InetAddress.getByName(ip2);
		        InetAddress subnetAddress = InetAddress.getByName(subnetMask);

		        byte[] ip1Bytes = inetAddress1.getAddress();
		        byte[] ip2Bytes = inetAddress2.getAddress();
		        byte[] subnetBytes = subnetAddress.getAddress();

		        for (int i = 0; i < 4; i++) {
		            if ((ip1Bytes[i] & subnetBytes[i]) != (ip2Bytes[i] & subnetBytes[i])) {
		                allGood = false;
		            }
		        }
		        if (allGood) return true;
			} else {
				//if (debug) System.out.println("not trying [" + ip1 + "]");
			}
		return false;
    }
    
    private static void queryLan(boolean debug) {
        StringBuffer buf = new StringBuffer();
        lastInquiry = new Date().getTime();
        try {
            for (int tries = 2; tries > 0; tries--) {
                Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
                if (debug) System.out.println(new Date() + " DEBUG: interface has elements " + e.hasMoreElements());
                while(e.hasMoreElements()) {
                    NetworkInterface n = (NetworkInterface) e.nextElement();
                    if (debug) System.out.println(new Date() + " DEBUG: interface " + n.getName() + " " + n.getDisplayName());
                    buf.append("  Interface [" + n.getName() + "] Addresses [" );
                    Enumeration<InetAddress> ee = n.getInetAddresses();
                    if (debug) System.out.println(new Date() + " DEBUG: addresses has elements " + ee.hasMoreElements());
                    while (ee.hasMoreElements()) {
                        InetAddress i = (InetAddress) ee.nextElement();
                        String aMachineAddress = i.getHostAddress();
                        if (debug) System.out.println(new Date() + " DEBUG: address " + aMachineAddress);
                        buf.append(aMachineAddress + ", ");
                        foundMachineAddresses.add(aMachineAddress);
                    }
                    buf.append("]");
                }
                if (foundMachineAddresses.size() > 0) {
                    break;
                } else {
                    System.out.println(new Date() + " The list of interfaces [" + buf.toString() + "].  Retrying after 1 second.");
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            System.out.println(new Date()  + " ERROR: Could not get IP address of the machine " + e.getMessage());
        } finally {
            if (debug) System.out.println(new Date() + " Looked at these machine IP's: " + buf.toString());
        }
	}

	public static String getMachineIpForTunerIp(String someIpAddress) throws UnknownHostException {
		boolean debug = false;
    	long msSinceLast = new Date().getTime() - lastInquiry;
		if (foundMachineAddresses.size() == 0 || msSinceLast > 100000) queryLan(debug); //only query if it's not been done yet, or has been a while.
		
		for (String aMachineIp : foundMachineAddresses) {
			if (NetworkDetails.isSameSubnet(aMachineIp, someIpAddress, "255.255.255.0", false)) return aMachineIp;
		}
		return null;
	}

    
	public String toString() {
		StringBuffer buf = new StringBuffer();
		for (String string : foundMachineAddresses) {
			buf.append(string).append("\n");
		}
		return buf.toString();
	}
	
	public static void main(String[] args) throws UnknownHostException {
		boolean debug = false;
		
		String someIpAddress = "192.168.2.2";
		//System.out.println(someIpAddress + (NetworkDetails.isSameSubnet(someIpAddress, "255.255.255.0", debug)?" is on subnet":" is not on subnet"));
		
		someIpAddress = "192.168.3.2";
		//System.out.println(someIpAddress + (NetworkDetails.isSameSubnet(someIpAddress, "255.255.255.0", debug)?" is on subnet":" is not on subnet"));
		
		
		String machineIpForTuner = NetworkDetails.getMachineIpForTunerIp(someIpAddress);
		System.out.println("The machine was [" + machineIpForTuner + "] for tuner [" + someIpAddress + "]");
		
	}

}
