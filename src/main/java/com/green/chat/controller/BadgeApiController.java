package com.green.chat.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.green.chat.security.LoginUser;
import com.green.chat.service.DmService;
import com.green.chat.service.MuteService;
import com.green.chat.service.ReadService;

@RestController
public class BadgeApiController {

	public record UnreadSummary(long total) {
	}

	private final ReadService readService;
	private final DmService dmService;
	private final MuteService muteService;

	public BadgeApiController(ReadService readService, DmService dmService, MuteService muteService) {
		this.readService = readService;
		this.dmService = dmService;
		this.muteService = muteService;
	}

	// GET /api/unread → 전체 안 읽은 수
	// GET /api/unread?excludeRoom=3 → 3번 방(지금 보고 있는 방)은 빼고
	// 알림을 꺼 둔 채팅방·1:1 대화는 셈에서 뺌
	@GetMapping("/api/unread")
	public UnreadSummary unread(@RequestParam(required = false) Long excludeRoom,
			@AuthenticationPrincipal LoginUser loginUser) {
		Long me = loginUser.getId();
		Set<Long> mutedRooms = muteService.mutedTargets(me, MuteService.ROOM);
		long rooms = readService.unreadCounts(me).entrySet().stream()
				.filter(e -> !e.getKey().equals(excludeRoom) && !mutedRooms.contains(e.getKey()))
				.mapToLong(Map.Entry::getValue).sum();
		long dm = dmService.totalUnread(me, muteService.mutedTargets(me, MuteService.DM));
		return new UnreadSummary(rooms + dm);
	}
}