package com.andrewproject.agent.config;

import com.andrewproject.agent.filter.AuthGatewayFilter;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    @Resource
    private AuthGatewayFilter authGatewayFilter;

    @Override
    public AuthGatewayFilter apply(Object config) {
        return authGatewayFilter;
    }

    @Override
    public String name() {
        return "AuthFilter";
    }
}
