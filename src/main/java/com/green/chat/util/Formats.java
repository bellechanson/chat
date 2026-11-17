package com.green.chat.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 날짜 표시, 글자 자르기 등 여러 곳에서 같이 쓰는 도구 모음
public final class Formats {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private Formats() {
	}

	// 메시지 시각: "14:30" (날짜는 화면의 날짜 구분선이 보여 줌)
	public static String clock(LocalDateTime t) {
		return t == null ? "" : t.format(TIME);
	}

	// 메시지 날짜: "2026-11-17" (화면에서 날짜가 바뀌는 곳에 구분선을 넣는 기준)
	public static String day(LocalDateTime t) {
		return t == null ? "" : t.toLocalDate().toString();
	}

	// 관리자 화면: "2026-11-16 10:30"
	public static String fullTime(LocalDateTime t) {
		return t == null ? "" : t.format(FULL);
	}

	// 앞뒤 공백 제거 후 최대 글자 수로 자름 (null → "")
	public static String cut(String value, int max) {
		if (value == null)
			return "";
		String v = value.trim();
		return v.length() > max ? v.substring(0, max) : v;
	}

	// 데스크톱 알림 미리보기 (최대 100자)
	public static String preview(String text) {
		return cut(text, 100);
	}

	// 아바타 색 번호 0~5 (이름 첫 글자로 정함 → 화면의 av-0 ~ av-5, JS와 같은 규칙)
	public static int tint(String name) {
		return (name == null || name.isEmpty()) ? 0 : Math.floorMod(name.codePointAt(0), 6);
	}

	// 아바타에 넣을 첫 글자 ("(탈퇴한 사용자)"처럼 괄호로 시작하면 괄호는 건너뜀, JS initialOf와 같은 규칙)
	public static String initial(String name) {
		if (name == null)
			return "?";
		String n = name.startsWith("(") ? name.substring(1) : name;
		return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1));
	}

}