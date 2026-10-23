package com.medical.agent.web.security;

import com.medical.agent.common.exception.BusinessException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;
    private final AccessGuard accessGuard;

    public WebSocketAuthChannelInterceptor(JwtTokenService jwtTokenService, AccessGuard accessGuard) {
        this.jwtTokenService = jwtTokenService;
        this.accessGuard = accessGuard;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            AuthenticatedUser user = authenticate(accessor);
            accessor.setUser(user);
            SecurityContextHolder.getContext().setAuthentication(buildAuthentication(user));
            return message;
        }

        AuthenticatedUser user = principal(accessor);
        if (user == null) {
            throw new BusinessException(401, "WebSocket 未认证");
        }
        SecurityContextHolder.getContext().setAuthentication(buildAuthentication(user));

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!accessGuard.canSubscribeDestination(accessor.getDestination())) {
                throw new BusinessException(403, "无权订阅该消息通道");
            }
        }
        return message;
    }

    private AuthenticatedUser authenticate(StompHeaderAccessor accessor) {
        String header = firstNativeHeader(accessor, HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(401, "WebSocket 缺少认证令牌");
        }
        String token = header.substring(7).trim();
        if (token.isBlank()) {
            throw new BusinessException(401, "WebSocket 认证令牌为空");
        }
        try {
            return jwtTokenService.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(401, "WebSocket 认证令牌无效");
        }
    }

    private AuthenticatedUser principal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof AuthenticatedUser user) {
            return user;
        }
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(AuthenticatedUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of(user.authority()));
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
