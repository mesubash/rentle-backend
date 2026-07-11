package com.rentle.shared.notification;

import com.rentle.config.RentleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Dev delivery channel: mirrors OTP codes and email verification links to a
 * Discord webhook so they can be read during testing (there is no SMS or email
 * provider wired yet). No-op when no webhook is configured.
 */
@Slf4j
@Component
public class DiscordNotifier {

    private final RentleProperties props;
    private final RestClient restClient = RestClient.create();

    public DiscordNotifier(RentleProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        return props.discordWebhook() != null && !props.discordWebhook().isBlank();
    }

    @Async
    public void send(String content) {
        if (!isEnabled()) return;
        try {
            restClient.post()
                    .uri(props.discordWebhook())
                    .body(Map.of("content", content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Discord webhook delivery failed: {}", e.getMessage());
        }
    }
}
