package com.green.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessageType;
import com.green.chat.dto.NotifyEvent;
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

	public RoomNotifier(SimpMessageSendingOperations messagingTemplate, PresenceService presenceService,
			RoomService roomService) {
		this.messagingTemplate = messagingTemplate;
		this.presenceService = presenceService;
		this.roomService = roomService;
	}

	// 방 안의 모든 사람에게 (대화, 입장/퇴장, 강퇴, 삭제 알림)
	public void toRoom(Long roomId, ChatMessage message) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
	}

	// 이모지 반응이 바뀜 → 방 안 사람들 (알림·안 읽은 수는 건드리지 않음)
	public void reactions(Long roomId, ReactionEvent event) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reactions", event);
	}

	// 새 메시지(글, 파일) → 방 안 사람들 + 방 목록 안 읽은 수 + 데스크톱 알림
	public void messageSent(Long roomId, Long senderId, ChatMessage message) {
		toRoom(roomId, message);
		activity(roomId, message.sender());
		try {
			notifyMembers(roomId, senderId, message);
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
	public void typing(Long roomId, String nickname, boolean typing) {
		messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing", new RoomTypingEvent(nickname, typing));
	}

	// 새 메시지 → 이 방에 들어와 본 회원들에게 데스크톱 알림 (보낸 사람 제외)
	private void notifyMembers(Long roomId, Long senderId, ChatMessage message) {
		RoomService.NotifyTargets targets = roomService.notifyTargets(roomId, senderId);
		NotifyEvent event = new NotifyEvent(targets.roomName(),
				message.sender() + ": " + Formats.preview(message.content()), "/rooms/" + roomId, "room-" + roomId,
				message.id());
		for (String username : targets.usernames()) {
			messagingTemplate.convertAndSendToUser(username, "/queue/notify", event);
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