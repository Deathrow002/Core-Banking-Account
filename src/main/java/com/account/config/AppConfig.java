package com.account.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.account.config.security.SessionForwardingInterceptor;

@Configuration
public class AppConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Forward the caller's SESSION cookie and CSRF token so downstream services resolve the same user.
        restTemplate.getInterceptors().add(new SessionForwardingInterceptor());
        return restTemplate;
    }
}