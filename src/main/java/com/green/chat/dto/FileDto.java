package com.green.chat.dto;

import com.green.chat.entity.Attachment;

// 화면으로 보내는 첨부 파일 정보 (url로 내려받기 / 사진 미리보기)
public record FileDto(String name, long size, boolean image, String url) {

	public static FileDto of(Attachment a, String url) {
		return a == null ? null : new FileDto(a.getName(), a.getSize(), a.isImage(), url);
	}
}