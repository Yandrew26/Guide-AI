package com.andrewproject.agent.service;

import com.andrewproject.agent.dto.ChatHistoryDTO;
import com.andrewproject.agent.dto.ChatHistoryPageDTO;
import com.andrewproject.agent.dto.PageDTO;
import com.andrewproject.agent.entity.ChatHistory;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface ChatHistoryService extends IService<ChatHistory> {
    void addChatHistory(ChatHistoryDTO chatHistoryDTO);
    List<ChatHistoryDTO> getByConversationId(String conversationId);
    PageDTO<ChatHistoryPageDTO> pages(Long pageNum, Long pageSize, String userid, String DateStart, String dateEnd);
}
