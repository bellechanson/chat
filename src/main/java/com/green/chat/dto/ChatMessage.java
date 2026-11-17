package com.green.chat.dto;

import java.time.LocalDateTime;

import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.util.Formats;

// deleted = true 이면 content, file은 보내지 않음 (화면에서 "삭제된 메시지입니다")
// time: "14:30", date: "2026-11-17" (날짜 구분선용)
public record ChatMessage(MessageType type, String sender, String content, String time, Long id, boolean deleted,
		FileDto file, String date) {

	// 지금 시각으로 메시지 생성 (입장/퇴장 알림용)
	public static ChatMessage of(MessageType type, String sender, String content) {
		LocalDateTime now = LocalDateTime.now();
		return new ChatMessage(type, sender, content, Formats.clock(now), null, false, null, Formats.day(now));
	}

	// DB 메시지 → 화면용
	public static ChatMessage from(ChatMessageEntity e) {
		boolean deleted = e.isDeleted();
		FileDto file = deleted ? null
				: FileDto.of(e.getAttachment(), "/api/rooms/" + e.getRoom().getId() + "/files/" + e.getId());
		return new ChatMessage(MessageType.TALK, e.getSender(), deleted ? null : e.getContent(),
				Formats.clock(e.getCreatedAt()), e.getId(), deleted, file, Formats.day(e.getCreatedAt()));
	}

	// "이 번호 메시지가 삭제됨" 신호
	public static ChatMessage deleted(Long messageId) {
		return new ChatMessage(MessageType.MESSAGE_DELETED, null, null, null, messageId, true, null, null);
	}
}