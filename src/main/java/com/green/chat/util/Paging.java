package com.green.chat.util;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// 번호 기준 "이어서 불러오기" 공통 (메시지 검색, 사진 모아보기)
//   1) 쿼리에 before(beforeId) 와 plusOne(size) 를 넘겨 size+1개를 가져옴
//   2) cut(rows, size, id) → 앞의 size개 + 더 있는지 + 다음에 이어서 볼 기준 번호
public final class Paging {

	private Paging() {
	}

	// 이어서 볼 기준 번호 (null이면 처음부터 → 가장 큰 번호)
	public static long before(Long beforeId) {
		return beforeId == null ? Long.MAX_VALUE : beforeId;
	}

	// 하나 더 가져와서 다음 쪽이 있는지 봄
	public static Pageable plusOne(int size) {
		return PageRequest.of(0, size + 1);
	}

	// rows: 최신순으로 size+1개까지 가져온 것
	public static <T> Chunk<T> cut(List<T> rows, int size, Function<T, Long> id) {
		boolean hasMore = rows.size() > size;
		List<T> page = hasMore ? rows.subList(0, size) : rows;
		Long nextBefore = page.isEmpty() ? null : id.apply(page.get(page.size() - 1));
		return new Chunk<>(page, hasMore, nextBefore);
	}

	// page: 이번 쪽 / hasMore: 더 오래된 것이 있음 / nextBefore: 이번에 살펴본 가장 오래된 번호
	public record Chunk<T>(List<T> page, boolean hasMore, Long nextBefore) {
	}

}