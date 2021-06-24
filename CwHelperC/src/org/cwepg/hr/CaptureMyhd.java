/*
 * Created on Jul 25, 2009
 *
 */
package org.cwepg.hr;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.cwepg.reg.RegistryHelperMyhd;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;

public class CaptureMyhd extends Capture{
    
    int captureMode = -1;

    public CaptureMyhd(Slot slot, Channel channel){
        super(slot, channel);
    }

    public CaptureMyhd(String channelName, Slot slot, String tunerString, String channelVirtual, String rfChannel, String protocol) {
        try {
            this.slot = slot;
            Tuner tuner = TunerManager.getInstance().getTuner(tunerString.toUpperCase());
            if (tuner == null){
                tuner = new TunerMyhd(true); // automatically adds to TunerManager
            }
            Channel defaultChannel = null;
            if (channelName != null && !channelName.endsWith(".0")){
                defaultChannel = new ChannelDigital(channelName, tuner, channelVirtual, protocol);
            } else {
                int rf = 0;
                try {rf = Integer.parseInt(rfChannel);} catch (Exception e){/*ignore*/};
                defaultChannel = new ChannelAnalog(rfChannel, tuner, "analogCable", rf, 0 , "Cat", 1);
                //TODO:figure out how to tell if it's air or able analog, and if it's input 1 or 2
            }
            this.channel = tuner.getChannel(channelVirtual, rfChannel, defaultChannel.input, protocol);
            if (this.channel == null){
                this.channel = defaultChannel;
                System.out.println(new Date() + " WARNING: Virtual channel (virtchan.subch) [" + channelVirtual + "] was not in MyHD registry channel lineup.");
                if (channelName.equals("0.0")){
                    throw new Exception ("ChannelDigital name not specified.  Not enough information provided to define a channel or no matching channel found.");
                } else {
                    System.out.println(new Date() + " WARNING: Using channel name (rf.pid) [" + channelName + "] to construct an non-lineup channel.");
                }
            } 
        } catch (Exception e) {
            lastError = new Date() + " ERROR: CaptureMyhd<init> " + e.getMessage();
            System.out.println(lastError);
            System.err.println(lastError);
            e.printStackTrace();
        }
    }

    public void persist(boolean writeIt, boolean warn, Tuner tuner) throws CaptureScheduleException {
        //try {throw new Exception("CaptureMyHD.persist(writeIt=" + writeIt + "  warn=" + warn);} catch(Exception e){e.printStackTrace();}
        if (writeIt){
            String reservationName = RegistryHelperMyhd.getReservationNameForSlot(slot, false);
            if (reservationName != null){
                this.lastError = "Reservation " + reservationName + " already exists in the registry.";
                if (warn) System.out.println(new Date() + " " + this.lastError);
            } else {
                String openReservation = RegistryHelperMyhd.getFirstUnusedReservationName();
                try {
                    RegistryHelperMyhd.createReservation(this, openReservation);
                } catch (Exception e) {
                    this.lastError = "Could not create reservation for MyHD.  " + e.getMessage();
                    System.out.println(new Date() + " " + this.lastError);
                    System.err.println(new Date() + " " + this.lastError);
                    e.printStackTrace();
                }
            }
        }
        notifyExternalApplication();
        System.out.println(new Date() + " CaptureMyhd.persist(" + writeIt + ", " + warn + ")");
        //super.persistToHistoryTable(writeIt, true, tuner);
    }
    
    public boolean isValid() {
        boolean valid = true;
        boolean channelMustBeInLineup = true;
        if (!channel.tuner.available(this, channelMustBeInLineup, true)){
            valid = false; // no tuner available
            System.out.println(new Date() + " no tuner is available for " + this);
        }
        
        String reservation = null;
        try {
            reservation = RegistryHelperMyhd.getReservationNameForSlot(slot, false);
        } catch (CaptureScheduleException e) {
            System.out.println(new Date() + " Capture not valid " + this + " " + e.getMessage());
        }
        if (reservation != null){
            valid = false; // the slot is filled 
            System.out.println(new Date() + " no slot is available for " + this);
        }
        return valid;
    }

    public void setCaptureMode(int captureMode) {
        this.captureMode = captureMode;
    }

    public int getCaptureMode() {
        if (this.captureMode > 0) return this.captureMode;
        
        int captureMode = 2; // Digital Capture (default)
        if (this.target.isWatch()){
            captureMode = 0; // Watch
        } else if (this.channel instanceof ChannelAnalog){
            captureMode = 1; // Analog Capture
        }
        return captureMode;
    }

    public int getProtocol() {
        int protocolValue = 2;
        try {
            protocolValue = channel.getMyhdProtocolValue();
        } catch (Throwable e) {
            System.out.println(new Date() + " " + e.getMessage() + " defaulting to qam");
        }
        return protocolValue;
    }
    
    public static String notifyExternalApplication() {
        StringBuffer buf = new StringBuffer();
        try {
            User32.HWND broadcast = new User32.HWND();
            broadcast.setPointer(Pointer.createConstant(0xffff));
            User32.WPARAM ten = new User32.WPARAM(10L);
            User32.WPARAM fifteen = new User32.WPARAM(15L);
            User32.LPARAM zero = new User32.LPARAM(0L);

            int myIrcMsg = User32.INSTANCE.RegisterWindowMessage("MyIRC");
            //boolean ircResult = User32.INSTANCE.PostMessage(broadcast, myIrcMsg, ten, zero);
            User32.INSTANCE.PostMessage(broadcast, myIrcMsg, ten, zero);
            //buf.append(" ircResult was " + ircResult + "\n");
            
            //buf.append(" lastError: " + Native.getLastError() + "\n");

            int myHdMsg = User32.INSTANCE.RegisterWindowMessage("MyHD_App");
            //boolean hdResult = User32.INSTANCE.PostMessage(broadcast, myHdMsg, fifteen, zero);
            User32.INSTANCE.PostMessage(broadcast, myHdMsg, fifteen, zero);
            //buf.append(" hdResult was " + hdResult + "\n");

           // buf.append(" lastError: " + Native.getLastError() + "\n");

        } catch (RuntimeException e) {
            e.printStackTrace();
            buf.append(" " + e.getMessage());
        } 
        //System.out.println(new Date() + " DEBUG: " + buf.toString());
        return new String(buf);
    }

    /////////////// IMPLEMENTATIONS FOR ABSTRACT METHODS /////////////
    
    public String getSignalQualityData(){
        return ""; // nothing to do in MyHD land
    }
    @Override
    public int getNonDotCount() {
        return 0; // no data in this tuner type
    }

    
    public void interrupt(){
        removeWakeup();
        removeIcon();
    }
    
    @Override
    public int getTunerType() {
        return Tuner.MYHD_TYPE;
    }
    
    @Override
    public void setFileName(String fileName) throws Exception { 
        this.target.setFileName(fileName, null, null, null, this.getTunerType(), true, null);
    }

    public boolean addIcon(){
        return true; // nothing to do in MyHD land, but tell them we're happy
    }
    
    public boolean removeIcon(){
        return true; // nothing to do in MyHD land, but tell them we're happy
    }
    
    public void configureDevice(){
        // nothing to do in MyHD land
    }

    public void deConfigureDevice(){
        // nothing to do in MyHD land
    }
    
    public void run(){
        // nothing to do in MyHD land
    }

    public static CaptureMyhd getTestCapture() throws Exception {
        String dateTime = "03/24/2010 14:00"; 
        String durationMinutes = "60";
        String fileName = "C:\\TV\\D22-1(889)-090315-1100-Meet the Press.tp"; //G:\TV\D22-1(889)-090315-1100-Meet the Press.tp
        String title = "The Test From CaptureMyhd main method";
        Slot slot = new Slot(dateTime, durationMinutes);
        CaptureMyhd capture = new CaptureMyhd("11.3", slot, "myhd", "42.1:1","11", "8vsb");
        int randomHours = (int)Math.random() * 1000;
        capture.shift(randomHours);
        System.out.println("Test capture created with slot " + capture.slot);
        capture.setTarget(new Target(fileName, title, null, null, capture.getChannelProtocol(), Tuner.MYHD_TYPE));
        return capture;
    }

    public static CaptureMyhd getTestCapture(int frequency, int pid, int virt, int subch, int input) throws Exception {
        String dateTime = "03/24/2010 14:00"; 
        String durationMinutes = "60";
        String fileName = "C:\\TV\\TestCapture on " + frequency + "." + pid + ".tp"; //G:\TV\D22-1(889)-090315-1100-Meet the Press.tp
        String title = "The Test From CaptureMyhd for frequency.pid " + frequency + "." + pid;
        Slot slot = new Slot(dateTime, durationMinutes);
        int randomHours = (int)(Math.random() * 1000);
        slot.shift(randomHours);
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy kk:mm");
        System.out.println("channelname=" + frequency + "." + pid + "&channelvirtual=" + virt + "." + subch + ":" + input + "&tuner=myhd&protocol=qam&datetime=" + sdf.format(slot.start.getTime()) + "&durationminutes="+durationMinutes+"&filename="+fileName+"&title="+title);
        CaptureMyhd capture = new CaptureMyhd(frequency + "." + pid, slot, "myhd", virt + "." + subch + ":" + input, "" + frequency, "8vsb");
        System.out.println("Test capture created with slot " + capture.slot);
        capture.setTarget(new Target(fileName, title, null, null, "8vsb", Tuner.MYHD_TYPE));
        return capture;
    }

    
    // for testing
    public static void main(String[] args) throws Exception {
        
        //CaptureMyhd capture = getTestCapture();
        //CaptureMyhd capture = getTestCapture(0,0,42,2,1);
        CaptureMyhd capture = null;
        
        boolean testGetCapture = true;
        if (testGetCapture){
            capture = getTestCapture(0,0,42,2,1);
        }
        
        boolean testToStringValues = false;
        if (testToStringValues){
            System.out.println("channel output:\n" + capture.channel);
            System.out.println("\n\ncapture output:\n" + capture);
            System.out.println("\n\ntuner output:\n" + capture.channel.tuner);
        }

        boolean testCreationOfAReservation = false;
        if (testCreationOfAReservation){
            String reservationName = RegistryHelperMyhd.getReservationNameForSlot(capture.slot, true);  // returns null if there is a conflict
            if (reservationName == null){
                System.out.println("no conflict on time slot");
                System.out.println("try to get the first unused reservation from the registry");
                String openReservation = RegistryHelperMyhd.getFirstUnusedReservationName();
                System.out.println("found " + openReservation  + " in the registry");
                System.out.println("running createReservation");
                RegistryHelperMyhd.createReservation(capture, openReservation);
                System.out.println(capture);
            } else {
                System.out.println("conflict on time slot");
            } 
        }

        boolean testWritingToRegistryAndDatabase = false;
        if (testWritingToRegistryAndDatabase){
            capture.persist(true, true, new TunerMyhd("myhd", true));
        }
        
        boolean testRemoval = false;
        if (testRemoval){
            capture.channel.tuner.removeCapture(capture);
        }
    }
}
