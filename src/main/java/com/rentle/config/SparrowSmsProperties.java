package com.rentle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms.sparrow")
public record SparrowSmsProperties(
        String token,
        String from
) {}
