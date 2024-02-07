package org.cwepg.hr;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class ChannelDigital extends Channel implements Comparable {

    public ChannelDigital(String channelName, Tuner tuner, double priority, String protocol) throws Exception {
        this.channelKey = channelName + ":1-" + protocol; // Defined as real
        // rf channel number
        // dot pid colon
        // input
        this.channelDescription = channelKey; // Can be anything, but defaults
        this.tuner = tuner;
        this.priority = priority;
        int[] channels = splitDottedChannel(channelName);
        this.frequency = "" + channels[0];
        this.pid = "" + channels[1];
        this.protocol = protocol;
    }

    // Constructor for MyHD external recording (not in lineup)
    public ChannelDigital(String channelName, Tuner tuner, String channelVirtual, String protocol) throws Exception {
        this.tuner = tuner;
        this.priority = 1;
        int[] channels = splitDottedChannel(channelName);
        this.frequency = "" + channels[0];
        this.pid = "" + channels[1];
        String cv = "0.0";
        if (channelVirtual.indexOf(":") > 0 && !channelVirtual.endsWith(":")) { // MyHD
            // Channels
            // have
            // nn.n:i
            // (i=input
            // 1 or
            // 2)
            cv = channelVirtual.substring(0, channelVirtual.indexOf(":"));
            this.input = channelVirtual.substring(channelVirtual.indexOf(":") + 1);
        } else {
            throw new Exception("The virtual channel must be in the form x.y:z (virt.subch:input)");
        }
        int[] virtuals = splitDottedChannel(cv);
        this.virtualChannel = virtuals[0];
        this.virtualSubchannel = virtuals[1];
        this.channelVirtual = cv;
        this.protocol = protocol;
        this.channelKey = channelName + ":" + this.input + "-" + protocol; // Defined
        // as
        // real
        // rf
        // channel
        // number
        // dot
        // pid
        // colon
        // input
        this.channelDescription = channelKey; // Can be anything, but defaults
        this.alphaDescription = channelKey; // Can be anything, but defaults
    }

    private int[] splitDottedChannel(String dc) throws Exception {
        if (dc == null || dc.trim().equals("") || dc.indexOf(".") < 1 || dc.indexOf(".") == dc.length())
            throw new Exception("The channel " + dc + " was not properly formatted x.y");
        int[] virtuals = new int[2];
        int dotLoc = dc.indexOf(".");
        String left = dc.substring(0, dotLoc);
        String right = dc.substring(dotLoc + 1);
        try {
            virtuals[0] = Integer.parseInt(left);
            virtuals[1] = Integer.parseInt(right);
        } catch (Exception e) {
            throw new Exception("The channel " + dc + " was not made from two numbers with a '.' between.");
        }
        return virtuals;
    }

    // this.channelKey + "|" + this.pid + "|" + this.frequency + "|" +
    // this.protocol + "|" + this.priority + "|" + this.channelDescription + "|"
    // this.alphaDescription + "|" + this.channelVirtual + "|" + this.megahertz
    // + "|" + this.ss + "|" + this.seq + "|" + this.snq + "|" + this.input +
    // "|"
    // this.virtualChannel + "|" + this.virtualSubchannel + "|"
    // this.physical[i][TUNER_STANDARD] + "|" + this.physical[i][TUNER_VALUE] +
    // "|");
    /* Only used with HDHR tuners */
    public ChannelDigital(String persistenceData, Tuner tuner) {
        ArrayList<String[]> phy = new ArrayList<String[]>();
        StringTokenizer tok = new StringTokenizer(persistenceData, "|");
        this.channelKey = tok.nextToken();
        this.pid = tok.nextToken();
        this.frequency = tok.nextToken();
        this.protocol = tok.nextToken();
        this.priority = Double.parseDouble(tok.nextToken());
        this.channelDescription = tok.nextToken();
        if (tok.countTokens() >= 9) { // can get rid of this once all old
            // persistance files are gone.
            this.alphaDescription = tok.nextToken();
            this.channelVirtual = tok.nextToken();
            this.megahertz = tok.nextToken();
            this.ss = tok.nextToken();
            this.seq = tok.nextToken();
            this.snq = tok.nextToken();
            this.input = tok.nextToken();
            try {
                this.virtualChannel = Integer.parseInt(tok.nextToken());
            } catch (Exception e) {/* ignore */
            }
            ;
            try {
                this.virtualSubchannel = Integer.parseInt(tok.nextToken());
            } catch (Exception e) {/* ignore */
            }
            ;
        }

        while (tok.hasMoreTokens()) {
            String standard = tok.nextToken();
            String value = tok.nextToken();
            String[] pair = new String[] { standard, value };
            phy.add(pair);
        }
        this.physical = (String[][]) phy.toArray(new String[0][0]);
        this.tuner = tuner;
    }

    public ChannelDigital(String scanOutput, int targetProgram, Tuner tuner, double priority) {
        /*
         * SCANNING: 521000000 (us-bcast:22) LOCK: 8vsb (ss=66 snq=56 seq=100)
         * PROGRAM: 1: 36.1 WCNC-HD PROGRAM: 2: 36.2 WCNC-WX
         * 
         * SCANNING: 699000000 (us-cable:108, us-irc:108) LOCK: qam256 (ss=100
         * snq=90 seq=100) PROGRAM: 1: 0.0 PROGRAM: 2: 0.0
         */

        StringTokenizer tok = new StringTokenizer(scanOutput);
        int programCount = 0;
        while (tok.hasMoreTokens()) {
            String word = tok.nextToken();
            if (word.equals("SCANNING:")) {
                this.megahertz = tok.nextToken();
                this.physical = parsePhysical(tok.nextToken());
                this.frequency = getFirstRf();
            } else if (word.equals("LOCK:")) {
                this.protocol = tok.nextToken();
            } else if (word.equals("PROGRAM:") || word.equals("PROGRAM")) {
                if (programCount == targetProgram) {
                    this.pid = tok.nextToken();
                    if (this.pid.indexOf(":") > 0) {
                        this.pid = this.pid.substring(0, this.pid.indexOf(":"));
                    }
                    this.channelKey = physical[0][Channel.TUNER_VALUE] + "." + this.pid + ":1-" + this.protocol;
                    this.channelDescription = this.channelKey;
                    if (tok.hasMoreTokens()) {
                        this.channelDescription = tok.nextToken("\n").trim(); // take
                        // the
                        // rest
                        // of
                        // the
                        // line
                        int dotLoc = this.channelDescription.indexOf(".");
                        if (dotLoc > 0) {
                            this.channelVirtual = this.channelDescription.substring(0, dotLoc);
                            try {
                                this.virtualChannel = Integer.parseInt(this.channelVirtual);
                            } catch (Exception e) {/* ignore */
                            }
                            String subchannelVirtual = this.channelDescription.substring(dotLoc + 1);
                            int blankLoc = subchannelVirtual.indexOf(" ");
                            if (blankLoc > 0 && (blankLoc + 1) < subchannelVirtual.length()) {
                                try {
                                    this.virtualSubchannel = Integer.parseInt(subchannelVirtual.substring(0, blankLoc));
                                } catch (Exception e) {/* ignore */
                                }
                            }
                        }
                        int blankLoc = this.channelDescription.indexOf(" ");
                        if (blankLoc > 1 && (blankLoc + 1) < this.channelDescription.length()) {
                            this.alphaDescription = this.channelDescription.substring(blankLoc + 1);
                        } else {
                            this.alphaDescription = "";
                        }
                    }
                }
                programCount++;
            } else if (word.indexOf("ss=") > -1) {
                int loc = word.indexOf("ss=") + 3;
                this.ss = word.substring(loc).trim();
            } else if (word.indexOf("snq=") > -1) {
                int loc = word.indexOf("snq=") + 4;
                this.snq = word.substring(loc).trim();
            } else if (word.indexOf("seq=") > -1) {
                int loc = word.indexOf("seq=") + 4;
                word = word.replace(')', ' ');
                this.seq = word.substring(loc).trim();
            }
        }
        this.priority = priority;
        this.tuner = tuner;
    }

    /*
     FOR original file:
  <Program>
    <Name>WJXTDT</Name>
    <Enabled>false</Enabled>
    <Modulation>qam256</Modulation>
    <Frequency>753000000</Frequency>
    <TransportStreamID>33418</TransportStreamID>
    <ProgramNumber>2</ProgramNumber>
    <GuideNumber>437</GuideNumber>
    <UserModified>2009-09-23 23:27:30</UserModified>
  </Program>

    FOR CableCARD file:
  <Program>
    <Name>TBS</Name>
    <Enabled>true</Enabled>
    <GuideNumber>17</GuideNumber>
  </Program>
    
     */
    /* Constructor for creation from hdhr xml file */
    public ChannelDigital(String hdhrXml, String airCatSource, String xmlFileName, Tuner tuner, double priority) {
        //System.out.println(new Date() + " DEBUG CHANNEL CREATION xmlFileName: " + xmlFileName + " hdhrXml: " + hdhrXml);
        this.airCat = "Cat";
        if(airCatSource != null && !airCatSource.equals("")){ 
            if(airCatSource.equals("Digital Antenna")) this.airCat = "Air";
        } else {
            if (xmlFileName != null && xmlFileName.indexOf("Antenna") > -1) this.airCat = "Air";
        }
        this.tuner = tuner;
        this.priority = priority;
        this.protocol = getFromXml(hdhrXml, "Modulation");
        this.channelVirtual = getFromXml(hdhrXml, "GuideNumber");

        this.alphaDescription = getFromXml(hdhrXml, "Name");
        if (this.alphaDescription == null) {
            this.alphaDescription = getFromXml(hdhrXml, "GuideName");
            virtualHandlingRequired = true;
            this.isVirtualCable = this.channelVirtual != null && !this.channelVirtual.contains(".");
        } 
        this.megahertz = getFromXml(hdhrXml, "Frequency");
        this.pid = getFromXml(hdhrXml, "ProgramNumber");
        if (this.alphaDescription == null) this.alphaDescription = getFromXml(hdhrXml, "TransportStreamID") + "-" + this.pid;
        //this.channelVirtual = getFromXml(hdhrXml, "GuideNumber");  // DRS 20190910 - moved up
        if (this.channelVirtual == null && this.airCat.equals("Air")) this.channelVirtual = "0.0"; //Digital Antenna has #.#
        if (this.channelVirtual == null && this.airCat.equals("Cat")) this.channelVirtual = "0";   //Digital Cable just has #
        this.channelDescription = this.channelVirtual + " " + this.alphaDescription;
        int dotLoc = this.channelVirtual.indexOf(".");
        if (dotLoc > 0 && dotLoc < this.channelVirtual.length()) {
            this.virtualChannel = Integer.parseInt(this.channelVirtual.substring(0, dotLoc));
            try { this.virtualSubchannel = Integer.parseInt(this.channelVirtual.substring(dotLoc + 1)); } catch (Throwable t) {this.virtualSubchannel = 0;}
        }

        if (virtualHandlingRequired) {
//            if (isVirtualCable) {
//                String appendString = ".0:1-qam256";  // I suspect that this should use the stated protocol (which is "auto") but let's leave it alone
//                if (this.channelVirtual.contains(".")) appendString = ":1-qam256";  // N.B.:  IIRC, none of the cable GuideNumbers contain "."
//                this.channelKey = this.channelVirtual + appendString;
//                this.protocol = "qam256";
//            } else {
//                String appendString = ".0:1-8vsb"; // DRS 20181128 - ...-vchn  // Again, why does this not use the stated protocol?
//                if (this.channelVirtual.contains(".")) appendString = ":1-8vsb"; // DRS 20181128 - ...-vchn
//                this.channelKey = this.channelVirtual + appendString;
                //this.frequency = this.channelVirtual;
                //if (!channelVirtual.contains(".")) this.channelVirtual = this.channelVirtual + ".0";
                //this.pid = "0";
//                this.protocol = "8vsb";
//            }
            try {
              this.frequency = String.valueOf(this.virtualChannel);
              this.pid = String.valueOf(this.virtualSubchannel);
                
            } catch (Exception e) {
                System.out.println(new Date() + " ERROR: unable to get 'RF frequency' for " + this.channelVirtual + " -- " + this.alphaDescription);
                e.printStackTrace();
            } 
        } else {
            // DRS 20181025 traditional existing code here
//            this.protocol = getFromXml(hdhrXml, "Modulation");  // TMP 20240206 moved up
            if (this.protocol == null) {
                //System.out.println(new Date() + " Modulation tag missing.  Assuming 8vsb."); // Terry 11/25/2018
                this.protocol = "auto"; // "8vsb";  // TMP 20240206 "auto" should work for any tuner and input (but it should be in the XML file!)
            }
            this.frequency = getRfFromMegahertz(this.megahertz, this.protocol);
//            this.channelKey = this.frequency + "." + this.pid + ":1-" + this.protocol;  // TMP 20240206 I think that this could be used for all the above definitions!
        }
        this.channelKey = this.frequency + "." + this.pid + ":1-" + this.protocol;  // TMP 20240206 I think that this can be used for all the above definitions!
        this.physical = new String[1][2];
        this.physical[0][TUNER_STANDARD] = this.protocol;
        this.physical[0][TUNER_VALUE] = "" + this.frequency;

//        int dotLoc = this.channelVirtual.indexOf(".");  // TMP 20240206 moved up
//        if (dotLoc > 0 && dotLoc < this.channelVirtual.length()) {
//            this.virtualChannel = Integer.parseInt(this.channelVirtual.substring(0, dotLoc));
//            try { this.virtualSubchannel = Integer.parseInt(this.channelVirtual.substring(dotLoc + 1)); } catch (Throwable t) {this.virtualSubchannel = 0;}
//        }
    }


	private String getRfFromMegahertz(String megahertz, String protocol) {
        if (megahertz == null || protocol == null) {
            System.out.println(new Date() + " Unable to get RF from megahertz/protocol [" + megahertz + "/" + protocol + "]");
            return null;
        }
        double freq = Double.parseDouble(megahertz) / 1000000D;
        double rfChannel = -1;
        if ("8vsb".equalsIgnoreCase(protocol)) {
            if (freq >= 473)
                rfChannel = 14D + ((freq - 473D) / 6D);
            else if (freq >= 177)
                rfChannel = 7D + ((freq - 177D) / 6D);
            else if (freq >= 79)
                rfChannel = 5D + ((freq - 79D) / 6D);
            else if (freq >= 57)
                rfChannel = 2D + ((freq - 57D) / 6D);
        } else if ("qam256".equalsIgnoreCase(protocol) || "qam64".equalsIgnoreCase(protocol)) {
            if (freq >= 651)
                rfChannel = 100D + ((freq - 651D) / 6D);
            else if (freq >= 219)
                rfChannel = 23D + ((freq - 219D) / 6D);
            else if (freq >= 177)
                rfChannel = 7D + ((freq - 177D) / 6D);
            else if (freq >= 123)
                rfChannel = 14D + ((freq - 123D) / 6D);
            else if (freq >= 93)
                rfChannel = 95 + ((freq - 93D) / 6D);
            else if (freq >= 79)
                rfChannel = 5D + ((freq - 79D) / 6D);
            else if (freq >= 57)
                rfChannel = 2D + ((freq - 57D) / 6D);
        } else {
            System.out.println(new Date() + " ERROR: Unrecognized protocol: " + protocol);
        }
        return "" + (int) rfChannel;
    }

    private String getFromXml(String xml, String key) {
        if (xml == null || xml.trim().equals("") || key == null || key.trim().equals(""))
            return null;
        int startLoc = xml.indexOf("<" + key + ">") + key.length() + 2;
        int endLoc = xml.indexOf("</" + key + ">");
        if (startLoc > -1 && endLoc > -1 && endLoc > startLoc) {
            return xml.substring(startLoc, endLoc);
        } else {
            return null;
        }
    }

    public String getTunerName() {
        if (tuner != null) {
            return tuner.getFullName();
        }
        return "(no tuner defined)";
    }

    public String getChannelKey() {
        return channelKey;
    }

    public int getPid() throws Throwable {
        return Integer.parseInt(pid);
    }

    private String[][] parsePhysical(String scanPhysical) {
        /* (us-bcast:11, us-cable:11, us-irc:11) */
        ArrayList<String[]> list = new ArrayList<String[]>();
        scanPhysical = scanPhysical.replace("(", "").replace(")", "");
        StringTokenizer tok = new StringTokenizer(scanPhysical, ",");
        while (tok.hasMoreTokens()) {
            String entry = tok.nextToken();
            if (entry.indexOf(":") > 1 && !entry.endsWith(":")) {
                String[] physicalEntry = { entry.substring(0, entry.indexOf(":")), entry.substring(entry.indexOf(":") + 1) };
                list.add(physicalEntry);
            }
        }
        return (String[][]) list.toArray(new String[0][0]);
    }

    /*
     * hashCode, equals, compareTo, toString
     * 
     */
    public int hashCode() {
        return this.channelKey.hashCode();
    }

    public boolean equals(Object otherChannel) {
        if (otherChannel instanceof ChannelDigital) {
            // System.out.println(this.channelName +
            // this.getFullVirtualChannel() + tuner.id);
            // System.out.println(((Channel)otherChannel).channelName +
            // ((Channel)otherChannel).getFullVirtualChannel() +
            // ((Channel)otherChannel).tuner.id);
            return this.channelKey.equals(((Channel) otherChannel).channelKey)
                    && this.getFullVirtualChannel(virtualHandlingRequired).equals(((Channel) otherChannel).getFullVirtualChannel(virtualHandlingRequired))
                    && this.tuner.id.equals(((Channel) otherChannel).tuner.id);
        }
        return false;
    }

    public int compareTo(Object otherChannel) {
        if (otherChannel instanceof ChannelDigital) {
            Channel oc = (Channel) otherChannel;
            return (oc.channelKey + oc.getFullVirtualChannel(virtualHandlingRequired)).compareTo(this.channelKey + this.getFullVirtualChannel(virtualHandlingRequired));
        }
        return -1;
    }

    public String getPersistanceData() {
        StringBuffer buf = new StringBuffer();
        buf.append(this.channelKey + "|" + this.pid + "|" + this.frequency + "|" + this.protocol + "|" + this.priority + "|"
                + this.channelDescription + "|");
        buf.append(this.alphaDescription + "|" + this.channelVirtual + "|" + this.megahertz + "|" + this.ss + "|" + this.seq
                + "|" + this.snq + "|" + this.input + "|" + this.virtualChannel + "|" + this.virtualSubchannel + "|");
        for (int i = 0; physical != null && i < physical.length; i++) {
            buf.append(this.physical[i][TUNER_STANDARD] + "|" + this.physical[i][TUNER_VALUE] + "|");
        }
        return new String(buf);
    }

    public static void main(String[] args) throws Throwable {
        boolean testingVchannel = false;
        if (testingVchannel) {
            StringBuffer buf = new StringBuffer();
            buf.append("<Name>QVC</Name>");
            buf.append("<GuideNumber>1010</GuideNumber>");
            Channel channel = new ChannelDigital(buf.toString(), "Digital Antenna", "Digital Antenna", new TunerHdhr("10119D6F-1", true, TunerHdhr.VCHANNEL),  1000);
            LineUpHdhr aLineup = new LineUpHdhr();
            aLineup.addChannel(channel);

            System.out.println("channel key: " + channel.channelKey);
            System.out.println(channel.getXml());
            System.out.println("toString:\n" + channel);
            Map<String, Channel> aMap = aLineup.channels;
            for (String key : aMap.keySet()) {
                System.out.println("Channel key: " + key + " 0Channel: " + aMap.get(key).getXml());
            }
        }
        
        boolean testingHdhrXml = true;
        if (testingHdhrXml) {
            TunerHdhr tuner =  new TunerHdhr("10119D6F-1", true, TunerHdhr.ORIGINAL);
            StringBuffer buf = new StringBuffer();
            buf.append("<Name>WCNC-HD</Name>");
            buf.append("<Enabled>true</Enabled>");
            //buf.append("<Modulation>8vsb</Modulation>");
            buf.append("<Frequency>521000000</Frequency>");
            buf.append("<TransportStreamID>1801</TransportStreamID>");
            buf.append("<ProgramNumber>1</ProgramNumber>");
            buf.append("<GuideNumber>36.1</GuideNumber>");
            Channel channel = new ChannelDigital(buf.toString(), "Digital Antenna", "Digital Antenna", tuner,  1000);

            LineUpHdhr aLineup = new LineUpHdhr();
            aLineup.addChannel(channel);
            
            
            System.out.println("channel key: " + channel.channelKey);
            System.out.println(channel.getXml());
            System.out.println("toString:\n" + channel);
            Map<String, Channel> aMap = aLineup.channels;
            for (String key : aMap.keySet()) {
                System.out.println("Channel key: " + key + " 1Channel: " + aMap.get(key).getXml());
            }
        }

        boolean testingHdhrXml2 = false;
        if (testingHdhrXml2) {
            StringBuffer buf = new StringBuffer();
            buf.append("<Name>WCNC-HD</Name>");
            buf.append("<GuideNumber>1111</GuideNumber>");
            Channel channel = new ChannelDigital(buf.toString(), "Digital Antenna", "Digital Antenna", new TunerHdhr("10119D6F-1", true, TunerHdhr.ORIGINAL),  1000);
            System.out.println("channel key: " + channel.channelKey);
            System.out.println(channel.getXml());
            System.out.println("toString:\n" + channel);
        }

        boolean testingPersistance = false;
        if (testingPersistance) {
            StringBuffer buf = new StringBuffer();
            buf.append("SCANNING: 521000000 (us-bcast:22)\n");
            buf.append("LOCK: 8vsb (ss=66 snq=56 seq=100)\n");
            buf.append("PROGRAM: 1: 36.1 WCNC-HD\n");
            buf.append("PROGRAM 2: 36.2 WCNC-WX\n");
            ChannelDigital channelDigital = new ChannelDigital(new String(buf), 1, null, 1);
            System.out.println(channelDigital);
            System.out.println(channelDigital.getPersistanceData());
            ChannelDigital bChannel = new ChannelDigital(channelDigital.getPersistanceData(), null);
            System.out.println(bChannel);
            System.out.println(bChannel.getPersistanceData());
        }

        boolean testingWebData = false;
        if (testingWebData) {
            StringBuffer buf = new StringBuffer();
            buf.append("SCANNING: 521000000 (us-bcast:22)\n");
            buf.append("LOCK: 8vsb (ss=66 snq=56 seq=100)\n");
            buf.append("PROGRAM: 1: 3.3 WCNC-HD\n"); // this is first one
            buf.append("PROGRAM 2: 3.4 WCNC-WX\n"); // this is second one
            int firstOne = 0;
            int secondOne = 1;
            Channel channelDigital = new ChannelDigital(new String(buf), firstOne, null, 1);
            System.out.println("full virtual:" + channelDigital.getFullVirtualChannel(channelDigital.virtualHandlingRequired));
            channelDigital = new ChannelDigital(new String(buf), secondOne, null, 1);
            System.out.println("full virtual:" + channelDigital.getFullVirtualChannel(channelDigital.virtualHandlingRequired));
        }

        boolean testingMyhd = false;
        if (testingMyhd) {
            // String virtual = null;
            // String virtual = "";
            // String virtual = "42";
            String virtual = "42.1:1";
            ChannelDigital channelDigital = new ChannelDigital("11.3", new TunerMyhd(true), virtual, "8vsb");
            System.out.println(channelDigital + "\n phys:" + channelDigital.getPhysicalChannel() + "\n  pid:"
                    + channelDigital.getPid() + "\n virt:" + channelDigital.getVirtualChannel() + "\n  sub:"
                    + channelDigital.getVirtualSubchannel() + "\ninput:" + channelDigital.getInput());
            // ChannelDigital [11.3 11.3:1 pid:3 11 (no prtcl) virt:42.1 MYHD-0
            // () 1.0]
            // ChannelDigital [11.3 11.3:1 pid:3 11 (no prtcl) virt:42 MYHD-0 ()
            // 1.0]
        }
    }

}
