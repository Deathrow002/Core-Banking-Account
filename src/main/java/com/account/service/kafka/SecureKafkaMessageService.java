package com.account.service.kafka;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.account.config.kafka.SessionKafkaMessageConverter;

import lombok.RequiredArgsConstructor;

/**
 * Service to demonstrate session-secured Kafka message processing
 * This service shows how to use the custom SessionKafkaMessageConverter
 * to resolve the shared session referenced by Kafka message headers
 */
@Service
@RequiredArgsConstructor
public class SecureKafkaMessageService {
    
    private static final Logger log = LoggerFactory.getLogger(SecureKafkaMessageService.class);
    
    private final SessionKafkaMessageConverter sessionMessageConverter;
    
    /**
     * Process a message with session authentication validation
     * This method demonstrates how to use the session message converter
     * to extract authentication information from Kafka headers
     */
    public void processSecureMessage(org.apache.kafka.common.header.Headers kafkaHeaders, String messageContent) {
        log.info("Processing secure message with session validation");
        
        // Resolve session information from Kafka headers
        SessionKafkaMessageConverter.SessionInfo sessionInfo = sessionMessageConverter.extractSessionInfo(kafkaHeaders);
        
        if (!sessionInfo.isValid()) {
            log.error("Message processing failed - Invalid session: {}", sessionInfo.getError());
            throw new SecurityException("Invalid or missing session: " + sessionInfo.getError());
        }
        
        // Create security context for the authenticated user
        Map<String, Object> securityContext = sessionMessageConverter.createSecurityContext(sessionInfo);
        
        log.info("Message processing authorized for user: {} with role: {} for customer: {}", 
                sessionInfo.getUsername(), sessionInfo.getRole(), sessionInfo.getCustomerId());
        
        // Process the message with the security context
        processMessageWithSecurity(messageContent, securityContext, sessionInfo);
    }
    
    /**
     * Process the actual message content with security context
     */
    private void processMessageWithSecurity(String messageContent, Map<String, Object> securityContext, 
                                          SessionKafkaMessageConverter.SessionInfo sessionInfo) {
        
        log.info("Processing message content: {} with security context", messageContent);
        
        // Example security checks
        if (securityContext.get("authenticated").equals(true)) {
            String role = sessionInfo.getRole();
            String customerId = sessionInfo.getCustomerId();
            
            // Role-based processing
            switch (role) {
                case "ROLE_ADMIN" -> log.info("Admin user processing message - full access granted");
                case "ROLE_MANAGER" -> log.info("Manager user processing message - limited access granted");
                case "ROLE_CUSTOMER" -> {
                    log.info("Regular user processing message for customer: {}", customerId);
                    // Additional customer-specific validation could be added here
                }
                default -> log.warn("Unknown role: {} - processing with limited access", role);
            }
            
            // Simulate message processing
            log.info("Message processed successfully by user: {}", sessionInfo.getUsername());
        } else {
            throw new SecurityException("User not authenticated");
        }
    }
    
    /**
     * Validate that a customer has permission to access account data
     */
    public boolean validateCustomerAccess(String sessionCustomerId, String accountCustomerId) {
        if (sessionCustomerId == null || accountCustomerId == null) {
            return false;
        }
        
        boolean hasAccess = sessionCustomerId.equals(accountCustomerId);
        log.info("Customer access validation - Session Customer: {}, Account Customer: {}, Access: {}", 
                sessionCustomerId, accountCustomerId, hasAccess);
        
        return hasAccess;
    }
}
