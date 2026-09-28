package com.andrewproject.agent.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.andrewproject.agent.config.ApplicationConfig;
import com.andrewproject.agent.config.graph.StreamingGenerator;
import com.andrewproject.agent.enums.NodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.Resource;

import java.util.Map;

@Slf4j
public class PromptTemplateNode implements NodeAction {

    private final Map<String, NodeStatus> node2Status;

    public static final String NODE_NAME = "prompt_template";

    public static final String NODE_CONTENT = "prompt_template_content";

    public PromptTemplateNode(Map<String, NodeStatus> node2Status) {
        this.node2Status = node2Status;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        String appId = state.value("app_id", "");
        log.info("appId: {}", appId);

        SystemPromptTemplate systemPromptTemplate = getPromptTemplate(appId);
        String promptTemplateContent = systemPromptTemplate.getTemplate();

        AsyncGenerator<? extends NodeOutput> generator = StreamingGenerator.builder()
                .startingNode("prompt_template_stream")
                .startingState(state)
                .mapResult(() -> {
                    node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, promptTemplateContent);
                }).build(promptTemplateContent);

        return Map.of(NODE_CONTENT, generator);
    }

    private SystemPromptTemplate getPromptTemplate(String appId) {
        if (appId.equals("TRAVEL")) {
            return new SystemPromptTemplate(ApplicationConfig.getInstance().getTravelPromptTemplate());
        }
        if (appId.equals("BOOK LOOKUP")) {
            return new SystemPromptTemplate(ApplicationConfig.getInstance().getBookPromptTemplate());
        }
        return new SystemPromptTemplate("You are a helpful assistant.");
    }
}
