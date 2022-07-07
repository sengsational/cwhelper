/*
 * Created on May 22, 2022
 *
 */
package org.cwepg.svc;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.cwepg.hr.TunerManager;

public class HttpRequester {
    
    private static String lastError;

    public static String getLastError() {
        return lastError;
    }

    static String performPost(String url, String userName, String password, boolean quiet) {
        List<NameValuePair> paramList = new ArrayList<NameValuePair>();
        paramList.add(new BasicNameValuePair("username", userName));
        paramList.add(new BasicNameValuePair("password", getSha1(password, quiet))); 

        HttpClient httpclient = new DefaultHttpClient();
        String responseBody = "(response uninitialized)";
        lastError = "";
        try {
            HttpPost httppost = new HttpPost(url);
            httppost.setEntity(new UrlEncodedFormEntity(paramList));
            if (!quiet) System.out.println(new Date() + " Executing request " + httppost.getURI());
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            responseBody = httpclient.execute(httppost, responseHandler);
            if (!quiet) System.out.println("finished executing request with " + responseBody.length() + " characters received.");
            if (responseBody != null && responseBody.contains("Bad Request")) throw new Exception("Get page responded with 'Bad Request'");
            return responseBody;
        } catch (Exception e) {
            lastError = new Date() + " ERROR: Post failed for " + url + " " + e.getClass().getName() + " " + e.getMessage(); 
            System.out.println(lastError);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return responseBody;
        
    }
    
    private static String getSha1(String password, boolean quiet) {
        String sha1Hash = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(password.getBytes("utf8"));
            sha1Hash = String.format("%040x", new BigInteger(1, digest.digest()));
        } catch (Exception e){
            System.out.println(new Date() + " ERROR: Unable to create hash from password. " + e.getClass().getName() + " " + e.getMessage());
        }
        System.out.println("password hash [" + sha1Hash + "]");
        return sha1Hash;
    }

    static String performPost(String url, boolean quiet) {
        HttpClient httpclient = new DefaultHttpClient();
        String responseBody = "(response uninitialized)";
        lastError = "";
        try {
            HttpPost httppost = new HttpPost(url);
            if (!quiet) System.out.println(new Date() + " Executing request " + httppost.getURI());
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            responseBody = httpclient.execute(httppost, responseHandler);
            if (!quiet) System.out.println("finished executing request with " + responseBody.length() + " characters received.");
            //if (!quiet && responseBody.length() < 100) System.out.println("[" + responseBody + "]");
            if (responseBody != null && responseBody.contains("Bad Request")) throw new Exception("Get page responded with 'Bad Request'");
            return responseBody;
            
        } catch (Exception e) {
            lastError = new Date() + " ERROR: Post failed for " + url + " " + e.getClass().getName() + " " + e.getMessage(); 
            System.out.println(lastError);
        } finally {
            httpclient.getConnectionManager().shutdown();
        }
        return responseBody;
    }

    public static void main(String[] args) {
        String responseBody = performPost("https://json.schedulesdirect.org/20141201/token", "sengsational", "Z5864qp", false);
        System.out.println("responseBody [" + responseBody + "]");
    }




}
