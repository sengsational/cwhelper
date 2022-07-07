/*
 * Created on Jul 4, 2022
 *
 */
package org.cwepg.hr;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.CharBuffer;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.Future;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.client.methods.ZeroCopyConsumer;
import org.apache.http.nio.client.methods.ZeroCopyPost;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

public class HttpProcess implements Runnable {

    private static final long STARTUP_SECONDS = 4;
    private String ipAddress;
    private String channelKey;
    private int durationSeconds;
    private int tunerNumber;
    private String testingUrl;
    private String fileName;
    private boolean ended = false;
    private boolean endStatus = false;

    public HttpProcess(int tunerNumber, String ipAddress, String channelKey, int durationSeconds, String fileName) {
        this.tunerNumber = tunerNumber;
        this.ipAddress = ipAddress;
        this.channelKey = channelKey;
        this.durationSeconds = durationSeconds;
        this.fileName = fileName;
    }
    
    public HttpProcess(String testingUrl, String fileName) {
        this.testingUrl = testingUrl;
        this.fileName = fileName;
    }
    
    @Override
    public void run() {
        System.out.println(new Date() + " HttpProcess.run() starting.");
        CloseableHttpAsyncClient httpAsyncClient = null;
        boolean isPost = false;
        boolean quiet = false;
        String targetPage = "http://" + ipAddress + ":5004/tuner" + tunerNumber + "/" + channelKey + "?duration=" + durationSeconds;
        if (testingUrl != null) {
            targetPage = testingUrl;
            if (testingUrl.startsWith("get")) {
                isPost = false;
                targetPage = testingUrl.substring(3);
            } else if (testingUrl.startsWith("post")) {
                isPost = true;
                targetPage = testingUrl.substring(4);
            }
            durationSeconds = getDurationFromCommand(targetPage);
        }
        
        long startedAtMs = new Date().getTime();
        try {
            if (!isPost) {
                if (!quiet) System.out.println(new Date() + " Executing get request " + targetPage);
                System.out.println(new Date() + " FileName [" + fileName + "]");
                File download = new File(fileName);
                ZeroCopyConsumer<File> zcConsumer = new ZeroCopyConsumer<File>(download) {
                  @Override
                  protected File process(final HttpResponse response, final File file, final ContentType contentType) throws Exception {
                      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                          endStatus = false;
                          throw new ClientProtocolException("Failed: " + response.getStatusLine());
                      }
                      return file;
                  }
                }; 
                httpAsyncClient = HttpAsyncClients.createDefault();
                httpAsyncClient.start();
                System.out.println(new Date() + " HttpAsycClient started... executing get with zcConsumer with future.get().");
                Future<File> future = httpAsyncClient.execute(HttpAsyncMethods.createGet(targetPage), zcConsumer, null);
                File result = future.get(); // blocks
                endStatus = true;
                System.out.println(new Date() + " Response file length: " + result.length());
            } else {
                System.out.println(new Date() + " Post not implemented in HttpProcess.");
            }
            if (!quiet) System.out.println(new Date() + " Finished executing request.");
        } catch (Exception e) {
            System.out.println(new Date() + " Failed to get http page [" + targetPage + "] " + e.getClass().getName() + " " + e.getMessage());
            endStatus = false;
        } finally {
            ended = true;
            if (httpAsyncClient != null) {
                try {httpAsyncClient.close();} catch (Throwable t){System.out.println("WARNING: Unable to close http connection. " + t.getClass().getName() + " " + t.getMessage());}
            }
        }
        try {Thread.sleep(500);} catch (InterruptedException ee){}
        if ((((new Date().getTime() - startedAtMs)/1000) - STARTUP_SECONDS) > durationSeconds) {
            System.out.println(new Date() + " Timeout on page [" + targetPage + "] " + (new Date().getTime() - startedAtMs)/1000F + " seconds.");
            endStatus = true; // ??
        }
        System.out.println(new Date() + "  HttpProcess.run() ending.");
    }
    
    public boolean ended() {
        return ended;
    }

    public boolean endStatus() {
        return endStatus;
    }

    
    public static int getDurationFromCommand(String command) {
        int durationLoc = command.indexOf("?duration=");
        if (durationLoc > -1) {
            try {
                return Integer.parseInt(command.substring(durationLoc + 10));
            } catch (Throwable t) {
                System.out.println("Unable to parse duration seconds " + t.getClass().getName() + " " + t.getMessage());
            }
        }
        return -1;

    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Start the program with a parameter like this:\n'HttpProcess gethttp://<devIP>:5004/<auto|tunerN>/<vVC|chRF.PID>[?duration=seconds]' or \n'HttpProcess posthttp://<devIP>:5004/<auto|tunerN>/<vVC|chRF.PID>[?duration=seconds]'");
            System.exit(0);
        }
        
        boolean testWithConstructor = true;
        if (testWithConstructor) {
            int tunerNumber = 1;
            String ipAddress = "192.168.3.186";
            String channelKey = "v103.1";
            int durationSeconds = 20;
            String fileName = (Math.random() + "").substring(3)+".ts";
            
            HttpProcess proc = new HttpProcess(tunerNumber, ipAddress, channelKey, durationSeconds, fileName);
            Thread processThread = new Thread(proc);
            processThread.start(); // does not block
            System.out.println("HttpProcess.main() sleeping for " + (durationSeconds + 15) + " seconds.");
            try {Thread.sleep((durationSeconds + 15) * 1000);} catch (Throwable t) {System.out.println("Thread sleep interupted " + t.getMessage());}
        }
        

        boolean testWithUrlAsArgument = false;
        if (testWithUrlAsArgument) {
            String fileName = (Math.random() + "").substring(3)+".ts";
            HttpProcess proc = new HttpProcess(args[0], fileName);
            proc.run(); // blocks
        }
        
        System.out.println("HttpProcess.main() ending.");
    }


}
