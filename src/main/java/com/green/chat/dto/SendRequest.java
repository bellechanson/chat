package com.green.chat.dto;

// 메시지 보내기 요청 본문: { "content": "안녕" }  (채팅방, DM 공통)
public record SendRequest(String content) {
}