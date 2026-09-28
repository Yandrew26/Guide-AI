package com.andrewproject.agent.config.graph;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.QuestionClassifierNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.andrewproject.agent.config.memory.SelfMysqlChatMemoryRepository;
import com.andrewproject.agent.enums.NodeStatus;
import com.andrewproject.agent.node.*;
import com.andrewproject.agent.repository.AuthorRepository;
import com.andrewproject.agent.repository.SystemUserRepository;
import com.andrewproject.agent.service.ChatHistoryService;
import com.andrewproject.agent.service.SearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class ParallelGraphConfig {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AuthorRepository authorRepository;

    @Resource
    private SearchService searchService;

    @Resource
    private SystemUserRepository systemUserRepository;

    @Bean
    public StateGraph parallelStreamGraph(ChatClient.Builder chatClientBuilder, ToolCallbackProvider toolCallbackProvider, SelfMysqlChatMemoryRepository selfMysqlChatMemoryRepository) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();

            keyStrategyHashMap.put("app_id", new ReplaceStrategy());
            keyStrategyHashMap.put("message", new ReplaceStrategy());
            keyStrategyHashMap.put("conversation_id", new ReplaceStrategy());
            keyStrategyHashMap.put("trace_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_name", new ReplaceStrategy());

            keyStrategyHashMap.put(ChatNode.NODE_CONTENT, new ReplaceStrategy());
            keyStrategyHashMap.put(PromptTemplateNode.NODE_CONTENT, new ReplaceStrategy());
            keyStrategyHashMap.put(VectorSearchNode.NODE_CONTENT, new ReplaceStrategy());
            keyStrategyHashMap.put(Neo4jSearchNode.NODE_CONTENT, new ReplaceStrategy());

            return keyStrategyHashMap;
        };

        Map<String, NodeStatus> node2Status = new HashMap<>();

        SelfQuestionClassifierNode questionClassifierNode = SelfQuestionClassifierNode.builder()
                .chatClient(chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build())
                .inputTextKey("message")
                .categories(List.of("TRAVEL", "BOOK LOOKUP", "CASUAL CHAT", "GOOGLE WORKSPACE"))
                .outputKey("app_id")
                .classificationInstructions(List.of("classify the user query based on what the user is asking",
                        "questions about weather, time zones, maps, directions, trains or trips are TRAVEL"))
                .build();

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode(ChatNode.NODE_NAME, node_async(new ChatNode(chatHistoryService, selfMysqlChatMemoryRepository,
                        toolCallbackProvider, chatClientBuilder, node2Status)))
                .addNode(PromptTemplateNode.NODE_NAME, node_async(new PromptTemplateNode(node2Status)))
                .addNode(VectorSearchNode.NODE_NAME, node_async(new VectorSearchNode(node2Status, searchService, chatClientBuilder)))
                .addNode(Neo4jSearchNode.NODE_NAME, node_async(new Neo4jSearchNode(node2Status, authorRepository, systemUserRepository)))
                .addNode(MergeNode.NODE_NAME, node_async(new MergeNode(node2Status)))
                .addNode("classifier_node", node_async(questionClassifierNode))

                .addEdge(START, "classifier_node")
                .addEdge("classifier_node", PromptTemplateNode.NODE_NAME)
                .addConditionalEdges(PromptTemplateNode.NODE_NAME,
                        AsyncEdgeAction.edge_async(state -> {
                            String appId = state.value("app_id", "");
                            if (appId.equals("BOOK LOOKUP")) return "book_agent";
                            else return "other_agent";
                        }),
                        Map.of("book_agent", VectorSearchNode.NODE_NAME, "other_agent", ChatNode.NODE_NAME)
                )
                .addEdge(VectorSearchNode.NODE_NAME, Neo4jSearchNode.NODE_NAME)
                .addEdge(Neo4jSearchNode.NODE_NAME, ChatNode.NODE_NAME)
                .addEdge(ChatNode.NODE_NAME, MergeNode.NODE_NAME)
                .addEdge(MergeNode.NODE_NAME, END);

        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "graph flow");
        log.info("\n=== graph UML flow ===");
        log.info(representation.content());
        log.info("==================================");

        return stateGraph;
    }
}
