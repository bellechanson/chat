package com.green.chat.dto;

// 반응 누르기 요청 본문: { "id": 123, "emoji": "like" }  (채팅방, DM 공통)
//   같은 이모지를 다시 누르면 취소, 다른 이모지를 누르면 바뀜 (한 사람당 하나)
public record ReactRequest(Long id, String emoji) {
}