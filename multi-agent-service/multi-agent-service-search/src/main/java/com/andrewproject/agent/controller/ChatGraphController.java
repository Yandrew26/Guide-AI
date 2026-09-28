package com.andrewproject.agent.controller;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.andrewproject.agent.config.graph.GraphProcess;
import com.andrewproject.agent.service.SystemUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/graph")
public class ChatGraphController {

    @Resource
    private SystemUserService systemUserService;

    private final CompiledGraph compiledGraph;

    public ChatGraphController(@Qualifier("parallelStreamGraph") StateGraph stateGraph) throws GraphStateException {
        this.compiledGraph = stateGraph.compile();
    }

    @GetMapping("/create-chat")
    public ResponseEntity<String> create(@RequestParam("userId") String userId) {
        StringBuilder resultConversationId = new StringBuilder();
        String sessionId = UUID.randomUUID().toString().replaceAll("-", "");
        String conversationId = resultConversationId.append(userId).append("_").append(sessionId).toString();

        return ResponseEntity.ok("chat started successfully at conversation_id: " + conversationId);
    }

    @GetMapping("/rag")
    public ResponseEntity<Map<String, Object>> chatRag(@RequestParam(name = "appId", required = false) String appId,
                                                       @RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) throws GraphRunnerException {
        log.info("start chat");

        String userId = conversationId.split("_")[0];
        String userName = systemUserService.getUserNameByUserId(userId);
        String traceId = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> objectMap = setupGraph(appId, message, conversationId, traceId, userId, userName);

        OverAllState result = compiledGraph.invoke(objectMap).get();

        return ResponseEntity.ok(result.data());
    }

    @GetMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatRagStream(@RequestParam(name = "appId", required = false) String appId,
                                                       @RequestParam("message") String message,
                                                       @RequestParam("conversationId") String conversationId) throws GraphRunnerException {
        log.info("start chat stream");

        String userId = conversationId.split("_")[0];
        String userName = systemUserService.getUserNameByUserId(userId);
        String traceId = UUID.randomUUID().toString().replaceAll("-", "");
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();

        Map<String, Object> objectMap = setupGraph(appId, message, conversationId, traceId, userId, userName);
        GraphProcess graphProcess = new GraphProcess(compiledGraph);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AsyncGenerator<NodeOutput> resultFuture = compiledGraph.stream(objectMap, runnableConfig);
        graphProcess.processStream(resultFuture, sink);

        return sink.asFlux()
                .doOnCancel(() -> log.info("Client disconnected from stream"))
                .doOnError(e -> log.info("Error occurred during streaming", e));
    }

    private Map<String, Object> setupGraph(String appId, String message, String conversationId, String traceId, String userId, String userName) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("app_id", appId);
        objectMap.put("message", message);
        objectMap.put("conversation_id", conversationId);
        objectMap.put("trace_id", traceId);
        objectMap.put("user_id", userId);
        objectMap.put("user_name", userName);
        return objectMap;
    }
}
