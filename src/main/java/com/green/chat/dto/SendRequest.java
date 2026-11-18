package com.green.chat.dto;

// 메시지 보내기 요청 본문: { "content": "안녕", "replyTo": 120 }  (채팅방, DM 공통)
//   replyTo: 답장이면 원래 메시지 번호, 아니면 빠지거나 null
public record SendRequest(String content, Long replyTo) {
}