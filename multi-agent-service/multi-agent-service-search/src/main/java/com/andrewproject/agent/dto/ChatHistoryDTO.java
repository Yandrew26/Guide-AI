package com.andrewproject.agent.dto;

import com.andrewproject.agent.entity.ChatHistory;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ChatHistoryDTO {

    private Long id;

    private String appId;

    private String userName;

    private String userId;

    private String conversationId;

    private String traceId;

    private String content;

    private String type;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(
            pattern = "yyyy-MM-dd HH:mm:ss",
            timezone = "GMT+8"
    )
    private LocalDateTime createdDate;

    public ChatHistoryDTO(ChatHistory chatHistory) {
        this.id = chatHistory.getId();
        this.appId = chatHistory.getAppId();
        this.userId = chatHistory.getUserId();
        this.userName = chatHistory.getUserName();
        this.conversationId = chatHistory.getConversationId();
        this.traceId = chatHistory.getTraceId();
        this.content = chatHistory.getContent();
        this.type = chatHistory.getType();
        this.createdDate = chatHistory.getCreatedDate();
    }
}
