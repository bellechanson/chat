package com.green.chat.dto;

// 채팅방 사람들에게 보내는 "입력 중" 신호 (저장하지 않음)
public record RoomTypingEvent(String nickname, boolean typing) {
}