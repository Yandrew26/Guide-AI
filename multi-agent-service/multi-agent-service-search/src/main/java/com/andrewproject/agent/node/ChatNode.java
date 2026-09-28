package com.andrewproject.agent.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingChatGenerator;
import com.andrewproject.agent.config.ApplicationConfig;
import com.andrewproject.agent.config.advisor.ChatHistoryAdvisor;
import com.andrewproject.agent.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.agent.enums.NodeStatus;
import com.andrewproject.agent.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChatNode implements NodeAction {

    private final ChatHistoryService chatHistoryService;

    private final MessageWindowChatMemory messageWindowChatMemory;

    private final ChatClient chatClient;

    private final List<ToolCallback> tools;

    private final Map<String, NodeStatus> node2Status;

    public static final String NODE_NAME = "rag_chat";

    public static final String NODE_CONTENT = "rag_chat_content";

    public ChatNode(ChatHistoryService chatHistoryService,
                    SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository,
                    ToolCallbackProvider toolCallbackProvider,
                    ChatClient.Builder chatClientBuilder,
                    Map<String, NodeStatus> node2Status) {
        this.messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(selfMysqlChatMemoryRepository)
                .maxMessages(10)
                .build();
        this.chatHistoryService = chatHistoryService;
        this.node2Status = node2Status;

        this.tools = List.of(toolCallbackProvider.getToolCallbacks());
        tools.forEach(t -> log.info("Tool callback: {}", t.getToolDefinition().name()));

        this.chatClient = chatClientBuilder.build();
    }

    // Each route gets only its own tools: every schema sent costs context (the Google server alone has ~90).
    // MCP tool names embed the connection name, e.g. multi_agent_mcp_client_google_workspace_search_gmail_messages.
    private List<ToolCallback> toolsFor(String appId) {
        return switch (appId) {
            case "GOOGLE WORKSPACE" -> tools.stream().filter(t -> isWorkspaceTool(t)).toList();
            case "TRAVEL" -> tools.stream().filter(t -> !isWorkspaceTool(t)).toList();
            default -> List.of();
        };
    }

    private static boolean isWorkspaceTool(ToolCallback tool) {
        return tool.getToolDefinition().name().contains("google_workspace");
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        String appId = state.value("app_id","");
        String message = state.value("message","");
        String conversationId = state.value("conversation_id","");
        String traceId = state.value("trace_id","");
        String userId = state.value("user_id","");
        String userName = state.value("user_name","");
        List<String> elasticsearchResult = state.value(VectorSearchNode.NODE_CONTENT, new ArrayList<>());
        List<String> neo4jResults = state.value(Neo4jSearchNode.NODE_CONTENT, new ArrayList<>());

        Flux<ChatResponse> chatResponseFlux = chatClient
                .prompt(getPrompt(appId, message, elasticsearchResult, neo4jResults))
                .advisors(a -> a.advisors(getAdvisors(appId, conversationId, traceId, userId, userName))
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolCallbacks(toolsFor(appId))
                .stream().chatResponse();

        AsyncGenerator<? extends NodeOutput> generator = StreamingChatGenerator.builder()
                .startingNode("rag_chat_stream")
                .startingState(state)
                .mapResult(response -> {
                    String content = response.getResult().getOutput().getText();
                    List<String> queryVariants = Arrays.asList(content.split("\n"));
                    node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, queryVariants);
                }).build(chatResponseFlux);
        return Map.of(NODE_CONTENT, generator);
    }

    private List<Advisor> getAdvisors(String appId, String conversationId, String traceId, String userId, String userName) {
        List<Advisor> advisors = new ArrayList<>();

        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build();
        advisors.add(messageChatMemoryAdvisor);
        log.info("appId:{}, traceId:{}, conversationId:{} - Started chat short memory", appId, traceId, conversationId);

        ChatHistoryAdvisor chatHistoryAdvisor = ChatHistoryAdvisor.builder()
                .chatHistoryService(chatHistoryService)
                .appId(appId)
                .traceId(traceId)
                .conversationId(conversationId)
                .userId(userId)
                .userName(userName)
                .build();
        advisors.add(chatHistoryAdvisor);
        log.info("appId:{}, traceId:{}, conversationId:{} - Started chat  ", appId, traceId, conversationId);

        return advisors;
    }

    private Prompt getPrompt(String appId, String message, List <String> elasticsearchResult, List<String> neo4jResults) {
        Message systemMessage = new SystemMessage("You are a helpful assistant.");
        if (appId.equals("BOOK LOOKUP")) {
            SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(ApplicationConfig.getInstance().getBookPromptTemplate());
            systemMessage = systemPromptTemplate.createMessage(Map.of("name", "book query",
                    "voice", "friendly assistant",
                    "elasticsearch_results", elasticsearchResult,
                    "neo4j_results", neo4jResults));

            log.info("Elasticsearch results: {}", elasticsearchResult);
            log.info("Neo4j results: {}", neo4jResults);
        }
        if (appId.equals("TRAVEL")) {
            systemMessage = new SystemMessage(ApplicationConfig.getInstance().getTravelPromptTemplate());
        }

        UserMessage userMessage = new UserMessage(message);

        return new Prompt(List.of(
                systemMessage,
                userMessage
        ));
    }
}
