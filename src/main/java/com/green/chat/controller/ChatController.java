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
import com.green.chat.dto.DeletePhotoRequest;
import com.green.chat.dto.DeleteRequest;
import com.green.chat.dto.EditRequest;
import com.green.chat.dto.MessageType;
import com.green.chat.dto.ReactRequest;
import com.green.chat.dto.ReactionEvent;
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

		// "@닉네임"은 멘션으로, replyTo가 있으면 답장으로 저장
		ChatService.Sent sent = chatService.save(roomId, user.getId(), user.getNickname(), content, message.replyTo());
		// 방 안 + 목록 뱃지 + 데스크톱 알림 (부른 사람은 알림을 꺼 뒀어도)
		notifier.messageSent(roomId, user.getId(), sent.message(), sent.mentioned(), sent.repliedTo());
	}

	// 입력 중 신호 (들어갈 수 있는 방에서만) — 사진·파일을 올리는 중이면 개수도 함께
	@MessageMapping("/chat/{roomId}/typing")
	public void typing(@DestinationVariable Long roomId, @Payload TypingRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || !roomService.canJoin(roomId, user.getId()))
			return;
		notifier.typing(roomId, user.getNickname(), request.typing(), request.photoCount(), request.fileCount());
	}

	// 메시지 삭제: 내 메시지, 또는 방장·관리자는 모든 메시지
	@MessageMapping("/chat/{roomId}/delete")
	public void deleteMessage(@DestinationVariable Long roomId, @Payload DeleteRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null || !roomService.canJoin(roomId, user.getId()))
			return;

		try {
			chatService.delete(roomId, request.id(), user.getNickname(), canManage(roomId, user));
		} catch (IllegalArgumentException e) {
			log.warn("[메시지 삭제 실패] room={}, msg={}, reason={}", roomId, request.id(), e.getMessage());
			return;
		}
		notifier.toRoom(roomId, ChatMessage.deleted(request.id()));
	}

	// 사진 묶음에서 한 장만 삭제: 지울 수 있는 사람은 메시지 삭제와 같음
	// → 방 안 모두의 화면에서 그 사진만 빠짐 (마지막 한 장이면 "삭제된 메시지입니다")
	@MessageMapping("/chat/{roomId}/delete-photo")
	public void deletePhoto(@DestinationVariable Long roomId, @Payload DeletePhotoRequest request,
			Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null || !roomService.canJoin(roomId, user.getId()))
			return;

		try {
			ChatMessage changed = chatService.deletePhoto(roomId, request.id(), request.photoId(), user.getNickname(),
					canManage(roomId, user));
			notifier.toRoom(roomId, changed);
		} catch (IllegalArgumentException e) {
			log.warn("[사진 삭제 실패] room={}, msg={}, photo={}, reason={}", roomId, request.id(), request.photoId(),
					e.getMessage());
		}
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
			ChatMessage edited = chatService.edit(roomId, request.id(), user.getId(), user.getNickname(), content);
			notifier.toRoom(roomId, edited);
		} catch (IllegalArgumentException e) {
			log.warn("[메시지 수정 실패] room={}, msg={}, reason={}", roomId, request.id(), e.getMessage());
		}
	}

	// 이모지 반응: 같은 걸 다시 누르면 취소, 다른 걸 누르면 바뀜 → 방 안 모두의 화면에 반영 (조용히)
	@MessageMapping("/chat/{roomId}/react")
	public void react(@DestinationVariable Long roomId, @Payload ReactRequest request, Principal principal) {
		LoginUser user = LoginUser.of(principal);
		if (user == null || request == null || request.id() == null || !roomService.canJoin(roomId, user.getId()))
			return;

		try {
			ReactionEvent event = chatService.react(roomId, request.id(), user.getId(), request.emoji());
			notifier.reactions(roomId, event);
		} catch (IllegalArgumentException e) {
			log.warn("[반응 실패] room={}, msg={}, reason={}", roomId, request.id(), e.getMessage());
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

	// 방장·관리자인지 (남의 메시지·사진도 지울 수 있음) — 내 것만 지우게 하려면 false를 돌려주면 됨
	private boolean canManage(Long roomId, LoginUser user) {
		return roomService.findById(roomId).map(room -> roomService.canManage(room, user.getId(), user.isAdmin()))
				.orElse(false);
	}
}