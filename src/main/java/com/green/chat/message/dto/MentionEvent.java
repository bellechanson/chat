package com.green.chat.message.dto;

// 채팅방에서 내가 불림(@멘션, 내 메시지에 답장) → 사이드바 그 방에 "@" (/user/queue/mention)
public record MentionEvent(Long roomId, Long messageId) {
}