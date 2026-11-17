package com.green.chat.dto;

import java.util.List;

import com.green.chat.entity.DmMessage;
import com.green.chat.entity.MessagePhoto;
import com.green.chat.util.Formats;

// 화면으로 보내는 DM 메시지
public record DmMessageDto(Long id, Long conversationId, Long senderId, // 내 메시지인지는 화면에서 내 id와 비교
		String senderNickname, String content, // 삭제된 메시지면 null
		String time, // "14:30"
		boolean read, // 상대가 읽었는지 (false면 "1" 표시)
		boolean deleted, FileDto file, // 첨부 파일 (없거나 삭제됐으면 null)
		String date, // "2026-11-17" (날짜 구분선용)
		String editedAt, // 수정했으면 "2026-11-17 14:35" (화면에 "(수정됨)"), 안 했으면 null
		List<ReactionDto> reactions, // 이모지 반응 칩 목록 (없으면 빈 목록)
		List<FileDto> photos // 사진 묶음 메시지의 사진들 (보낸 순서, 지운 사진 제외, 묶음이 아니면 빈 목록)
) {
	// 새로 보낸 글·파일 메시지: 반응·사진 묶음 없음
	public static DmMessageDto from(DmMessage m, String senderNickname) {
		return from(m, senderNickname, List.of(), List.of());
	}

	// 이전 대화 불러오기(반응·사진까지), 새 사진 묶음
	public static DmMessageDto from(DmMessage m, String senderNickname, List<ReactionDto> reactions,
			List<MessagePhoto> photos) {
		boolean deleted = m.isDeleted();
		Long convId = m.getConversation().getId();
		String fileUrl = fileUrl(convId, m.getId());
		FileDto file = deleted ? null : FileDto.of(m.getAttachment(), fileUrl);
		String editedAt = (deleted || m.getEditedAt() == null) ? null : Formats.fullTime(m.getEditedAt());
		return new DmMessageDto(m.getId(), convId, m.getSenderId(), senderNickname, deleted ? null : m.getContent(),
				Formats.clock(m.getCreatedAt()), m.getReadAt() != null, deleted, file, Formats.day(m.getCreatedAt()),
				editedAt, deleted ? List.of() : reactions, deleted ? List.of() : FileDto.photos(fileUrl, photos));
	}

	// 사진 묶음 → 화면용 사진 목록 (한 장 삭제 신호에도 씀)
	public static List<FileDto> photoDtos(Long conversationId, Long messageId, List<MessagePhoto> photos) {
		return FileDto.photos(fileUrl(conversationId, messageId), photos);
	}

	// 이 메시지의 파일 주소: /api/dm/3/files/120 (사진 묶음은 뒤에 /photos/사진번호)
	private static String fileUrl(Long conversationId, Long messageId) {
		return "/api/dm/" + conversationId + "/files/" + messageId;
	}
}