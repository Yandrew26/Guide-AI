package com.andrewproject.agent.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.andrewproject.agent.config.graph.StreamingGenerator;
import com.andrewproject.agent.enums.NodeStatus;
import com.andrewproject.agent.repository.AuthorRepository;
import com.andrewproject.agent.repository.SystemUserRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class Neo4jSearchNode implements NodeAction {

    public static final String NODE_NAME = "neo4j_search_node";
    public static final String NODE_CONTENT = "neo4j_search_node_content";

    private final Map<String, NodeStatus> node2Status;
    private final AuthorRepository authorRepository;
    private final SystemUserRepository systemUserRepository;

    public Neo4jSearchNode(Map<String, NodeStatus> node2Status, AuthorRepository authorRepository, SystemUserRepository systemUserRepository) {
        this.node2Status = node2Status;
        this.authorRepository = authorRepository;
        this.systemUserRepository = systemUserRepository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("Neo4jSearchNode started");
        node2Status.put(NODE_NAME, NodeStatus.RUNNING);

        String userId = state.value("user_id","");
        List<String> VectorSearchNodeResult = state.value(VectorSearchNode.NODE_CONTENT, new ArrayList<>());
        log.info("VectorSearchNodeResult: {}", VectorSearchNodeResult);

        if (VectorSearchNodeResult.isEmpty()) {
            log.info("No authors extracted, skipping Neo4j search");
            node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
            return Map.of(NODE_CONTENT, new ArrayList<>());
        }

        List<String> recommendations = new ArrayList<>();
        for (String bookTitle : VectorSearchNodeResult) {
            systemUserRepository.createUserEbookRelationshipIfNotExist(userId, bookTitle);
            log.info("Neo4j - User({})->Ebook({}) Relationship", userId, bookTitle);
            List<String> ebookTitlesWithSameAuthor = authorRepository.findEbookTitlesWithSameAuthor(bookTitle);
            List<String> ebookTitlesWithSameUser = systemUserRepository.findEbookTitlesWithSameUser(bookTitle, userId);
            recommendations.addAll(ebookTitlesWithSameAuthor);
            recommendations.addAll(ebookTitlesWithSameUser);
        }

        AsyncGenerator<? extends NodeOutput> generator = StreamingGenerator.builder()
                .startingNode("neo4j_search_stream")
                .startingState(state)
                .mapResult(() -> {
                    node2Status.put(NODE_NAME, NodeStatus.COMPLETED);
                    return Map.of(NODE_CONTENT, recommendations);
                }).build(recommendations.toString());

        return Map.of(NODE_CONTENT, generator);
    }
}
