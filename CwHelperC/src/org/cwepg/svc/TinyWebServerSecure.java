package org.cwepg.svc;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.cwepg.hr.CaptureManager;
import org.cwepg.hr.ServiceLauncher;

public class TinyWebServerSecure implements Runnable {
    
    //https://stackoverflow.com/questions/47068155/is-it-possible-to-use-java-serversocket-to-accept-https-requests
	
    public SSLServerSocket listen_socket;
	private boolean runFlag = true;
    private int port = ServiceLauncher.WEB_SERVER_SECURE_PORT;
    Thread runningThread;
    public static final String TINY_SECURE_END = "TinyWebServerSecureEnd";
    public static final SimpleDateFormat SDF = new SimpleDateFormat("hh:mm:ss:SSS");
    public static int debug = 0;
    public static TinyWebServerSecure webServerSecure;
    
    public static TinyWebServerSecure getInstance(int port) {
        if (webServerSecure == null) {
            webServerSecure = new TinyWebServerSecure(port);
        }
        return webServerSecure;
    }
    
	private TinyWebServerSecure(int port) {
	    this.port = port;
	}
	
	public boolean start() {
	    HttpsURLConnection.setDefaultHostnameVerifier(
            new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            }
        );
	    
        TrustManager[] trustAllCerts = new X509TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null,  trustAllCerts,  new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            char[] keyPassword = "4#r!DED".toCharArray();
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(this.getClass().getClassLoader().getResourceAsStream("CwHelper.p12"), keyPassword);
            
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keyPassword);      
            KeyManager keyManagers[] = keyManagerFactory.getKeyManagers();            
            
            Enumeration aliases = keystore.aliases();
            String keyAlias = "";
            while (aliases.hasMoreElements()) {
                keyAlias = (String) aliases.nextElement();
                System.out.println("KEY FOUND: " + keyAlias);
            }
            
            SSLContext sslContextTls = SSLContext.getInstance("TLS"); 
            sslContextTls.init(keyManagers, null, null);
            
            SSLServerSocketFactory sslContextFactory = (SSLServerSocketFactory) sslContextTls.getServerSocketFactory();
            listen_socket = (SSLServerSocket) sslContextFactory.createServerSocket(ServiceLauncher.WEB_SERVER_SECURE_PORT);     
            listen_socket.setEnabledProtocols(ServiceLauncher.PROTOCOLS);
            listen_socket.setEnabledCipherSuites(sslContextFactory.getSupportedCipherSuites());            
        } catch (IOException e) {
            System.err.println(new Date() + " Error in TinyWebServerSecure constructor " + e.getMessage());
            System.out.println(new Date() + " Error in TinyWebServerSecure constructor " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        if (listen_socket != null){
            runningThread = new Thread(this, "Thread-TinyWebServerSecure");
            if (debug > 2) System.out.println(SDF.format(new Date()) + " TinyWebServerSecure Start.");
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
				if (debug > 5) System.out.println(SDF.format(new Date()) + " TinyWebServerSecure accept on socket.");
				if (!isRunning()) break;
				new TinyConnection(client_socket);
                if (debug > 5) System.out.println(SDF.format(new Date()) + " TinyWebServerSecure returned from new TinyConnection.");
			} catch (IOException e) {
                message = e.getMessage();
                System.err.println(new Date() + " Error in TinyWebServerSecure.run " + message);
                System.out.println(new Date() + " Error in TinyWebServerSecure.run " + message);
				e.printStackTrace();
                if (secondsDelay > 8) setRunning(false);
                try {Thread.sleep(secondsDelay * 1000);} catch (Exception x){}
                secondsDelay = secondsDelay*2;
			} catch (Throwable t){
                message = t.getMessage();
                System.err.println(new Date() + " Error in TinyWebServerSecure.run " + message);
                System.out.println(new Date() + " Error in TinyWebServerSecure.run " + message);
                t.printStackTrace();
                if (secondsDelay > 8) setRunning(false);
                try {Thread.sleep(secondsDelay * 1000);} catch (Exception x){}
                secondsDelay = secondsDelay*2;
            }
		}
        System.err.println(new Date() + " TinyWebServerSecure ending. " + message);
        System.out.println(new Date() + " TinyWebServerSecure ending. " + message);
        this.setRunning(false);
        CaptureManager.shutdown(TINY_SECURE_END);
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
            socket = new Socket("127.0.0.1", this.port);
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
			System.out.println("usage: java TinyWebServerSecure <port>");
			return;
		} else {
		    System.out.println("running TinyWebServerSecure on [" + argv[0] + "]");
		}
		int argPort = Integer.parseInt(argv[0]);
		TinyWebServerSecure server = new TinyWebServerSecure(argPort);

		// Run the server for 120 seconds
		Thread.sleep(120 * 1000); 

		// Set run flag to "false" and poke it so it knows to quit
		System.out.println("runFlag set to false"); 
		server.setRunning(false);
		Socket socketThing = new Socket("127.0.0.1", argPort);
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
