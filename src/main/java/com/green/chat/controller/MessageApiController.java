package com.green.chat.controller;

import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessagePage;
import com.green.chat.security.LoginUser;
import com.green.chat.service.ChatService;
import com.green.chat.service.FileStorage;
import com.green.chat.service.RoomNotifier;
import com.green.chat.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
public class MessageApiController {

	private final ChatService chatService;
	private final RoomService roomService;
	private final RoomNotifier notifier;
	private final FileStorage fileStorage;

	public MessageApiController(ChatService chatService, RoomService roomService, RoomNotifier notifier,
			FileStorage fileStorage) {
		this.chatService = chatService;
		this.roomService = roomService;
		this.notifier = notifier;
		this.fileStorage = fileStorage;
	}

	// GET /api/rooms/1/messages → 최신 50개
	// GET /api/rooms/1/messages?before=351 → 351번보다 오래된 50개
	@GetMapping("/{roomId}/messages")
	public ResponseEntity<MessagePage> messages(@PathVariable Long roomId, @RequestParam(required = false) Long before,
			@AuthenticationPrincipal LoginUser loginUser) {
		if (!roomService.canJoin(roomId, loginUser.getId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(chatService.messages(roomId, before));
	}

	// POST /api/rooms/1/files (file) → 파일 메시지 저장 후 방 안 사람들에게 전송
	@PostMapping("/{roomId}/files")
	public ResponseEntity<Map<String, String>> upload(@PathVariable Long roomId,
			@RequestParam("file") MultipartFile file, @AuthenticationPrincipal LoginUser loginUser) {
		if (!roomService.canJoin(roomId, loginUser.getId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "이 방에는 보낼 수 없습니다."));
		}
		try {
			ChatMessage saved = chatService.saveFile(roomId, loginUser.getNickname(), file);
			notifier.messageSent(roomId, loginUser.getId(), saved);
			return ResponseEntity.ok(Map.of("message", "ok"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	// GET /api/rooms/1/files/351 → 파일 내려받기 (이 방에 들어갈 수 있는 사람만)
	@GetMapping("/{roomId}/files/{messageId}")
	public ResponseEntity<Resource> download(@PathVariable Long roomId, @PathVariable Long messageId,
			@AuthenticationPrincipal LoginUser loginUser) {
		if (!roomService.canJoin(roomId, loginUser.getId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return chatService.attachment(roomId, messageId).map(att -> fileStorage.response("rooms/" + roomId, att))
				.orElse(ResponseEntity.notFound().build());
	}
}