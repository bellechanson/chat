package com.green.chat.message.dto;

// 사진 묶음에서 한 장만 지우기 본문: { "id": 메시지 번호, "photoId": 사진 번호 } (채팅방, DM 공통)
public record DeletePhotoRequest(Long id, Long photoId) {
}