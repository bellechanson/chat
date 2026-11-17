package com.green.chat.dto;

import com.green.chat.entity.DmMessage;
import com.green.chat.util.Formats;

// 화면으로 보내는 DM 메시지
public record DmMessageDto(Long id, Long conversationId, Long senderId, // 내 메시지인지는 화면에서 내 id와 비교
		String senderNickname, String content, // 삭제된 메시지면 null
		String time, // "14:30"
		boolean read, // 상대가 읽었는지 (false면 "1" 표시)
		boolean deleted, FileDto file, // 첨부 파일 (없거나 삭제됐으면 null)
		String date, // "2026-11-17" (날짜 구분선용)
		String editedAt // 수정했으면 "2026-11-17 14:35" (화면에 "(수정됨)"), 안 했으면 null
) {
	public static DmMessageDto from(DmMessage m, String senderNickname) {
		boolean deleted = m.isDeleted();
		Long convId = m.getConversation().getId();
		FileDto file = deleted ? null : FileDto.of(m.getAttachment(), "/api/dm/" + convId + "/files/" + m.getId());
		String editedAt = (deleted || m.getEditedAt() == null) ? null : Formats.fullTime(m.getEditedAt());
		return new DmMessageDto(m.getId(), convId, m.getSenderId(), senderNickname, deleted ? null : m.getContent(),
				Formats.clock(m.getCreatedAt()), m.getReadAt() != null, deleted, file, Formats.day(m.getCreatedAt()),
				editedAt);
	}
}