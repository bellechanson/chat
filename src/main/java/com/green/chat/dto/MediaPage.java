package com.green.chat.dto;

import java.util.List;

// 사진 모아보기 한 묶음 (최신순 — 묶음 속 사진은 묶음 안 순서대로)
//   hasMore    : 더 오래된 사진이 있음 → 다음에 before=nextBefore로 이어서
//   nextBefore : 이번에 살펴본 가장 오래된 메시지 번호
//   total      : 이 대화의 전체 사진 수 (처음 불러올 때만, 이어서 불러올 때는 null)
public record MediaPage(List<MediaItem> items, boolean hasMore, Long nextBefore, Long total) {

	public static final int SIZE = 20; // 한 번에 살펴보는 메시지 수 (묶음이면 사진은 더 많음)
}