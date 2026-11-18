package com.green.chat.controller;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.DmPage;
import com.green.chat.dto.SearchPage;
import com.green.chat.security.LoginUser;
import com.green.chat.service.DmNotifier;
import com.green.chat.service.DmService;
import com.green.chat.service.FileStorage;

@RestController
public class DmApiController {

	private final DmService dmService;
	private final DmNotifier dmNotifier;
	private final FileStorage fileStorage;

	public DmApiController(DmService dmService, DmNotifier dmNotifier, FileStorage fileStorage) {
		this.dmService = dmService;
		this.dmNotifier = dmNotifier;
		this.fileStorage = fileStorage;
	}

	// GET /api/dm/3/messages → 최신 50개
	// GET /api/dm/3/messages?before=120 → 120번보다 오래된 50개
	@GetMapping("/api/dm/{id}/messages")
	public ResponseEntity<DmPage> messages(@PathVariable Long id, @RequestParam(required = false) Long before,
			@AuthenticationPrincipal LoginUser loginUser) {
		try {
			return ResponseEntity.ok(dmService.messages(id, loginUser.getId(), before));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 남의 대화
		}
	}

	// GET /api/dm/3/search?q=강아지 → 검색 결과 (최신순), &before=120 → 이어서
	@GetMapping("/api/dm/{id}/search")
	public ResponseEntity<SearchPage> search(@PathVariable Long id, @RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) Long before, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			return ResponseEntity.ok(dmService.search(id, loginUser.getId(), q, before));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 남의 대화
		}
	}

	// POST /api/dm/3/files (file) → 파일 메시지 저장 후 나와 상대에게 전송
	@PostMapping("/api/dm/{id}/files")
	public ResponseEntity<Map<String, String>> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal LoginUser loginUser) {
		try {
			DmService.Sent sent = dmService.sendFile(id, loginUser.getId(), loginUser.getNickname(), file);
			dmNotifier.messageSent(loginUser.getUsername(), loginUser.getNickname(), sent);
			return ResponseEntity.ok(Map.of("message", "ok"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	// POST /api/dm/3/photos (files 여러 개) → 사진 묶음 메시지 하나로 저장 후 나와 상대에게 전송
	@PostMapping("/api/dm/{id}/photos")
	public ResponseEntity<Map<String, String>> uploadPhotos(@PathVariable Long id,
			@RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal LoginUser loginUser) {
		try {
			DmService.Sent sent = dmService.sendPhotos(id, loginUser.getId(), loginUser.getNickname(), files);
			dmNotifier.messageSent(loginUser.getUsername(), loginUser.getNickname(), sent);
			return ResponseEntity.ok(Map.of("message", "ok"));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	// GET /api/dm/3/files/120/photos/12 → 사진 묶음 속 한 장 (참여자만), ?thumb=1 이면 작은 미리보기
	@GetMapping("/api/dm/{id}/files/{messageId}/photos/{photoId}")
	public ResponseEntity<Resource> photo(@PathVariable Long id, @PathVariable Long messageId,
			@PathVariable Long photoId, @RequestParam(defaultValue = "false") boolean thumb,
			@AuthenticationPrincipal LoginUser loginUser) {
		return dmService.photo(id, messageId, photoId, loginUser.getId())
				.map(att -> fileStorage.response("dm/" + id, att, thumb)).orElse(ResponseEntity.notFound().build());
	}

	// GET /api/dm/3/files/120 → 파일 내려받기 (참여자만), ?thumb=1 이면 사진은 작은 미리보기
	@GetMapping("/api/dm/{id}/files/{messageId}")
	public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long messageId,
			@RequestParam(defaultValue = "false") boolean thumb, @AuthenticationPrincipal LoginUser loginUser) {
		return dmService.attachment(id, messageId, loginUser.getId())
				.map(att -> fileStorage.response("dm/" + id, att, thumb)).orElse(ResponseEntity.notFound().build());
	}
}