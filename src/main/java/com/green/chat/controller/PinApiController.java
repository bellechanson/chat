package com.green.chat.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.green.chat.dto.PinDto;
import com.green.chat.dto.PinEvent;
import com.green.chat.dto.PinRequest;
import com.green.chat.security.LoginUser;
import com.green.chat.service.DmNotifier;
import com.green.chat.service.PinService;
import com.green.chat.service.RoomNotifier;

// 공지(고정 메시지) — 응답은 모두 PinEvent { targetId, pin } (pin이 null이면 공지 없음)
//   채팅방: 방장·관리자만 고정·해제, 바뀌면 /topic/room/{id}/pin
//   1:1   : 둘 중 누구나, 바뀌면 둘 다에게 /user/queue/dm-pin
@RestController
public class PinApiController {

	private final PinService pinService;
	private final RoomNotifier roomNotifier;
	private final DmNotifier dmNotifier;

	public PinApiController(PinService pinService, RoomNotifier roomNotifier, DmNotifier dmNotifier) {
		this.pinService = pinService;
		this.roomNotifier = roomNotifier;
		this.dmNotifier = dmNotifier;
	}

	// GET /api/rooms/3/pin → 3번 채팅방의 지금 공지
	@GetMapping("/api/rooms/{id}/pin")
	public ResponseEntity<?> roomPin(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			return ResponseEntity.ok(new PinEvent(id, pinService.roomPin(id, loginUser.getId()).orElse(null)));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	// POST /api/rooms/3/pin { "messageId": 351 } → 고정 (방 사람들에게 공지 띠 + 알림)
	@PostMapping("/api/rooms/{id}/pin")
	public ResponseEntity<?> pinRoom(@PathVariable Long id, @RequestBody PinRequest request,
			@AuthenticationPrincipal LoginUser loginUser) {
		try {
			PinDto pin = pinService.pinRoom(id, request.messageId(), loginUser.getId(), loginUser.isAdmin());
			roomNotifier.pinned(id, loginUser.getId(), pin);
			return ResponseEntity.ok(new PinEvent(id, pin));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	// DELETE /api/rooms/3/pin → 해제
	@DeleteMapping("/api/rooms/{id}/pin")
	public ResponseEntity<?> unpinRoom(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			pinService.unpinRoom(id, loginUser.getId(), loginUser.isAdmin());
			roomNotifier.pinChanged(id, null);
			return ResponseEntity.ok(new PinEvent(id, null));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	// GET /api/dm/3/pin → 3번 1:1 대화의 지금 공지
	@GetMapping("/api/dm/{id}/pin")
	public ResponseEntity<?> dmPin(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			return ResponseEntity.ok(new PinEvent(id, pinService.dmPin(id, loginUser.getId()).orElse(null)));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	// POST /api/dm/3/pin { "messageId": 120 } → 고정 (나·상대 공지 띠 + 상대에게 알림)
	@PostMapping("/api/dm/{id}/pin")
	public ResponseEntity<?> pinDm(@PathVariable Long id, @RequestBody PinRequest request,
			@AuthenticationPrincipal LoginUser loginUser) {
		try {
			PinService.DmPinned pinned = pinService.pinDm(id, request.messageId(), loginUser.getId());
			dmNotifier.pinChanged(id, loginUser.getUsername(), loginUser.getNickname(), pinned.partnerUsername(),
					pinned.pin());
			return ResponseEntity.ok(new PinEvent(id, pinned.pin()));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	// DELETE /api/dm/3/pin → 해제
	@DeleteMapping("/api/dm/{id}/pin")
	public ResponseEntity<?> unpinDm(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			String partner = pinService.unpinDm(id, loginUser.getId());
			dmNotifier.pinChanged(id, loginUser.getUsername(), loginUser.getNickname(), partner, null);
			return ResponseEntity.ok(new PinEvent(id, null));
		} catch (IllegalArgumentException e) {
			return badRequest(e);
		}
	}

	private static ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	}
}