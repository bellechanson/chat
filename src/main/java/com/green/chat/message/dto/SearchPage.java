package com.green.chat.message.dto;

import java.util.List;

// 검색 결과 한 묶음 (최신순)
//   hasMore    : 더 오래된 결과가 있을 수 있음 → 다음에 before=nextBefore로 이어서
//   nextBefore : 이번에 살펴본 가장 오래된 메시지 번호 (결과가 비어도 이어서 찾을 수 있게)
public record SearchPage(List<SearchHit> hits, boolean hasMore, Long nextBefore) {

	public static final int SIZE = 30; // 한 번에 살펴보는 메시지 수
	public static final int MAX_QUERY = 50; // 검색어 최대 글자 수

	public static SearchPage empty() {
		return new SearchPage(List.of(), false, null);
	}
}