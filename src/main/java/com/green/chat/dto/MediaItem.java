package com.green.chat.dto;

import java.time.LocalDateTime;

import com.green.chat.util.Formats;

// 사진 모아보기의 사진 한 장 (채팅방, 1:1 공통)
//   messageId : 이 사진이 있는 메시지 (크게 보기·실시간 삭제 반영용)
//   photo     : 사진 정보 (url 원본, url?thumb=1 미리보기, 묶음 속 사진이면 photoId)
//   date, time: "2026-11-18", "14:30"
public record MediaItem(Long messageId, String sender, String date, String time, FileDto photo) {

	public static MediaItem of(Long messageId, String sender, FileDto photo, LocalDateTime createdAt) {
		return new MediaItem(messageId, sender, Formats.day(createdAt), Formats.clock(createdAt), photo);
	}
}