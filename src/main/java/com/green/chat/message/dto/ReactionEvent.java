package com.green.chat.message.dto;

import java.util.List;

// 반응이 바뀜 → 그 메시지의 반응 칩 전체를 새로 그림 (채팅방, DM 공통)
//   conversationId: DM이면 대화 번호, 채팅방이면 null
public record ReactionEvent(Long conversationId, Long messageId, List<ReactionDto> reactions) {
}