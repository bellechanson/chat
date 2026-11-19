package com.green.chat.pin.dto;

import java.util.Map;

// 방 맨 위 공지 띠 (채팅방, 1:1 공통)
//   messageId : 고정된 메시지 번호 (누르면 그 메시지로 이동)
//   sender    : 메시지를 보낸 사람 이름
//   content   : 내용 (채팅방은 "<@번호>"가 그대로 + mentions로 이름, 사진·파일 메시지는 "사진" / "파일: 이름")
//   mentions  : 내용 속 "<@번호>"를 그릴 이름 {회원번호: 이름} (1:1은 빈 맵)
//   date, time: 메시지를 보낸 날짜 "2026-11-19" · 시각 "09:30"
//   pinnedBy  : 고정한 사람 이름
public record PinDto(Long messageId, String sender, String content, Map<Long, String> mentions, String date,
		String time, String pinnedBy) {
}