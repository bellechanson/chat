package com.green.chat.dto;

import com.green.chat.entity.Attachment;
import com.green.chat.entity.MessagePhoto;

// 화면으로 보내는 첨부 파일 정보 (url로 내려받기 / 사진 미리보기)
//   photoId: 사진 묶음 속 사진이면 그 사진 번호 (한 장만 지울 때 씀), 아니면 null
public record FileDto(String name, long size, boolean image, String url, Long photoId) {

	public static FileDto of(Attachment a, String url) {
		return a == null ? null : new FileDto(a.getName(), a.getSize(), a.isImage(), url, null);
	}

	// 사진 묶음 속 한 장 (url = 메시지 파일 주소 + "/photos/사진번호")
	public static FileDto photo(MessagePhoto p, String messageFileUrl) {
		Attachment a = p.getAttachment();
		return new FileDto(a.getName(), a.getSize(), true, messageFileUrl + "/photos/" + p.getId(), p.getId());
	}
}