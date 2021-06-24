/*
 * Created on Jun 2, 2008
 *
 */
package org.cwepg.svc;

import java.io.BufferedReader;
import java.io.FileReader;

import org.cwepg.hr.CaptureManager;


public class WebHelpDoc {

    public static String doc;
    
    static {
        StringBuffer buf = new StringBuffer();
        buf.append("<p><b>Unless you're a programmer, you probably don't need to be here. </b>");
        buf.append("If that statement only peaked your interest, well, maybe you are in the right place! ");
        buf.append("But you might just be on your own too because ");
        buf.append("this interface is here for CW-EPG, and you shouldn't need to do anything with it. ");
        buf.append("So although you can make CwHelper sing and dance using just your browser (or ");
        buf.append("anything that can handle http protocol), it's not something that's going to be well supported ");
        buf.append("in the end-user community.  I suggest that you just sit-back and let CW-EPG handle the details.");
        buf.append("Now just go off and watch some TV!</p><br>");
        buf.append("<table border=\"1\">\n");
        
        //From xls sheet:
        buf.append("<tr><td>Action</td><td>Name</td><td>Value</td><td>Description</td></tr>\n");
        buf.append("<tr><td>/set</td><td>alternatechannels</td><td>ie 58.3^42.1,18.2^64.3</td><td>To define pairs of channels that have identical programming for when the signal quality is poor and hdhrrecordmonitorseconds is configured.  Comma separated channel pairs with each pair separated by '^'. Format is '(channelName; i.e., RF.PID)'.  These are defined in the beginning of the log.  RESTART required to take effect! </td></tr>\n");
        buf.append("<tr><td>/set</td><td>clockoffset</td><td>ie -30</td><td>Seconds fast (+) or slow (-) to set the PC clock from an Internet timeserver when the program starts.  Negative means the clock will be slow, extending the end of a program and trimming the beginning.  The default is '0', and will cause the system NOT to attempt any PC clock manipulation.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>discoverdelay</td><td>ie 1000</td><td>Number of milliseconds between discover retries</td></tr>\n");
        buf.append("<tr><td>/set</td><td>discoverretries</td><td>ie 5</td><td>Number of times the hdhr discover command should be retried</td></tr>\n");
        buf.append("<tr><td>/set</td><td>endfusionwatchevents</td><td>ie true</td><td>Use to end Fusion watch events by putting a tiny capture at the end.  Default is false.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>fusionleadtime</td><td>ie 90</td><td>Number of seconds from 'now' to begin a restarted Fusion recording (default 120).  The closer you get to 'now' the less likely the Fusion recording will work.  Fusion ignores start times in the past.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>hdhrbadrecordingpercent</td><td>ie 0.5</td><td>The fraction of seconds in a recording that are deemed 'bad' by the tuner that will trigger the record monitor to spawn a backup recording.  If you wanted to trigger in the cases where there was one error per minute or more, you'd enter 1.67 (1/60 * 100%).  The default is 0.33.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>hdhrrecordmonitorseconds</td><td>ie 120</td><td>Setting this to a value will cause inspection of HDHR recordings every X seconds.  If the capture is low quality, an attempt will be made to start the capture again on another tuner.  The default is -1 (not active).  The minimum is 1 second, but that's crazy.  1 minute is plenty often.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>leadtime</td><td>ie 90</td><td>Number of seconds before the recording start time that you want your machine to start the wakeup process.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>myhdwakeup</td><td>ie true</td><td>Use to prevent this program from setting machine wake events for MyHD events.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>rerundiscover</td><td>ie true</td><td>Set to true if your HDHR devices are sometimes powered down and therefore disappear from your network.  Default is false.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>shortenexternalrecordingseconds</td><td>ie 15</td><td>Number of seconds early to stop recordings on external tuners (those that use CapDVHS.ini). Default: 15</td></tr>\n");
        buf.append("<tr><td>/set</td><td>simulate</td><td>ie true</td><td>Use if you do not have a HDHR installed</td></tr>\n");
        buf.append("<tr><td>/set</td><td>sleepmanaged</td><td>ie true</td><td>Use to keep the machine awake for recordings.  Default is false.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>trayicon</td><td>ie false</td><td>Set to false if you do not want the application to have a tray icon.  Default is true.</td></tr>\n");
        //buf.append("<tr><td>/set</td><td>recreateiconuponwakeup</td><td>ie true</td><td>Set to true if you want to delete and re-add the icon when machine resets an innacurate clock upon waking.  Default is true.</td></tr>\n");
        buf.append("<tr><td>/set</td><td>alltraditionalhdhr</td><td>ie true</td><td>Set to true you want to have all tuners treated as if they were tradional (not new model) tuners.  Default is false.</td></tr>\n");
        //buf.append("<tr><td>/set</td><td>pollintervalseconds</td><td>ie 30</td><td>The number of seconds between each check of the system clock. Default is 15.  Set to less than 1 to disable ClockChecker.</td></tr>\n");
        buf.append("<tr><td>/settunerpath</td><td>tuner</td><td>ie 1010CC54-0</td><td>You use whatever the discover command replies with.</td></tr>\n");
        buf.append("<tr><td>-</td><td>path</td><td>ie c:\\mypath\\</td><td>The default record path for an HDHR tuner (for disk space reporting on /tuners output).  The setting will remain through restarts.  Leaving off the path will remove the setting.</td></tr>\n");
        buf.append("<tr><td>/discover</td><td>-</td><td>-</td><td>Deletes all tuners, then recreates all tuners from the registry.  For HDHR it goes to the network, or uses information from an earlier discover.txt file.</td></tr>\n");
        buf.append("<tr><td>/tuners</td><td>-</td><td>-</td><td>Lists existing data about tuners.</td></tr>\n");
        buf.append("<tr><td>/scan</td><td>-</td><td>-</td><td>NOTE: Scan can take 5 or more minutes!</td></tr>\n");
        buf.append("<tr><td>-</td><td>signaltype</td><td>ie qam256 or 8vsb</td><td>(data not used in scan)</td></tr>\n");
        buf.append("<tr><td>-</td><td>timeout</td><td>ie 500</td><td>Maximum number of seconds to allow for the scan to run.</td></tr>\n");
        buf.append("<tr><td>-</td><td>tuner</td><td>ie 1010CC54-0</td><td>You use whatever the discover command replies with.</td></tr>\n");
        buf.append("<tr><td>-</td><td>interrupt</td><td>ie (anything)</td><td>Interrupt any/all scans going on at the moment.  Note that you will be left with a empty or parital channel list!  If present, will override anything else on this command.</td></tr>\n");
        buf.append("<tr><td>/channels</td><td>-</td><td>-</td><td>Lists channels known to this application.  Channels need NOT be known in order to schedule a recording (as long as the channels are known externally).</td></tr>\n");
        buf.append("<tr><td>/capture</td><td>channelname</td><td>ie 23.3</td><td>RF channel dot program number (for HDHR and Fusion captures).  If this parameter is specified as an asterisk with an HDHR tuner, multiple back-to-back recordings will be scheduled for every channel on the tuner.  The purpose of this is to test reception for people who have antennas connected to their HDHR units.</td></tr>\n");
        buf.append("<tr><td>-</td><td>channelVirtual</td><td>ie 3.1:1</td><td>Virtual channel number dot virtual subchannel colon tuner number (for MyHD captures)</td></tr>\n");
        buf.append("<tr><td>-</td><td>rfchannel</td><td>ie 23</td><td>RF channel (for MyHD captures -optionally specified when input has duplicate virtuals).</td></tr>\n");
        buf.append("<tr><td>-</td><td>datetime</td><td>ie 01/31/08 22:30</td><td>Start date/time in MM/DD HH:mm</td></tr>\n");
        buf.append("<tr><td>-</td><td>durationminutes</td><td>ie 60</td><td>Use this -or- datetimeend</td></tr>\n");
        buf.append("<tr><td>-</td><td>datetimeend</td><td>ie 01/31/08 23:30</td><td>End date/time in MM/DD HH:mm.  Use this or durationminutes.</td></tr>\n");
        buf.append("<tr><td>-</td><td>tuner</td><td>ie 1010CC54-0 or myhd</td><td>You use whatever the discover command replies with or \"myhd\".</td></tr>\n");
        buf.append("<tr><td>-</td><td>filename</td><td>ie c:\\mypath\\MyShow.tp or MyShow</td><td>For HDHR and MyHD, the name of the path and file name where your show will be saved. For Fusion, just the file name (no path, no extension  -- theFusion defaults will be used).  For all types, you must escape any ampersands.</td></tr>\n");
        buf.append("<tr><td>-</td><td>protocol</td><td>ie qam256, 8vsb</td><td>Needed if the channel does not appear in /channels output</td></tr>\n");
        buf.append("<tr><td>-</td><td>title</td><td>ie My Show Title</td><td>Anything you want here.  For your use.</td></tr>\n");
        buf.append("<tr><td>-</td><td>-</td><td>-</td><td>Channels are specified for HDHR and Fusion through 'channelname'.  MyHD uses 'channelVirtual' and optionally 'rfchannel'.  Optimally, use the /channels output to get data for the channelname / channelVirutal / rfchannel.</td></tr>\n");
        buf.append("<tr><td>-</td><td>-</td><td>-</td><td>Example capture URLs:</td></tr>\n");
        buf.append("<tr><td>-</td><td>-</td><td>-</td><td>http://127.0.0.1:8181/capture?channelname=11.3&protocol=8vsb&datetime=09/20/2009 11:06&durationMinutes=2&filename=c:\\mypath\\MyShow.tp&tuner=1013FADA-0&title=My HDHR Capture</td></tr>\n");
        buf.append("<tr><td>-</td><td>-</td><td>-</td><td>http://127.0.0.1:8181/capture?channelvirtual=42.1:1&rfchannel=11&protocol=8vsb&datetime=09/20/2009 11:01&durationMinutes=2&filename=c:\\mypath\\MyShow.tp&tuner=myhd&title=My MyHD Capture</td></tr>\n");
        buf.append("<tr><td>-</td><td>-</td><td>-</td><td>http://127.0.0.1:8181/capture?channelname=11.3&protocol=8vsb&datetime=09/20/2009 10:54&durationMinutes=2&filename=MyShow&tuner=Fusion.1&title=My Fusion Capture</td></tr>\n");
        buf.append("<tr><td>/captures</td><td>-</td><td>-</td><td>Get a list of scheduled captures.</td></tr>\n");
        buf.append("<tr><td>/decapture</td><td>sequence</td><td>ie 0</td><td>Remove a capture.  Sequence number from the /captures output.</td></tr>\n");
        buf.append("<tr><td>/decaptureall</td><td>-</td><td>-</td><td>Removes all captures, all tuners.</td></tr>\n");
        buf.append("<tr><td>/actives</td><td>-</td><td>-</td><td>Get a list of active captures.</td></tr>\n");
        buf.append("<tr><td>/recent</td><td>-</td><td>-</td><td>Get a list of recent 50 most recent captures matching (optional) criteria.</td></tr>\n");
        buf.append("<tr><td>-</td><td>title</td><td>ie NOVA</td><td>Get a list of recent captures with matching title (optional)</td></tr>\n");
        buf.append("<tr><td>-</td><td>channel</td><td>ie 44.5</td><td>Get a list of recent captures with matching channelKey (optional)</td></tr>\n");
        buf.append("<tr><td>-</td><td>filename</td><td>ie (anything in the filename you've configured)</td><td>Get a list of recent captures with matching targetFile (optional)</td></tr>\n");
        buf.append("<tr><td>/recentall</td><td>-</td><td>-</td><td>Same as /recent, but doesn't limit to the first 50.  Takes a long time to process if you have a lot!</td></tr>\n");
        buf.append("<tr><td>-</td><td>title</td><td>ie NOVA</td><td>Get a list of all captures with matching title (optional)</td></tr>\n");
        buf.append("<tr><td>-</td><td>channel</td><td>ie 44.5</td><td>Get a list of all captures with matching channelKey (optional)</td></tr>\n");
        buf.append("<tr><td>-</td><td>filename</td><td>ie (anything in the filename you've configured)</td><td>Get a list of all captures with matching targetFile (optional)</td></tr>\n");
        buf.append("<tr><td>/sortchannels</td><td>-</td><td>-</td><td>Sorts HDHR channels (only) by signal strength.  After using /capture with channelname=*, and getting sample recordings, executing this function will re-arrange cw_epg's channel_maps.txt by swapping pairs of same channels.  For instance, if 11.3 on HDHR tuner 0 was higher in the channel_maps.txt than 11.3 on HDHR tuner 1,  but the latter had a better signal, the two would be swapped.  And this continues for all mapped channels.</td></tr>\n");
        buf.append("<tr><td>/path</td><td>root</td><td>ie drives or c:\\somePath</td><td>Get a list of folders below the root specified.</td></tr>\n");
        buf.append("<tr><td>/shutdown</td><td>-</td><td>-</td><td>Stop active captures, if any, and stop the service.  The web interface stops too.</td></tr>\n");
        buf.append("<tr><td>/log</td><td>-</td><td>-</td><td>Displays current log file.</td></tr>\n");
        buf.append("<tr><td>/properties</td><td>-</td><td>-</td><td>Displays properties held in the CaptureManager.</td></tr>\n");
        buf.append("<tr><td>/dbcopy</td><td>-</td><td>-</td><td>Copy cw_epg.mdb from the source to the local machine</td></tr>\n");
        buf.append("<tr><td>-</td><td>source</td><td>ie 192.168.1.45</td><td>IP number or name of the machine with the share.</td></tr>\n");
        buf.append("<tr><td>-</td><td>share</td><td>-</td><td>Share name. Optional. Defaults to CW_EPG_DATA.</td></tr>\n");
        buf.append("<tr><td>-</td><td>timeout</td><td>-</td><td>Timeout in seconds.  Defaults to 20 seconds.</td></tr>\n");
        buf.append("<tr><td>-</td><td>lastresult</td><td>ie true</td><td>Optional, but if not specified, the command will return immediately (not waiting for the final result).  Also may be specified alone, after an earlier /dbcopy.</td></tr>\n");
        buf.append("<tr><td>/emailer</td><td>-</td><td>-</td><td>DEPRICATED (do not use).  Use CW-EPG emailer instead.  Sends email alerts for recent recordings, scheduled recordings, and disk capacity.</td></tr>\n");
        buf.append("<tr><td>-</td><td>hourtosend</td><td>ie 16</td><td>To send at 4:30 PM (defaults to 5PM)</td></tr>\n");
        buf.append("<tr><td>-</td><td>minutetosend</td><td>ie 30</td><td>To send at 4:30 PM (defaults to 5PM)</td></tr>\n");
        buf.append("<tr><td>-</td><td>smtpservername</td><td>ie smtp.mycompany.com</td><td>Get from your email client config.</td></tr>\n");
        buf.append("<tr><td>-</td><td>smtpserverport</td><td>ie 25</td><td>Get from your email client config.</td></tr>\n");
        buf.append("<tr><td>-</td><td>logonuser</td><td>ie joedokes</td><td>Get from your email client config.</td></tr>\n");
        buf.append("<tr><td>-</td><td>logonpassword</td><td>ie se#creT</td><td>Get from your email client config.</td></tr>\n");
        buf.append("<tr><td>-</td><td>savetodisk</td><td>ie true</td><td>This will save all of these parameters to disk (including your password).  If this is true, the emailer will become active upon cwhelper restarts, otherwise emails will not be generated after a restart without running this command again.</td></tr>\n");
        buf.append("<tr><td>-</td><td>sendusers</td><td>ie joedokes@myisp.com;janedocs@myisp.com</td><td>Semicolon separated list of people to send to.</td></tr>\n");
        buf.append("<tr><td>-</td><td>lowdiskpercent</td><td>ie 5</td><td>(optional) Will send report if less than 5% remains on default recording paths.</td></tr>\n");
        buf.append("<tr><td>-</td><td>sendscheduled</td><td>ie true</td><td>(optional) Will send items scheduled.</td></tr>\n");
        buf.append("<tr><td>-</td><td>sendrecorded</td><td>ie true</td><td>(optional) Will send items recorded in the last 24 hours.</td></tr>\n");
        buf.append("<tr><td>/emailer</td><td>sendtestemail</td><td>ie true</td><td>Will send a test email to a previously configured emailer.</td></tr>\n");
        buf.append("<tr><td>/emailer</td><td>removeemailer</td><td>ie true</td><td>Will remove the active emailer and remove the persistence file, if it exists.</td></tr>\n");
        buf.append("<tr><td>/myhdpass</td><td>command</td><td>get</td><td>Will get a list of MyHD 'pass' items from the registry.</td></tr>\n");
        buf.append("<tr><td>/myhdpass</td><td>command</td><td>removeall</td><td>Will remove all MyHD 'pass' items from the registry.</td></tr>\n");
        buf.append("<tr><td>/wakeupevent</td><td>-</td><td>-</td><td>Wakes up a machine at a specified time and keeps it awake for duration minutes.  Optionally runs cwepg with parameters.</td></tr>\n");
        buf.append("<tr><td>-</td><td>hourtosend</td><td>ie 16</td><td>To send at 4:30 PM (defaults to 5PM)</td></tr>\n");
        buf.append("<tr><td>-</td><td>minutetosend</td><td>ie 30</td><td>To send at 4:30 PM (defaults to 5PM)</td></tr>\n");
        buf.append("<tr><td>-</td><td>durationminutes</td><td>ie 10</td><td>Amount of time a non-parameter (helper) will be prevented from sleeping.</td></tr>\n");
        buf.append("<tr><td>-</td><td>parameters</td><td>-</td><td>Parameters to put on the cwepg command line after 'cwepg.exe'.</td></tr>\n");
        buf.append("<tr><td>/vcr</td><td>-</td><td>-</td><td>Go to a page which allows the manual creation of captures.</td></tr>\n");
        buf.append("<tr><td>/clock</td><td>-</td><td>-</td><td>Get a report of how far off the system clock is from us.pool.ntp.org.</td></tr>\n");
        buf.append("<tr><td>-</td><td>clockoffset</td><td>ie 0</td><td>Request that the PC clock be set to clockoffset seconds from the ntp server.  Use zero to align the PC clock with ntp.  To set the clock slower than actual, use negative seconds.</td></tr>\n");
        buf.append("<tr><td>/help</td><td>-</td><td>-</td><td>This page.</td></tr>\n");
        // End from xls sheet
        
        buf.append("</table>\n");
        

        
        doc = new String(buf);
    }

    public static String getLog() {
        StringBuffer buf = new StringBuffer("<pre>");
        try {
            BufferedReader in = new BufferedReader(new FileReader(CaptureManager.dataPath + "\\logs\\stdout.txt"));
            String l = null;
            while((l = in.readLine())!= null){
                buf.append(l + "\n");
            }
            buf.append("</pre>");
            in.close();
        } catch (Throwable t){
            buf.append("Error reading log file " + t.getMessage());
        }
        return new String(buf);
    }
}
