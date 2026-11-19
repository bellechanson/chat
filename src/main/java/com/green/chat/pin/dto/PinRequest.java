package com.green.chat.pin.dto;

// POST /api/rooms/{id}/pin, /api/dm/{id}/pin { "messageId": 351 } → 이 메시지를 공지로 고정
public record PinRequest(Long messageId) {
}