/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.notification;

import java.util.Arrays;

/**
 * @author Andrew Hong
 * Date: 6/25/19
 */
public class SpringEmailer implements Emailer {

    private String fromAddress;

    private JavaMailSenderImpl mailSender;

    public SpringEmailer(String username, String password, String host, String port) {
        this.fromAddress = fromAddress; /////!!!!!!!!!

        mailSender = new JavaMailSenderImpl();
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setHost(host);
        mailSender.setHost(port);
    }

    @Override
    public void send(String subject, String body, String... recipients) {
        sendEmail(subject, body, recipients);
    }

    @Override
    public void sendAsHtml(String subject, String body, String... recipients) {
        sendEmail(subject, body, recipients);
    }

    private void sendEmail(String subject, String body, String... recipients) {
        SimpleMailMessage message = new SimpleMailMessage(); 
        message.setFrom(fromAddress)
        message.setTo(Arrays.asList(recipients)); 
        message.setSubject(subject); 
        message.setText(body);

        emailService.sendEmail(message);
    }
}
