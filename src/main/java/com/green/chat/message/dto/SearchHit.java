package com.green.chat.message.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.green.chat.global.util.Formats;

// 검색 결과 한 줄 (채팅방, 1:1 공통)
//   content  : 메시지 내용 (채팅방은 "<@번호>" 그대로 → 화면이 mentions로 "@이름"으로 그림)
//   mentions : {회원번호: 이름} (1:1은 빈 맵)
//   date, time: "2026-11-18", "14:30"
public record SearchHit(Long id, String sender, String content, Map<Long, String> mentions, String date, String time) {

	public static SearchHit of(Long id, String sender, String content, Map<Long, String> mentions,
			LocalDateTime createdAt) {
		return new SearchHit(id, sender, content, mentions, Formats.day(createdAt), Formats.clock(createdAt));
	}
}