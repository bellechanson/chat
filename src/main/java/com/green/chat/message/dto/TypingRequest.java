package com.green.chat.message.dto;

// 입력 중 신호 본문 (DM, 채팅방 공통)
//   { "typing": true }                         → 입력 중
//   { "typing": true, "photos": 12, "files": 0 } → 사진·파일을 올리는 중 (상대 화면에 "사진 12장을 보내는 중")
//   photos·files는 없어도 됨 (Jackson 3: 빠질 수 있는 값은 Integer)
public record TypingRequest(boolean typing, Integer photos, Integer files) {

	// 올리는 개수는 0~30으로만 (이상한 값이 와도 화면에 그대로 나가지 않게)
	public int photoCount() {
		return clamp(photos);
	}

	public int fileCount() {
		return clamp(files);
	}

	private static int clamp(Integer n) {
		return n == null ? 0 : Math.max(0, Math.min(30, n));
	}
}