package com.green.chat.dto;

// 공지가 바뀜 (고정·해제) — 채팅방은 /topic/room/{id}/pin, 1:1은 /user/queue/dm-pin
//   targetId : 채팅방 번호 또는 1:1 대화 번호 (다른 대화 화면에서는 무시)
//   pin      : 새 공지, 해제됐으면 null
public record PinEvent(Long targetId, PinDto pin) {
}