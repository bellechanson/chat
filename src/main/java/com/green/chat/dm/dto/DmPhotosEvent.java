package com.green.chat.dm.dto;

import java.util.List;

import com.green.chat.message.dto.FileDto;

// DM 사진 묶음에서 한 장이 지워짐 → 나와 상대 화면에서 남은 사진으로 다시 그리기
//   (다 지워지면 이 신호 대신 DmDeletedEvent → "삭제된 메시지입니다")
public record DmPhotosEvent(Long conversationId, Long messageId, List<FileDto> photos) {
}