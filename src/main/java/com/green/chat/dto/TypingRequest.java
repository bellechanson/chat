package com.green.chat.dto;

// 입력 중 신호 본문: { "typing": true }  (DM, 채팅방 공통)
public record TypingRequest(boolean typing) {
}