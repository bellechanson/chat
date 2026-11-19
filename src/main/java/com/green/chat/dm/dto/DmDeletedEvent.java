package com.green.chat.dm.dto;

// DM 메시지가 삭제됨 → 나와 상대 화면에서 "삭제된 메시지입니다"로 바꾸기
public record DmDeletedEvent(Long conversationId, Long messageId) {
}