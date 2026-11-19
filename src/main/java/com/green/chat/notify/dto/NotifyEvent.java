package com.green.chat.notify.dto;

// 데스크톱 알림 한 건
//   tag       : 대화방 단위 (같은 방 알림은 한 칸에서 최신 것으로 교체)
//   messageId : 창이 여러 개일 때 같은 메시지 중복 표시 방지용
public record NotifyEvent(String title, String body, String url, String tag, Long messageId) {
}