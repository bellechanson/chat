package com.green.chat.dto;

import java.util.List;

// 이전 대화 한 묶음: 메시지 목록(오래된 → 최신) + 더 이전 대화가 있는지
public record MessagePage(List<ChatMessage> messages, boolean hasMore) {
}