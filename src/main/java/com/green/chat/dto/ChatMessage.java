package com.green.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.util.Formats;

// deleted = true 이면 content, file은 보내지 않음 (화면에서 "삭제된 메시지입니다")
// time: "14:30", date: "2026-11-17" (날짜 구분선용)
// editedAt: 수정했으면 "2026-11-17 14:35" (화면에 "(수정됨)", 마우스를 올리면 이 시각), 안 했으면 null
// reactions: 이모지 반응 칩 목록 (없으면 빈 목록)
public record ChatMessage(MessageType type, String sender, String content, String time, Long id, boolean deleted,
		FileDto file, String date, String editedAt, List<ReactionDto> reactions) {

	// 지금 시각으로 메시지 생성 (입장/퇴장 알림용)
	public static ChatMessage of(MessageType type, String sender, String content) {
		LocalDateTime now = LocalDateTime.now();
		return new ChatMessage(type, sender, content, Formats.clock(now), null, false, null, Formats.day(now), null,
				List.of());
	}

	// DB 메시지 → 화면용 (새로 보낸 메시지: 반응 없음)
	public static ChatMessage from(ChatMessageEntity e) {
		return from(e, List.of());
	}

	// DB 메시지 + 반응 → 화면용 (이전 대화 불러오기)
	public static ChatMessage from(ChatMessageEntity e, List<ReactionDto> reactions) {
		boolean deleted = e.isDeleted();
		FileDto file = deleted ? null
				: FileDto.of(e.getAttachment(), "/api/rooms/" + e.getRoom().getId() + "/files/" + e.getId());
		String editedAt = (deleted || e.getEditedAt() == null) ? null : Formats.fullTime(e.getEditedAt());
		return new ChatMessage(MessageType.TALK, e.getSender(), deleted ? null : e.getContent(),
				Formats.clock(e.getCreatedAt()), e.getId(), deleted, file, Formats.day(e.getCreatedAt()), editedAt,
				deleted ? List.of() : reactions);
	}

	// "이 번호 메시지가 삭제됨" 신호
	public static ChatMessage deleted(Long messageId) {
		return new ChatMessage(MessageType.MESSAGE_DELETED, null, null, null, messageId, true, null, null, null,
				List.of());
	}

	// "이 번호 메시지가 수정됨" 신호 (새 내용 + 수정 시각)
	public static ChatMessage edited(ChatMessageEntity e) {
		return new ChatMessage(MessageType.MESSAGE_EDITED, e.getSender(), e.getContent(), null, e.getId(), false, null,
				null, Formats.fullTime(e.getEditedAt()), List.of());
	}
}