package com.green.chat.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.green.chat.dto.SendRequest;
import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.DeleteRequest;
import com.green.chat.dto.EditRequest;
import com.green.chat.dto.MessageType;
import com.green.chat.dto.TypingRequest;
import com.green.chat.security.LoginUser;
import com.green.chat.service.ChatService;
import com.green.chat.service.PresenceService;
import com.green.chat.service.ReadService;
import com.green.chat.service.RoomNotifier;
import com.green.chat.service.RoomService;
import com.green.chat.util.Formats;

@Controller
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);
	private static final int MAX_CONTENT = 1000;

	public record KickRequest(String target) {
	}

	private final ChatService chatService;
	private final PresenceService presenceService;
	private final RoomService roomService;
	private final ReadService readService;
	private final RoomNotifier notifier;

	public ChatController(ChatService chatService, PresenceService presenceService, RoomService roomService,
			ReadService readService, RoomNotifier notifier) {
		this.chatService = chatService;
		this.presenceService = presenceService;
		this.roomService = roomService;
		this.readService = readService;
		this.notifier = notifier;
	}

	@MessageMapping("/chat/{roomId}/enter")
	public void enter(@DestinationVariable Long roomId, Principal principal, SimpMessageHeaderAccessor accessor) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || !roomService.canJoin(roomId, user.getId()))
			return;

		readService.markRead(roomId, user.getId()); // 들어오면 여기까지 읽음

		String nickname = user.getNickname();
		boolean firstJoin = presenceService.join(roomId, accessor.getSessionId(), nickname);
		if (firstJoin) {
			notifier.toRoom(roomId, ChatMessage.of(MessageType.ENTER, nickname, nickname + "님이 입장했습니다."));
		}
		notifier.presenceChanged(roomId);
	}

	// 방 안에서 메시지를 받을 때마다 읽음 처리
	// (나갈 때만 처리하면, 사이드바로 다른 화면에 가는 순간 아직 안 읽은 것으로 세어짐)
	@MessageMapping("/chat/{roomId}/read")
	public void read(@DestinationVariable Long roomId, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || !roomService.canJoin(roomId, user.getId()))
			return;
		readService.markRead(roomId, user.getId());
	}

	@MessageMapping("/chat/{roomId}/send")
	public void send(@DestinationVariable Long roomId, @Payload SendRequest message, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || !roomService.canJoin(roomId, user.getId()))
			return;

		String content = Formats.cut(message.content(), MAX_CONTENT);
		if (content.isEmpty())
			return;

		ChatMessage saved = chatService.save(roomId, user.getNickname(), content);
		notifier.messageSent(roomId, user.getId(), saved); // 방 안 + 목록 뱃지 + 데스크톱 알림
	}

	// 입력 중 신호 (들어갈 수 있는 방에서만)
	@MessageMapping("/chat/{roomId}/typing")
	public void typing(@DestinationVariable Long roomId, @Payload TypingRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || !roomService.canJoin(roomId, user.getId()))
			return;
		notifier.typing(roomId, user.getNickname(), request.typing());
	}

	// 메시지 삭제: 내 메시지, 또는 방장·관리자는 모든 메시지
	@MessageMapping("/chat/{roomId}/delete")
	public void deleteMessage(@DestinationVariable Long roomId, @Payload DeleteRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null || !roomService.canJoin(roomId, user.getId()))
			return;

		// 방장·관리자 여부 (내 것만 지우게 하려면 이 줄을 false로)
		boolean canManage = roomService.findById(roomId)
				.map(room -> roomService.canManage(room, user.getId(), user.isAdmin())).orElse(false);
		try {
			chatService.delete(roomId, request.id(), user.getNickname(), canManage);
		} catch (IllegalArgumentException e) {
			log.warn("[메시지 삭제 실패] room={}, msg={}, reason={}", roomId, request.id(), e.getMessage());
			return;
		}
		notifier.toRoom(roomId, ChatMessage.deleted(request.id()));
	}

	// 메시지 수정: 내 글 메시지만 → 방 안 모두의 화면에서 내용 교체 + "(수정됨)"
	// 알림·안 읽은 수는 건드리지 않음 (조용히)
	@MessageMapping("/chat/{roomId}/edit")
	public void editMessage(@DestinationVariable Long roomId, @Payload EditRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null || !roomService.canJoin(roomId, user.getId()))
			return;

		String content = Formats.cut(request.content(), MAX_CONTENT);
		try {
			ChatMessage edited = chatService.edit(roomId, request.id(), user.getNickname(), content);
			notifier.toRoom(roomId, edited);
		} catch (IllegalArgumentException e) {
			log.warn("[메시지 수정 실패] room={}, msg={}, reason={}", roomId, request.id(), e.getMessage());
		}
	}

	@MessageMapping("/chat/{roomId}/kick")
	public void kick(@DestinationVariable Long roomId, @Payload KickRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null)
			return;

		String target = Formats.cut(request.target(), 20);
		try {
			roomService.kick(roomId, user.getId(), user.isAdmin(), target);
		} catch (IllegalArgumentException e) {
			log.warn("[강퇴 실패] room={}, by={}, reason={}", roomId, user.getNickname(), e.getMessage());
			return;
		}

		presenceService.kick(roomId, target);
		notifier.toRoom(roomId, ChatMessage.of(MessageType.KICK, target, target + "님이 강퇴되었습니다."));
		notifier.presenceChanged(roomId);
	}
}