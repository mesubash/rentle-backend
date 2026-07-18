package com.rentle.shared.notification;

import com.rentle.config.RentleProperties;
import com.rentle.shared.exception.RentleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real email delivery over SMTP. Active when {@code rentle.email=smtp}; the SMTP host/port/
 * credentials come from Spring's {@code spring.mail.*} (env-driven). Falls back to
 * {@link LoggingEmailService} in dev when unset.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rentle.email", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender, RentleProperties props) {
        this.mailSender = mailSender;
        this.from = props.mailFrom() != null ? props.mailFrom() : "no-reply@rentle.app";
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}", toEmail, e);
            throw new RentleException("Could not send email");
        }
    }
}
