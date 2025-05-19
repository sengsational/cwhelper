package org.cwepg.hr;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.mail.internet.InternetAddress;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

public class EmailerOauth extends Emailer {

    private static final String APPLICATION_NAME = "getemailcodes-457313"; /* DONE */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String[] scopes = {GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_SEND, GmailScopes.GMAIL_MODIFY};
    private static final List<String> SCOPES = Arrays.asList(scopes);
    private static final File DATA_STORE_DIR = new File(CaptureManager.dataPath + ".credentials/google-oauth");

    static EmailerOauth emailer;

    private EmailerOauth(){
        super();
    }

    public static Emailer getInstance(){
        if (emailer == null){
            emailer = new EmailerOauth();
        }
        return emailer;
    }

    private Credential getCredential() throws Exception {
    	InputStream clientSecretStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("client_secret_947814295630-3nc1b0vu7fvod4btogpqhuq7c21611ko.apps.googleusercontent.com.json");
    	System.out.println("clientSecretStream " + clientSecretStream);
    	InputStreamReader isr = new InputStreamReader(clientSecretStream);
    	
    	System.out.println("InputStream created: " + isr);
    	System.out.println("InputStream ready " + isr.ready());
    	
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, isr);
        
        //System.out.println("clientSecrets " + clientSecrets);
        
        if (clientSecrets == null) {
        	throw new Exception("unable to read client_secret from resource in jar file");
        }
    	
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR))
            .setAccessType("offline")
            .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(logonUser);
        
        long expiresInSeconds = credential.getExpiresInSeconds();
        System.out.println("Expires in seconds: " + expiresInSeconds);
        
        if (expiresInSeconds < 60) {
            boolean refreshResult = credential.refreshToken();
            System.out.println("refresh token result " + refreshResult);
            expiresInSeconds = credential.getExpiresInSeconds();
            System.out.println("Expires in seconds: " + expiresInSeconds);
        }
        
        return credential;
    }
    
    public boolean removeOauthCredentials() {
    	boolean ok = false;
        try {
        	File storedCredential = new File(DATA_STORE_DIR.getAbsolutePath() + "\\StoredCredential");
        	ok = storedCredential.delete();
            if (ok) System.out.println(new Date() + " Removed Emailer Oauth Credentials");
            else System.out.println(new Date() + " Unable to remove Emailer Oauth Credentials in file " + DATA_STORE_DIR + "\\StoredCredential");
        } catch (Exception e){
            System.out.println(new Date() + " Error removing Emailer Oauth Credentials " + e.getMessage());
        }
        return ok;
    }
    
    @Override
	public void sendTestMessage() {
        System.out.println(new Date() + " Sending TEST email to " + Emailer.sendUsers);
        try {
            Credential credential = getCredential();
    	    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    	    Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
    	        .setApplicationName(APPLICATION_NAME)
    	        .build();
    	    EmailIntegration integration = new EmailIntegration(service);
    	    String from = logonUser;
    	    String subject = "testing cwhelper";
    	    String bodyText = "The email test was successful.";
    	    
    	    
    	    Message message = integration.sendEmail(logonUser,  getToAddresses(), from,  subject,  bodyText);
    	    System.out.println("Message Sent.  Trying to trash the sent copy.");
    	    Thread.sleep(1000);
    	    System.out.println("Trashing sent message by ID.");
    	    integration.trashMessage(service, logonUser, message.getId());
            
        } catch (Exception e) {
        	System.out.println("Failed to send test message oauth " + e.getClass() + " " + e.getMessage());
        }
	}

    
	@Override
	public void send() {
        lastTriggerMs = new Date().getTime();
        try {Thread.sleep(500);} catch (Exception e){}
        System.out.println(new Date() + " Sending email to " + Emailer.sendUsers);
        TunerManager tm = TunerManager.getInstance();
        CaptureManager cm = CaptureManager.getInstance();
        try {
            Credential credential = getCredential();
    	    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    	    Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
    	        .setApplicationName(APPLICATION_NAME)
    	        .build();
    	    EmailIntegration integration = new EmailIntegration(service);
    	    String subject = "Daily CW_EPG News";
    	    
    	    // Build HTML content
            StringBuffer content = new StringBuffer();
            String recentWebCaptures = cm.getRecentWebCapturesList(24, false, null);
            String scheduledWebCaptures = tm.getWebCapturesList(false);
            if (Emailer.sendRecorded.toUpperCase().equals("TRUE") && recentWebCaptures.length() > 0) content.append("<h1>Recently Recorded</h1><br>" + recentWebCaptures + "<BR><BR>");
            if (Emailer.sendScheduled.toUpperCase().equals("TRUE") && scheduledWebCaptures.length() > 0) content.append("<h1>Scheduled for Recording</h1><br>" + scheduledWebCaptures + "<BR><BR>");
            boolean alertExists = tm.foundDiskFullAlertCondition(Emailer.lowDiskGb);
            if (!Emailer.lowDiskGb.equals("-1") && alertExists) content.append("<h1>Disk Status Alert</h1><br>" + tm.getWebTunerList());

            if (content.length() > 0){
            	Message message = integration.sendHtmlEmail(logonUser, getToAddresses(), logonUser, subject, new String(content));
        	    System.out.println("Message Sent.  Trying to trash the sent copy.");
        	    Thread.sleep(1000);
        	    System.out.println("Trashing sent message by ID.");
        	    integration.trashMessage(service, logonUser, message.getId());
            }
        } catch (Exception e) {
        	System.out.println("Failed to send test message oauth " + e.getClass() + " " + e.getMessage());
        }
        setWakeup("Emailer send()");
	}

	public void sendWarningEmail(String tunerName, String channelKey, String targetFile, String tsmiss, int nonDotCount, int strengthValue, boolean fileExists) {
        System.out.println(new Date() + " Sending quality problem email to " + Emailer.sendUsers);
        try {
            Credential credential = getCredential();
    	    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    	    Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
    	        .setApplicationName(APPLICATION_NAME)
    	        .build();
    	    EmailIntegration integration = new EmailIntegration(service);
    	    String from = logonUser;
    	    InternetAddress[] to = getToAddresses();
    	
    	    String subject = "(not defined)";
    	    String bodyText = "(not defined)";
            String tsMissMessage = "-1".equals(tsmiss)?"":" there were " + tsmiss + " misses, ";
            if (fileExists) {
                subject = "Possible Poor Quality Recording";
                bodyText = "The file " + targetFile + " might have too many dropouts.  The strength value was " + strengthValue + ", " + tsMissMessage + "and " + nonDotCount + " continuity errors.  This was on tuner " + tunerName + " and channel " + channelKey + ".";
            } else {
                subject = "Recording was not created";
                bodyText = "The file " + targetFile + " was not created.";
            }
    	    Message message = integration.sendEmail(logonUser,  to, from,  subject,  bodyText);
    	    System.out.println("Message Sent.  Trying to trash the sent copy.");
    	    Thread.sleep(1000);
    	    System.out.println("Trashing sent message by ID.");
    	    integration.trashMessage(service, logonUser, message.getId());
            
        } catch (Exception e) {
        	System.out.println("Failed to send test message oauth " + e.getClass() + " " + e.getMessage());
        }
	}
}
