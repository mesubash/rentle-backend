package com.rentle.shared.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "rentle.sms", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsService implements SmsService {

    private final DiscordNotifier discord;

    public LoggingSmsService(DiscordNotifier discord) {
        this.discord = discord;
    }

    @Override
    public void send(String toPhone, String message) {
        log.info("[SMS -> {}] {}", toPhone, message);
        discord.send("📱 **SMS → " + toPhone + "**\n" + message);
    }
}
