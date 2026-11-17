package com.green.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.green.chat.dto.ChatMessage;
import com.green.chat.dto.MessageType;
import com.green.chat.security.LoginUser;
import com.green.chat.service.PresenceService;
import com.green.chat.service.ReadService;
import com.green.chat.service.RoomNotifier;

@Component
public class WebSocketEventListener {

	private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

	private final PresenceService presenceService;
	private final ReadService readService;
	private final RoomNotifier notifier;

	public WebSocketEventListener(PresenceService presenceService, ReadService readService, RoomNotifier notifier) {
		this.presenceService = presenceService;
		this.readService = readService;
		this.notifier = notifier;
	}

	@EventListener
	public void handleDisconnect(SessionDisconnectEvent event) {
		PresenceService.Leave leave = presenceService.leave(event.getSessionId());
		if (leave == null)
			return; // 방에 있던 연결이 아님 (방 목록 화면, 강퇴, 삭제된 방 등)

		// 나가는 시점까지 읽음 처리
		LoginUser user = LoginUser.of(event.getUser());
		if (user != null) {
			try {
				readService.markRead(leave.roomId(), user.getId());
			} catch (Exception e) {
				log.warn("[읽음 처리 실패] room={}, user={}", leave.roomId(), user.getId(), e);
			}
		}

		log.info("[퇴장] room={}, nickname={}, lastSession={}", leave.roomId(), leave.nickname(), leave.lastSession());

		if (leave.lastSession()) {
			notifier.toRoom(leave.roomId(),
					ChatMessage.of(MessageType.LEAVE, leave.nickname(), leave.nickname() + "님이 퇴장했습니다."));
		}
		notifier.presenceChanged(leave.roomId());
	}
}