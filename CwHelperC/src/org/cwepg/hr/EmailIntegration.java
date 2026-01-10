package org.cwepg.hr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.Gmail.Users;
import com.google.api.services.gmail.Gmail.Users.Messages;
import com.google.api.services.gmail.Gmail.Users.Messages.Send;
import com.google.api.services.gmail.model.Message;

public class EmailIntegration {

	private final Gmail service;

	public EmailIntegration(Gmail service) {
		this.service = service;
	}

	// Method to create and send an email directly without using JavaMail api
	public Message sendEmail(String userId, InternetAddress[] toAddresses, String from, String subject, String bodyText)
			throws Exception {
		String emailBody = createRawEmailString(toAddresses, from, subject, bodyText);
		byte[] emailBytes = emailBody.getBytes("UTF-8");
		String encodedEmail = Base64.encodeBase64URLSafeString(emailBytes);

		Message message = new Message();
		message.setRaw(encodedEmail);
		Users users = service.users();
		Messages messages = users.messages();
		Send sendResult = messages.send(userId, message);
		Message messageBack = sendResult.execute();
		System.out.println("Sent email with ID: " + messageBack.getId());
		return messageBack;
	}

	// Helper method to create a raw MIME string for an email
	private String createRawEmailString(InternetAddress[] toAddresses, String from, String subject, String bodyText) {
		List<String> headers = new ArrayList<>();
		for (InternetAddress internetAddress : toAddresses) {
			headers.add("To: " + internetAddress.getAddress());
		}
		headers.add("From: " + from);
		headers.add("Subject: " + subject);
		headers.add("Content-Type: text/plain; charset=\"UTF-8\"");
		headers.add("MIME-Version: 1.0");

		String headerString = String.join("\r\n", headers);
		return headerString + "\r\n\r\n" + bodyText;
	}

	public void trashMessage(Gmail service, String userId, String messageId) throws IOException {
		try {
			service.users().messages().trash(userId, messageId).execute();
			System.out.println("Message with ID: " + messageId + " moved to trash.");
		} catch (IOException e) {
			System.err.println("An error occurred: " + e);
		}
	}

	public Message sendHtmlEmail(String userId, InternetAddress[] recipientEmails, String fromEmail, String subject, String htmlBody) throws MessagingException, IOException {
		// Setup Mail Session
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		// Create a MimeMessage using the session created above
		MimeMessage email = new MimeMessage(session);
		email.setFrom(new InternetAddress(fromEmail));
		email.addRecipients(javax.mail.Message.RecipientType.TO, recipientEmails);
		email.setSubject(subject);
		email.setContent(htmlBody, "text/html; charset=utf-8");

		// Encode and wrap the MimeMessage into a Gmail Message
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		email.writeTo(buffer);
		byte[] bytes = buffer.toByteArray();
		String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
		Message message = new Message();
		message.setRaw(encodedEmail);

		// Send the email
		message = service.users().messages().send(userId, message).execute();

		System.out.println("HTML email sent successfully.");
		return message;
	}

}