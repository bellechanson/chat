package com.green.chat.dm.dto;

// "readerId가 conversationId 대화를 읽었음" 알림
public record DmReadEvent(Long conversationId, Long readerId) {
}