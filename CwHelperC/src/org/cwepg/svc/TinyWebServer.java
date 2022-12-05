package org.cwepg.svc;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.ServiceLauncher;

public class TinyWebServer implements Runnable {

	public ServerSocket listen_socket;
	private boolean runFlag = true;
	private String port = ServiceLauncher.WEB_SERVER_PORT;
    Thread runningThread;
    public static final String TINY_END = "TinyWebServerEnd";
    public static final SimpleDateFormat SDF = new SimpleDateFormat("hh:mm:ss:SSS");
    public static int debug = 0;
    public static TinyWebServer webServer;
    
    public static TinyWebServer getInstance(String port) {
        if (webServer == null) {
            webServer = new TinyWebServer(port);
        }
        return webServer;
    }
    
	private TinyWebServer(String port) {
	    this.port = port;
	}
	
	public boolean start() {
        try {
            int servPort = Integer.parseInt(port);
            listen_socket = new ServerSocket(servPort);
        } catch (IOException e) {
            System.err.println(new Date() + " Error in TinyWebServer constructor " + e.getMessage());
            System.out.println(new Date() + " Error in TinyWebServer constructor " + e.getMessage());
        }
        if (listen_socket != null){
            runningThread = new Thread(this, "Thread-TinyWebServer");
            if (debug > 2) System.out.println(SDF.format(new Date()) + " TinyWebServer Start.");
            runningThread.start();
        } else {
            runFlag = false;
        }
	    return runFlag;
	}

	public void run() {
        String message = "";
        int secondsDelay = 1;
		while (isRunning()) {
			try {
				Socket client_socket = listen_socket.accept(); // blocks
				if (debug > 5) System.out.println(SDF.format(new Date()) + " TinyWebServer accept on socket.");
				if (!isRunning()) break;
				new TinyConnection(client_socket);
                if (debug > 5) System.out.println(SDF.format(new Date()) + " TinyWebServer returned from new TinyConnection.");
			} catch (IOException e) {
                message = e.getMessage();
                System.err.println(new Date() + " Error in TinyWebServer.run " + message);
                System.out.println(new Date() + " Error in TinyWebServer.run " + message);
				e.printStackTrace();
                if (secondsDelay > 8) setRunning(false);
                try {Thread.sleep(secondsDelay * 1000);} catch (Exception x){}
                secondsDelay = secondsDelay*2;
			} catch (Throwable t){
                message = t.getMessage();
                System.err.println(new Date() + " Error in TinyWebServer.run " + message);
                System.out.println(new Date() + " Error in TinyWebServer.run " + message);
                t.printStackTrace();
                if (secondsDelay > 8) setRunning(false);
                try {Thread.sleep(secondsDelay * 1000);} catch (Exception x){}
                secondsDelay = secondsDelay*2;
            }
		}
        System.err.println(new Date() + " TinyWebServer ending. " + message);
        System.out.println(new Date() + " TinyWebServer ending. " + message);
        this.setRunning(false);
        CaptureManager.shutdown(TINY_END);
	}
	
	public synchronized boolean isRunning(){
		return runFlag;
	}
	
	public synchronized void setRunning(boolean running){
		this.runFlag = running;
	}
	
	public void pokeForShutdown() {
	    // This "pokes" the web server, so that it shuts down.
        Socket socket = null;
        PrintStream out = null;
        try {
            try {Thread.sleep(500);} catch (Exception e){}
            socket = new Socket("127.0.0.1", Integer.parseInt(ServiceLauncher.WEB_SERVER_PORT));
            out = new PrintStream(new DataOutputStream(socket.getOutputStream()));
            out.write("GET /noop".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { out.close(); } catch (Exception e) {}
            try { socket.close(); } catch (Exception e) {}
        }
	}

	public static void main(String[] argv) throws Exception {
		if (argv.length < 1) {
			System.out.println("usage: java TinyWebServer <port>");
			return;
		} else {
		    System.out.println("running TinyWebServer on [" + argv[0] + "]");
		}
		TinyWebServer server = new TinyWebServer(argv[0]);

		// Run the server for 120 seconds
		Thread.sleep(120 * 1000); 

		// Set run flag to "false" and poke it so it knows to quit
		System.out.println("runFlag set to false"); 
		server.setRunning(false);
		Socket socketThing = new Socket("127.0.0.1", Integer.parseInt(argv[0]));
		PrintStream out = new PrintStream(new DataOutputStream(socketThing.getOutputStream()));
		out.write("GET /noop".getBytes());out.close();

		// Give it a moment then make sure it's gone
		Thread.sleep(2000);
		System.out.println("is alive:" + server.runningThread.isAlive());
		System.out.println("end of main.");
		socketThing.close();
		out.close();
		
	}
}
