package com.green.chat.dto;

// 메시지 삭제 요청 본문: { "id": 123 }  (채팅방, DM 공통)
public record DeleteRequest(Long id) {
}