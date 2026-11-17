package com.green.chat.dto;

// 상대에게 보내는 "입력 중" 신호 (저장하지 않음)
//   photos·files가 0보다 크면 "사진 12장을 보내는 중"처럼 올리는 중으로 표시
public record DmTypingEvent(Long conversationId, String nickname, boolean typing, int photos, int files) {
}