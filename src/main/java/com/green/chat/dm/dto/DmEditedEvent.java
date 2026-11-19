package com.green.chat.dm.dto;

// DM 메시지가 수정됨 → 나(다른 탭 포함)와 상대 화면에서 내용 교체 + "(수정됨)"
//   editedAt: "2026-11-17 14:35"
//   last    : 이 대화의 마지막 메시지면 true → 사이드바 미리보기 글도 바꿈
public record DmEditedEvent(Long conversationId, Long messageId, String content, String editedAt, boolean last) {
}