package com.green.chat.message.dto;

// 메시지 수정 요청 본문: { "id": 123, "content": "고친 내용" }  (채팅방, DM 공통)
public record EditRequest(Long id, String content) {
}