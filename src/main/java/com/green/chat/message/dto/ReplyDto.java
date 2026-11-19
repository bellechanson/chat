package com.green.chat.message.dto;

// 답장 메시지 위에 작게 보이는 인용 (채팅방, 1:1 공통)
//   id      : 원래 메시지 번호 (누르면 그 메시지로 이동)
//   sender  : 원래 메시지를 보낸 사람 이름
//   preview : 원래 내용 앞부분 (멘션은 "@이름"으로, 사진은 "사진", 파일은 "파일: 이름"), 삭제됐으면 null
//   deleted : 원래 메시지가 삭제됐으면 true → 화면에 "삭제된 메시지입니다"
public record ReplyDto(Long id, String sender, String preview, boolean deleted) {

	private static final int PREVIEW_MAX = 80;

	public static ReplyDto of(Long id, String sender, String plainContent, boolean deleted) {
		if (deleted || plainContent == null)
			return new ReplyDto(id, sender, null, true);
		String text = plainContent.replaceAll("\\s+", " ").trim();
		return new ReplyDto(id, sender, text.length() > PREVIEW_MAX ? text.substring(0, PREVIEW_MAX) + "…" : text,
				false);
	}
}