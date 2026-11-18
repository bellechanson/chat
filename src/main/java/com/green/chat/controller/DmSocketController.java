package com.green.chat.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import com.green.chat.dto.DeletePhotoRequest;
import com.green.chat.dto.DeleteRequest;
import com.green.chat.dto.DmDeletedEvent;
import com.green.chat.dto.EditRequest;
import com.green.chat.dto.ReactRequest;
import com.green.chat.dto.DmReadEvent;
import com.green.chat.dto.DmTypingEvent;
import com.green.chat.dto.SendRequest;
import com.green.chat.dto.TypingRequest;
import com.green.chat.security.LoginUser;
import com.green.chat.service.DmNotifier;
import com.green.chat.service.DmService;

@Controller
public class DmSocketController {

	private static final Logger log = LoggerFactory.getLogger(DmSocketController.class);

	private final DmService dmService;
	private final DmNotifier dmNotifier;
	private final SimpMessageSendingOperations messagingTemplate;

	public DmSocketController(DmService dmService, DmNotifier dmNotifier,
			SimpMessageSendingOperations messagingTemplate) {
		this.dmService = dmService;
		this.dmNotifier = dmNotifier;
		this.messagingTemplate = messagingTemplate;
	}

	// /app/dm/{id}/send
	@MessageMapping("/dm/{conversationId}/send")
	public void send(@DestinationVariable Long conversationId, @Payload SendRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null)
			return;

		try {
			DmService.Sent sent = dmService.send(conversationId, user.getId(), user.getNickname(), request.content(),
					request.replyTo());
			dmNotifier.messageSent(principal.getName(), user.getNickname(), sent);
		} catch (IllegalArgumentException e) {
			log.warn("[DM 전송 실패] conv={}, user={}, reason={}", conversationId, user.getId(), e.getMessage());
		}
	}

	// /app/dm/{id}/read : 대화 화면을 보고 있다는 신호
	@MessageMapping("/dm/{conversationId}/read")
	public void read(@DestinationVariable Long conversationId, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null)
			return;

		dmService.markRead(conversationId, user.getId()).ifPresent(partnerUsername -> {
			DmReadEvent event = new DmReadEvent(conversationId, user.getId());
			messagingTemplate.convertAndSendToUser(partnerUsername, "/queue/dm-read", event); // 상대: "1" 지우기
			messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/dm-read", event); // 나: 목록 뱃지 지우기
		});
	}

	// /app/dm/{id}/typing : 입력 중 신호를 상대에게만 전달 (참여자만) — 사진·파일을 올리는 중이면 개수도 함께
	@MessageMapping("/dm/{conversationId}/typing")
	public void typing(@DestinationVariable Long conversationId, @Payload TypingRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null)
			return;

		dmService.partnerUsername(conversationId, user.getId())
				.ifPresent(partner -> messagingTemplate.convertAndSendToUser(partner, "/queue/dm-typing",
						new DmTypingEvent(conversationId, user.getNickname(), request.typing(), request.photoCount(),
								request.fileCount())));
	}

	// /app/dm/{id}/delete : 내 메시지 삭제 → 나(다른 탭 포함)와 상대 화면에 반영
	@MessageMapping("/dm/{conversationId}/delete")
	public void deleteMessage(@DestinationVariable Long conversationId, @Payload DeleteRequest request,
			Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null)
			return;

		try {
			String partner = dmService.delete(conversationId, request.id(), user.getId());
			toBoth(principal.getName(), partner, "/queue/dm-deleted", new DmDeletedEvent(conversationId, request.id()));
		} catch (IllegalArgumentException e) {
			log.warn("[DM 삭제 실패] conv={}, msg={}, reason={}", conversationId, request.id(), e.getMessage());
		}
	}

	// /app/dm/{id}/delete-photo : 내 사진 묶음에서 한 장만 삭제 → 나(다른 탭 포함)와 상대 화면에서 그 사진만 빠짐
	// 마지막 한 장이었으면 메시지 삭제와 같은 신호 ("삭제된 메시지입니다")
	@MessageMapping("/dm/{conversationId}/delete-photo")
	public void deletePhoto(@DestinationVariable Long conversationId, @Payload DeletePhotoRequest request,
			Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null)
			return;

		try {
			DmService.PhotoDeleted result = dmService.deletePhoto(conversationId, request.id(), request.photoId(),
					user.getId());
			if (result.allDeleted()) {
				toBoth(principal.getName(), result.partnerUsername(), "/queue/dm-deleted",
						new DmDeletedEvent(conversationId, request.id()));
			} else {
				toBoth(principal.getName(), result.partnerUsername(), "/queue/dm-photos", result.event());
			}
		} catch (IllegalArgumentException e) {
			log.warn("[DM 사진 삭제 실패] conv={}, msg={}, photo={}, reason={}", conversationId, request.id(),
					request.photoId(), e.getMessage());
		}
	}

	// /app/dm/{id}/edit : 내 메시지 수정 → 나(다른 탭 포함)와 상대 화면에 반영
	// 알림·안 읽은 수는 건드리지 않음 (조용히), 마지막 메시지면 사이드바 미리보기도 바뀜
	@MessageMapping("/dm/{conversationId}/edit")
	public void editMessage(@DestinationVariable Long conversationId, @Payload EditRequest request,
			Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null)
			return;

		try {
			DmService.Edited edited = dmService.edit(conversationId, request.id(), user.getId(), request.content());
			toBoth(principal.getName(), edited.partnerUsername(), "/queue/dm-edited", edited.event());
		} catch (IllegalArgumentException e) {
			log.warn("[DM 수정 실패] conv={}, msg={}, reason={}", conversationId, request.id(), e.getMessage());
		}
	}

	// /app/dm/{id}/react : 이모지 반응 → 나(다른 탭 포함)와 상대 화면에 반영 (알림·안 읽은 수는 그대로)
	@MessageMapping("/dm/{conversationId}/react")
	public void react(@DestinationVariable Long conversationId, @Payload ReactRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null)
			return;

		try {
			DmService.Reacted reacted = dmService.react(conversationId, request.id(), user.getId(), request.emoji());
			toBoth(principal.getName(), reacted.partnerUsername(), "/queue/dm-reactions", reacted.event());
		} catch (IllegalArgumentException e) {
			log.warn("[DM 반응 실패] conv={}, msg={}, reason={}", conversationId, request.id(), e.getMessage());
		}
	}

	// 나(다른 탭 포함)와 상대 화면 모두에 보내기 (상대가 DB에서 지워진 회원이면 나에게만)
	private void toBoth(String myUsername, String partnerUsername, String destination, Object payload) {
		messagingTemplate.convertAndSendToUser(myUsername, destination, payload);
		if (partnerUsername != null) {
			messagingTemplate.convertAndSendToUser(partnerUsername, destination, payload);
		}
	}
}