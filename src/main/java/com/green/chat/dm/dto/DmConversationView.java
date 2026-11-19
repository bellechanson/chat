package com.green.chat.dm.dto;

// 사이드바 1:1 대화 한 줄
public record DmConversationView(Long id, Long partnerId, String partnerNickname, String lastMessage, long unread) {
}