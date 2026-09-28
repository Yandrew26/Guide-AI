package com.andrewproject.agent.config.graph;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.async.FlowGenerator;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Streams one precomputed chunk (e.g. search results) to the client, then writes the node's
 * result into graph state. The result used to be dropped because it waited for a ChatResponse
 * that never exists here, so later nodes saw empty search results.
 */
public interface StreamingGenerator {
    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private Supplier<Map<String, Object>> mapResult;
        private String startingNode;
        private OverAllState startingState;

        public Builder mapResult(Supplier<Map<String, Object>> mapResult) {
            this.mapResult = mapResult;
            return this;
        }

        public Builder startingNode(String node) {
            this.startingNode = node;
            return this;
        }

        public Builder startingState(OverAllState state) {
            this.startingState = state;
            return this;
        }

        public AsyncGenerator<? extends NodeOutput> build(String chunk) {
            Objects.requireNonNull(mapResult, "mapResult cannot be null");
            Flux<StreamingOutput> output = Flux.just(new StreamingOutput(chunk, startingNode, startingState));
            return FlowGenerator.fromPublisher(FlowAdapters.toFlowPublisher(output), mapResult::get);
        }
    }
}
