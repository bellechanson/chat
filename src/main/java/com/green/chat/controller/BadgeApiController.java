package com.green.chat.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.green.chat.security.LoginUser;
import com.green.chat.service.DmService;
import com.green.chat.service.ReadService;

@RestController
public class BadgeApiController {

	public record UnreadSummary(long total) {
	}

	private final ReadService readService;
	private final DmService dmService;

	public BadgeApiController(ReadService readService, DmService dmService) {
		this.readService = readService;
		this.dmService = dmService;
	}

	// GET /api/unread → 전체 안 읽은 수
	// GET /api/unread?excludeRoom=3 → 3번 방(지금 보고 있는 방)은 빼고
	@GetMapping("/api/unread")
	public UnreadSummary unread(@RequestParam(required = false) Long excludeRoom,
			@AuthenticationPrincipal LoginUser loginUser) {
		long rooms = readService.unreadCounts(loginUser.getId()).entrySet().stream()
				.filter(e -> !e.getKey().equals(excludeRoom)).mapToLong(Map.Entry::getValue).sum();
		long dm = dmService.totalUnread(loginUser.getId());
		return new UnreadSummary(rooms + dm);
	}
}