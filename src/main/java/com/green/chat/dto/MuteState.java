package com.green.chat.dto;

// 한 방의 알림 끄기 상태 (화면 표시용, 내 다른 탭에도 /user/queue/mute로 보냄)
//   scope  : "ROOM"(채팅방) / "DM"(1:1 대화)
//   muted  : 지금 꺼져 있으면 true
//   until  : "2026-11-18 18:30"까지 끔, 계속 끔이거나 켜져 있으면 null
public record MuteState(String scope, Long targetId, boolean muted, String until) {
}