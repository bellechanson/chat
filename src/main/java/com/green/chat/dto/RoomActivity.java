package com.green.chat.dto;

// 방 목록 화면에 보내는 "이 방에 새 메시지 왔음" 알림
public record RoomActivity(Long roomId, String sender) {
}