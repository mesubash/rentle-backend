package com.rentle.shared.notification;

public interface EmailService {

    void send(String toEmail, String subject, String body);
}
