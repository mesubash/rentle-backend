package com.rentle.shared.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoggingEmailService implements EmailService {

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("[EMAIL -> {}] {} | {}", toEmail, subject, body);
    }
}
