package org.cwepg.svc;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.cwepg.hr.CaptureManager;

public class TinyWebServer implements Runnable {

	public ServerSocket listen_socket;
	public boolean runFlag = true;
    Thread runningThread;
    public static final String TINY_END = "TinyWebServerEnd";
    public static final SimpleDateFormat SDF = new SimpleDateFormat("hh:mm:ss:SSS");
    public static final int debug = 0;
    
	public TinyWebServer(String port) {
		try {
			int servPort = Integer.parseInt(port);
			listen_socket = new ServerSocket(servPort);
		} catch (IOException e) {
            System.err.println(new Date() + " Error in TinyWebServer constructor " + e.getMessage());
            System.out.println(new Date() + " Error in TinyWebServer constructor " + e.getMessage());
		}
        if (listen_socket != null){
            runningThread = new Thread(this);
            if (debug > 2) System.out.println(SDF.format(new Date()) + " TinyWebServer Start.");
            runningThread.start();
        } else {
            runFlag = false;
        }
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

	public static void main(String[] argv) throws Exception {
		if (argv.length < 1) {
			System.out.println("usage: java TinyWebServer <port>");
			return;
		}
		TinyWebServer server = new TinyWebServer(argv[0]);

		// Run the server for 120 seconds
		Thread.sleep(120 * 1000); 

		// Set run flag to "false" and poke it so it knows to quit
		System.out.println("runFlag set to false"); 
		server.setRunning(false);
		PrintStream out = new PrintStream(new DataOutputStream(new Socket("127.0.0.1", Integer.parseInt(argv[0])).getOutputStream()));
		out.write("GET /noop".getBytes());out.close();

		// Give it a moment then make sure it's gone
		Thread.sleep(2000);
		System.out.println("is alive:" + server.runningThread.isAlive());
		System.out.println("end of main.");
		
	}
}
