package com.green.chat.notify.dto;

// 알림 끄기 요청 본문: { "duration": "1h" }
//   "1h"(1시간) / "8h"(8시간) / "morning"(아침 9시까지) / "forever"(계속) / "off"(다시 켜기)
public record MuteRequest(String duration) {
}