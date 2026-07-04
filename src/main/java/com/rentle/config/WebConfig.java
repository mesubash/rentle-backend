package com.rentle.config;

import com.rentle.shared.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RentleProperties rentleProperties;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, RentleProperties rentleProperties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.rentleProperties = rentleProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serves files saved by LocalStorageService (dev storage backend)
        String dir = Paths.get(rentleProperties.localUploadDir()).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/files/**").addResourceLocations(dir);
    }
}
