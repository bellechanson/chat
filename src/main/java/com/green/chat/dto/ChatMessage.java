package com.green.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.entity.MessagePhoto;
import com.green.chat.util.Formats;

// deleted = true 이면 content, file, photos는 보내지 않음 (화면에서 "삭제된 메시지입니다")
// time: "14:30", date: "2026-11-17" (날짜 구분선용)
// editedAt: 수정했으면 "2026-11-17 14:35" (화면에 "(수정됨)", 마우스를 올리면 이 시각), 안 했으면 null
// reactions: 이모지 반응 칩 목록 (없으면 빈 목록)
// photos: 사진 묶음 메시지의 사진들 (보낸 순서, 지운 사진 제외, 묶음이 아니면 빈 목록)
// reply: 답장이면 원래 메시지 인용, 아니면 null
// mentions: 내용 속 "<@번호>"를 그릴 이름 {회원번호: 이름} (없으면 빈 맵)
public record ChatMessage(MessageType type, String sender, String content, String time, Long id, boolean deleted,
		FileDto file, String date, String editedAt, List<ReactionDto> reactions, List<FileDto> photos, ReplyDto reply,
		Map<Long, String> mentions) {

	// 지금 시각으로 메시지 생성 (입장/퇴장 알림용)
	public static ChatMessage of(MessageType type, String sender, String content) {
		LocalDateTime now = LocalDateTime.now();
		return new ChatMessage(type, sender, content, Formats.clock(now), null, false, null, Formats.day(now), null,
				List.of(), List.of(), null, Map.of());
	}

	// DB 메시지 → 화면용 (새로 보낸 글·파일 메시지: 반응·사진 묶음 없음)
	public static ChatMessage from(ChatMessageEntity e) {
		return from(e, List.of(), List.of());
	}

	// DB 메시지 + 반응 + 사진 묶음 → 화면용 (이전 대화 불러오기, 새 사진 묶음)
	public static ChatMessage from(ChatMessageEntity e, List<ReactionDto> reactions, List<MessagePhoto> photos) {
		return from(e, reactions, photos, null, Map.of());
	}

	// + 답장 인용, 멘션 이름까지 (글 메시지 보내기, 이전 대화 불러오기)
	public static ChatMessage from(ChatMessageEntity e, List<ReactionDto> reactions, List<MessagePhoto> photos,
			ReplyDto reply, Map<Long, String> mentions) {
		boolean deleted = e.isDeleted();
		String fileUrl = fileUrl(e);
		FileDto file = deleted ? null : FileDto.of(e.getAttachment(), fileUrl);
		String editedAt = (deleted || e.getEditedAt() == null) ? null : Formats.fullTime(e.getEditedAt());
		return new ChatMessage(MessageType.TALK, e.getSender(), deleted ? null : e.getContent(),
				Formats.clock(e.getCreatedAt()), e.getId(), deleted, file, Formats.day(e.getCreatedAt()), editedAt,
				deleted ? List.of() : reactions, deleted ? List.of() : FileDto.photos(fileUrl, photos), reply,
				deleted ? Map.of() : mentions);
	}

	// "이 번호 메시지가 삭제됨" 신호
	public static ChatMessage deleted(Long messageId) {
		return new ChatMessage(MessageType.MESSAGE_DELETED, null, null, null, messageId, true, null, null, null,
				List.of(), List.of(), null, Map.of());
	}

	// "이 번호 메시지가 수정됨" 신호 (새 내용 + 수정 시각 + 멘션 이름)
	public static ChatMessage edited(ChatMessageEntity e, Map<Long, String> mentions) {
		return new ChatMessage(MessageType.MESSAGE_EDITED, e.getSender(), e.getContent(), null, e.getId(), false, null,
				null, Formats.fullTime(e.getEditedAt()), List.of(), List.of(), null, mentions);
	}

	// "사진 묶음에서 한 장이 지워짐" 신호 (남은 사진 전체 → 화면에서 묶음을 다시 그림)
	public static ChatMessage photosChanged(ChatMessageEntity e, List<MessagePhoto> photos) {
		return new ChatMessage(MessageType.PHOTOS_CHANGED, e.getSender(), null, null, e.getId(), false, null, null,
				null, List.of(), FileDto.photos(fileUrl(e), photos), null, Map.of());
	}

	// 이 메시지의 파일 주소: /api/rooms/1/files/351 (사진 묶음은 뒤에 /photos/사진번호), 모아보기에서도 씀
	public static String fileUrl(ChatMessageEntity e) {
		return "/api/rooms/" + e.getRoom().getId() + "/files/" + e.getId();
	}
}