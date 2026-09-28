package com.andrewproject.agent.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Spring AI's default wiring fails the whole service if any one MCP server doesn't answer.
 * Here each server is initialized in parallel; the ones that fail are logged and their tools left out.
 * Requires spring.ai.mcp.client.initialized=false and toolcallback.enabled=false (see application.yaml).
 */
@Slf4j
@Configuration
public class McpToolsConfig {

    private static final Duration INIT_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public ToolCallbackProvider mcpToolCallbacks(List<McpAsyncClient> mcpAsyncClients) {
        List<McpAsyncClient> ready = Flux.fromIterable(mcpAsyncClients)
                .flatMap(client -> client.initialize()
                        .timeout(INIT_TIMEOUT)
                        .thenReturn(client)
                        .onErrorResume(e -> {
                            log.warn("MCP server '{}' is unavailable, its tools are disabled: {}",
                                    client.getClientInfo().name(), e.toString());
                            return client.closeGracefully().onErrorComplete().then(Mono.empty());
                        }))
                .collectList()
                .block();
        log.info("MCP servers ready: {}", ready.stream().map(c -> c.getClientInfo().name()).toList());
        return new AsyncMcpToolCallbackProvider(ready);
    }
}
