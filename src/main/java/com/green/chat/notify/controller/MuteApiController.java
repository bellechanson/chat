package com.green.chat.notify.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.green.chat.global.security.LoginUser;
import com.green.chat.notify.dto.MuteRequest;
import com.green.chat.notify.dto.MuteState;
import com.green.chat.notify.service.MuteService;

// 방별 알림 끄기 (채팅방, 1:1 대화)
@RestController
public class MuteApiController {

	private final MuteService muteService;
	private final SimpMessageSendingOperations messagingTemplate;

	public MuteApiController(MuteService muteService, SimpMessageSendingOperations messagingTemplate) {
		this.muteService = muteService;
		this.messagingTemplate = messagingTemplate;
	}

	// GET /api/mutes → 내가 지금 꺼 둔 방 전부
	@GetMapping("/api/mutes")
	public List<MuteState> mine(@AuthenticationPrincipal LoginUser loginUser) {
		return muteService.mine(loginUser.getId());
	}

	// POST /api/rooms/3/mute { "duration": "1h" } → 3번 채팅방 알림 끄기/켜기
	@PostMapping("/api/rooms/{id}/mute")
	public ResponseEntity<?> room(@PathVariable Long id, @RequestBody MuteRequest request,
			@AuthenticationPrincipal LoginUser loginUser) {
		return set(MuteService.ROOM, id, request, loginUser);
	}

	// POST /api/dm/3/mute { "duration": "forever" } → 3번 1:1 대화 알림 끄기/켜기
	@PostMapping("/api/dm/{id}/mute")
	public ResponseEntity<?> dm(@PathVariable Long id, @RequestBody MuteRequest request,
			@AuthenticationPrincipal LoginUser loginUser) {
		return set(MuteService.DM, id, request, loginUser);
	}

	// 저장 → 내 다른 탭·기기에도 바로 알림 (종 모양, 뱃지 다시 계산)
	private ResponseEntity<?> set(String scope, Long id, MuteRequest request, LoginUser loginUser) {
		try {
			MuteState state = muteService.set(scope, id, loginUser.getId(), request.duration());
			messagingTemplate.convertAndSendToUser(loginUser.getUsername(), "/queue/mute", state);
			return ResponseEntity.ok(state);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
}