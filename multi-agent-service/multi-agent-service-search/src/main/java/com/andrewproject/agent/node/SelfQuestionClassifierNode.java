package com.andrewproject.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.util.StringUtils;

public class SelfQuestionClassifierNode implements NodeAction {
    private static final String CLASSIFIER_PROMPT_TEMPLATE = "\t### Job Description',\n\tYou are a text classification engine that analyzes text data and assigns categories based on user input or automatically determined categories.\n\t### Task\n\tYour task is to assign one category ONLY to the input text and only one category can be  returned in the output. Additionally, you need to extract the key words from the text that are related to the classification.\n\t### Format\n\tThe input text is: {inputText}. Categories are specified as a category list: {categories}. Classification instructions may be included to improve the classification accuracy: {classificationInstructions}.\n\t### Constraint\n\tDO NOT include anything other than the JSON array in your response.\n";
    private static final String QUESTION_CLASSIFIER_USER_PROMPT_1 = "\t{\"input_text\": [\"I want to book a flight to Paris next month and need hotel recommendations\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": [\"classify based on the primary intent of the user's question\"]}\n";
    private static final String QUESTION_CLASSIFIER_ASSISTANT_PROMPT_1 = "TRAVEL";
    private static final String QUESTION_CLASSIFIER_USER_PROMPT_2 = "\t{\"input_text\": [\"Can you recommend a good mystery novel by Agatha Christie?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n";
    private static final String QUESTION_CLASSIFIER_ASSISTANT_PROMPT_2 = "BOOK LOOKUP";
    private static final String QUESTION_CLASSIFIER_USER_PROMPT_3 = "\t{\"input_text\": [\"Hey, how is your day?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n";
    private static final String QUESTION_CLASSIFIER_ASSISTANT_PROMPT_3 = "CASUAL CHAT";
    private static final String QUESTION_CLASSIFIER_USER_PROMPT_4 = "\t{\"input_text\": [\"Can you schedule a meeting with my team and create a Google Doc for the agenda?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n";
    private static final String QUESTION_CLASSIFIER_ASSISTANT_PROMPT_4 = "GOOGLE WORKSPACE";
    private final SystemPromptTemplate systemPromptTemplate;
    private final ChatClient chatClient;
    private String inputText;
    private final List<String> categories;
    private final List<String> classificationInstructions;
    private final String inputTextKey;
    private final String outputKey;

    public SelfQuestionClassifierNode(ChatClient chatClient, String inputTextKey, List<String> categories, List<String> classificationInstructions, String outputKey) {
        this.chatClient = chatClient;
        this.inputTextKey = inputTextKey;
        this.categories = categories;
        this.classificationInstructions = classificationInstructions;
        this.systemPromptTemplate = new SystemPromptTemplate("You are a text classification engine that analyzes text data and assigns categories based on user input.\n\n### Task\nYour task is to assign EXACTLY ONE category to the input text. You MUST choose from the provided category list and CANNOT create new categories or combine existing ones.\n\n### Format\nThe input text is: {inputText}\nCategories are: {categories}\nClassification instructions: {classificationInstructions}\n\n### Rules\n1. Return ONLY the exact category name from the list, nothing else\n2. Do NOT combine categories\n3. Do NOT create new categories\n4. Do NOT add any explanation or additional text\n5. Choose the single most relevant category");
        this.outputKey = outputKey;
    }

    public Map<String, Object> apply(OverAllState state) throws Exception {
        if (StringUtils.hasLength(this.inputTextKey)) {
            this.inputText = (String)state.value(this.inputTextKey).orElse(this.inputText);
        }

        List<Message> messages = new ArrayList();
        UserMessage userMessage1 = new UserMessage("\t{\"input_text\": [\"I want to book a flight to Paris next month and need hotel recommendations\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": [\"classify based on the primary intent of the user's question\"]}\n");
        AssistantMessage assistantMessage1 = new AssistantMessage("TRAVEL");
        UserMessage userMessage2 = new UserMessage("\t{\"input_text\": [\"Can you recommend a good mystery novel by Agatha Christie?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n");
        AssistantMessage assistantMessage2 = new AssistantMessage("BOOK LOOKUP");
        UserMessage userMessage3 = new UserMessage("\t{\"input_text\": [\"Hey, how is your day?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n");
        AssistantMessage assistantMessage3 = new AssistantMessage("CASUAL CHAT");
        UserMessage userMessage4 = new UserMessage("\t{\"input_text\": [\"Can you schedule a meeting with my team and create a Google Doc for the agenda?\"],\n\t\"categories\": [\"TRAVEL\", \"BOOK LOOKUP\", \"CASUAL CHAT\", \"GOOGLE WORKSPACE\"],\n\t\"classification_instructions\": []}\n");
        AssistantMessage assistantMessage4 = new AssistantMessage("GOOGLE WORKSPACE");
        messages.add(userMessage1);
        messages.add(assistantMessage1);
        messages.add(userMessage2);
        messages.add(assistantMessage2);
        messages.add(userMessage3);
        messages.add(assistantMessage3);
        messages.add(userMessage4);
        messages.add(assistantMessage4);
        ChatResponse response = this.chatClient.prompt().system(this.systemPromptTemplate.render(Map.of("inputText", this.inputText, "categories", this.categories, "classificationInstructions", this.classificationInstructions))).messages(messages).user(this.inputText).call().chatResponse();
        Map<String, Object> updatedState = new HashMap<>();
        updatedState.put(this.outputKey, response.getResult().getOutput().getText());
        if (state.value("messages").isPresent()) {
            updatedState.put("messages", response.getResult().getOutput());
        }

        return updatedState;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String inputTextKey;
        private ChatClient chatClient;
        private List<String> categories;
        private List<String> classificationInstructions;
        private String outputKey;

        public Builder inputTextKey(String input) {
            this.inputTextKey = input;
            return this;
        }

        public Builder chatClient(ChatClient chatClient) {
            this.chatClient = chatClient;
            return this;
        }

        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        public Builder classificationInstructions(List<String> classificationInstructions) {
            this.classificationInstructions = classificationInstructions;
            return this;
        }

        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public SelfQuestionClassifierNode build() {
            return new SelfQuestionClassifierNode(this.chatClient, this.inputTextKey, this.categories, this.classificationInstructions, this.outputKey);
        }
    }
}
