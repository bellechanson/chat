package com.green.chat.dm.dto;

import java.util.List;

// 이전 대화 한 묶음 (오래된 → 최신) + 더 있는지
public record DmPage(List<DmMessageDto> messages, boolean hasMore) {
}