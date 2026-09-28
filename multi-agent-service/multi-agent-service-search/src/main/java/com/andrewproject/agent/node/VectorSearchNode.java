package com.andrewproject.agent.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.andrewproject.agent.config.ApplicationConfig;
import com.andrewproject.agent.config.graph.StreamingGenerator;
import com.andrewproject.agent.enums.NodeStatus;
import com.andrewproject.agent.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class VectorSearchNode implements NodeAction {

    private final Map<String, NodeStatus> node2Status;
    private final SearchService searchService;
    private final ChatClient.Builder chatClientBuilder;

    public static final String NODE_NAME = "vector_search";
    public static final String NODE_CONTENT = "vector_search_content";

    public VectorSearchNode(Map<String, NodeStatus> node2Status, SearchService searchService, ChatClient.Builder chatClientBuilder) {
        this.node2Status = node2Status;
        this.searchService = searchService;
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);
        String message = state.value("message","");

        List<Document> elasticsearchList = searchService.search(message,20);

        if (elasticsearchList.isEmpty()) {
            return Map.of(NODE_CONTENT, new ArrayList<>());
        }

        log.info("elasticsearchList result: {}", elasticsearchList.toString());
        ListOutputConverter outputConverter = new ListOutputConverter();

        ChatResponse chatResponse = chatClientBuilder.build()
                .prompt(getPrompt(elasticsearchList))
                .call()
                .chatResponse();

        List<String> result = outputConverter.convert(chatResponse.getResult().getOutput().getText());
        log.info("chatResponse: {}", result);

        AsyncGenerator<? extends NodeOutput> generator = StreamingGenerator.builder()
                .startingNode("vector_search_stream")
                .startingState(state)
                .mapResult(() -> {
                    node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, result);
                }).build(result.toString());

        return Map.of(NODE_CONTENT, generator);
    }

    private Prompt getPrompt(List<Document> elasticsearchList) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(ApplicationConfig.getInstance().getBookSearchPromptTemplate());
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("elasticsearch_results", elasticsearchList));
        return new Prompt(systemMessage);
    }
}
