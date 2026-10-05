package com.disaster.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private MessageChannel messageChannel;

    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketConfig config = new WebSocketConfig(jwtService);
        ChannelRegistration registration = mock(ChannelRegistration.class);
        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);

        config.configureClientInboundChannel(registration);
        verify(registration).interceptors(captor.capture());
        interceptor = captor.getValue();
    }

    private Message<?> createSubscribeMessage(String destination, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("Public topic subscription does not require authentication")
    void testPublicTopicSubscriptionAllowed() {
        Message<?> message = createSubscribeMessage("/topic/disasters", null);
        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Private org channel subscription without auth header throws exception")
    void testPrivateOrgChannelMissingAuthHeaderThrows() {
        Message<?> message = createSubscribeMessage("/topic/org/org-123", null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        assertTrue(ex.getMessage().contains("Authentication required"));
    }

    @Test
    @DisplayName("Private org channel subscription with invalid token throws exception")
    void testPrivateOrgChannelInvalidTokenThrows() {
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        Message<?> message = createSubscribeMessage("/topic/org/org-123", "Bearer bad-token");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        assertTrue(ex.getMessage().contains("Invalid or expired token"));
    }

    @Test
    @DisplayName("Private org channel subscription with mismatched org ID throws exception")
    void testPrivateOrgChannelMismatchedOrgIdThrows() {
        when(jwtService.isTokenValid("org2-token")).thenReturn(true);
        when(jwtService.extractOrgId("org2-token")).thenReturn("org-999");
        when(jwtService.extractRole("org2-token")).thenReturn("ORGANISATION");

        Message<?> message = createSubscribeMessage("/topic/org/org-123", "Bearer org2-token");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, messageChannel)
        );
        assertTrue(ex.getMessage().contains("Access denied to organisation channel: ID mismatch"));
    }

    @Test
    @DisplayName("Private org channel subscription with matching org ID succeeds")
    void testPrivateOrgChannelMatchingOrgIdAllowed() {
        when(jwtService.isTokenValid("org1-token")).thenReturn(true);
        when(jwtService.extractOrgId("org1-token")).thenReturn("org-123");
        when(jwtService.extractRole("org1-token")).thenReturn("ORGANISATION");

        Message<?> message = createSubscribeMessage("/topic/org/org-123", "Bearer org1-token");
        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Admin can subscribe to any private org channel")
    void testAdminCanSubscribeToAnyOrgChannel() {
        when(jwtService.isTokenValid("admin-token")).thenReturn(true);
        when(jwtService.extractRole("admin-token")).thenReturn("ADMIN");

        Message<?> message = createSubscribeMessage("/topic/org/org-123", "Bearer admin-token");
        Message<?> result = interceptor.preSend(message, messageChannel);
        assertNotNull(result);
    }
}

