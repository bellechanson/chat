package com.green.chat.dto;

// 상대에게 보내는 "입력 중" 신호 (저장하지 않음)
public record DmTypingEvent(Long conversationId, String nickname, boolean typing) {
}