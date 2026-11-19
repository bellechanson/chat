package com.green.chat.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MentionEvent;
import com.green.chat.dto.MessageType;
import com.green.chat.dto.NotifyEvent;
import com.green.chat.dto.PinDto;
import com.green.chat.dto.PinEvent;
import com.green.chat.dto.ReactionEvent;
import com.green.chat.dto.RoomActivity;
import com.green.chat.dto.RoomSummary;
import com.green.chat.dto.RoomTypingEvent;
import com.green.chat.entity.ChatRoom;
import com.green.chat.util.Formats;

// 채팅방 관련 실시간 알림을 한곳에서 보냄 (구독 주소 모음)
@Component
public class RoomNotifier {

	private static final Logger log = LoggerFactory.getLogger(RoomNotifier.class);

	private final SimpMessageSendingOperations messagingTemplate;
	private final PresenceService presenceService;
	private final RoomService roomService;
	private final MuteService muteService;
	private final MentionService mentionService;

	public RoomNotifier(SimpMessageSendingOperations messagingTemplate, PresenceService presenceService,
			RoomService roomService, MuteService muteService, MentionService mentionService) {
		this.messagingTemplate = messagingTemplate;
		this.presenceService = presenceService;
		this.roomService = roomService;
		this.muteService = muteService;
		this.mentionService = mentionService;
	}

	// 방 안의 모든 사람에게 (대화, 입장/퇴장, 강퇴, 삭제 알림)
	public void toRoom(Long roomId, ChatMessage message) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
	}

	// 이모지 반응이 바뀜 → 방 안 사람들 (알림·안 읽은 수는 건드리지 않음)
	public void reactions(Long roomId, ReactionEvent event) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reactions", event);
	}

	// 새 메시지(파일, 사진 묶음) → 방 안 사람들 + 방 목록 안 읽은 수 + 데스크톱 알림
	public void messageSent(Long roomId, Long senderId, ChatMessage message) {
		messageSent(roomId, senderId, message, Set.of(), Set.of());
	}

	// 새 글 메시지 → 위와 같고, 부른 사람(mentioned: @멘션, repliedTo: 답장 상대)은 알림을 꺼 뒀어도 알림 + 사이드바
	// "@"
	public void messageSent(Long roomId, Long senderId, ChatMessage message, Set<Long> mentioned, Set<Long> repliedTo) {
		toRoom(roomId, message);
		activity(roomId, message.sender());
		try {
			notifyMembers(roomId, senderId, message, mentioned, repliedTo);
		} catch (Exception e) {
			log.warn("[알림 전송 실패] room={}", roomId, e); // 알림이 실패해도 대화에는 영향 없음
		}
	}

	// 접속자 변경 → 방 안 접속자 목록 + 방 목록 인원수
	public void presenceChanged(Long roomId) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/users", presenceService.users(roomId));
		sendCounts();
	}

	// 새 메시지 → 방 목록의 안 읽은 수, 작업 표시줄 뱃지
	private void activity(Long roomId, String sender) {
		messagingTemplate.convertAndSend("/topic/rooms/activity", new RoomActivity(roomId, sender));
	}

	// 입력 중 신호 → 방 안 사람들에게 (보낸 사람 화면에서는 자기 것을 무시)
	// photos·files: 올리는 중인 사진·파일 개수 (0이면 그냥 입력 중)
	public void typing(Long roomId, String nickname, boolean typing, int photos, int files) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing",
				new RoomTypingEvent(nickname, typing, photos, files));
	}

	// 새 메시지 → 이 방에 들어와 본 회원들에게 데스크톱 알림 (보낸 사람 제외)
	// 이 방 알림을 꺼 둔 사람은 건너뜀, 단 불린 사람(@멘션·답장)은 꺼 뒀어도 "○○님이 나를 불렀어요"로
	private void notifyMembers(Long roomId, Long senderId, ChatMessage message, Set<Long> mentioned,
			Set<Long> repliedTo) {
		RoomService.NotifyTargets targets = roomService.notifyTargets(roomId, senderId);
		Set<String> muted = muteService.mutedUsernames(MuteService.ROOM, roomId);
		String url = "/rooms/" + roomId;
		String tag = "room-" + roomId;
		String body = message.sender() + ": "
				+ Formats.preview(MentionService.plain(message.content(), message.mentions())); // "<@12>" → "@영희"

		// 불린 사람 로그인 아이디 → 알림 제목 (멘션과 답장을 둘 다 받으면 멘션으로)
		Map<String, String> calledTitles = new HashMap<>();
		mentionService.usernames(mentioned).values()
				.forEach(u -> calledTitles.put(u, message.sender() + "님이 나를 불렀어요 · " + targets.roomName()));
		mentionService.usernames(repliedTo).values().forEach(
				u -> calledTitles.putIfAbsent(u, message.sender() + "님이 내 메시지에 답장했어요 · " + targets.roomName()));

		NotifyEvent normal = new NotifyEvent(targets.roomName(), body, url, tag, message.id());
		for (String username : targets.usernames()) {
			String calledTitle = calledTitles.get(username);
			if (calledTitle == null && muted.contains(username))
				continue;
			NotifyEvent event = calledTitle == null ? normal
					: new NotifyEvent(calledTitle, body, url, tag, message.id());
			messagingTemplate.convertAndSendToUser(username, "/queue/notify", event);
			if (calledTitle != null) // 사이드바 그 방에 "@"
				messagingTemplate.convertAndSendToUser(username, "/queue/mention",
						new MentionEvent(roomId, message.id()));
		}
	}

	// 공지가 바뀜 (pin이 null이면 해제) → 방 안 사람들의 공지 띠
	public void pinChanged(Long roomId, PinDto pin) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/pin", new PinEvent(roomId, pin));
	}

	// 새 공지 → 공지 띠 + 이 방에 들어와 본 회원들에게 데스크톱 알림 (고정한 사람, 이 방 알림을 꺼 둔 사람 제외)
	public void pinned(Long roomId, Long byUserId, PinDto pin) {
		pinChanged(roomId, pin);
		try {
			RoomService.NotifyTargets targets = roomService.notifyTargets(roomId, byUserId);
			Set<String> muted = muteService.mutedUsernames(MuteService.ROOM, roomId);
			NotifyEvent event = new NotifyEvent("📌 새 공지 · " + targets.roomName(),
					pin.sender() + ": " + Formats.preview(MentionService.plain(pin.content(), pin.mentions())),
					"/rooms/" + roomId, "room-" + roomId + "-pin", pin.messageId());
			for (String username : targets.usernames()) {
				if (!muted.contains(username))
					messagingTemplate.convertAndSendToUser(username, "/queue/notify", event);
			}
		} catch (Exception e) {
			log.warn("[공지 알림 전송 실패] room={}", roomId, e); // 알림이 실패해도 공지는 그대로
		}
	}

	// 새 방 → 방 목록에 바로 추가
	public void roomCreated(ChatRoom room) {
		messagingTemplate.convertAndSend("/topic/rooms/created", RoomSummary.from(room));
	}

	// 방 삭제: 접속 정보 정리 → 방 안 사람들 내보내기 → 방 목록에서 제거
	public void roomDeleted(Long roomId, String by, String text) {
		presenceService.removeRoom(roomId);
		toRoom(roomId, ChatMessage.of(MessageType.ROOM_DELETED, by, text));
		sendCounts();
		messagingTemplate.convertAndSend("/topic/rooms/deleted", roomId);
	}

	private void sendCounts() {
		messagingTemplate.convertAndSend("/topic/rooms/counts", presenceService.counts());
	}
}