package com.green.chat.global.websocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.green.chat.global.security.LoginUser;
import com.green.chat.room.service.RoomService;

@Component
public class RoomSubscribeInterceptor implements ChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RoomSubscribeInterceptor.class);

	private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/room/(\\d+)(/.*)?$");

	private final RoomService roomService;

	public RoomSubscribeInterceptor(RoomService roomService) {
		this.roomService = roomService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE)
			return message;

		String destination = accessor.getDestination();
		if (destination == null)
			return message;

		// 개인 메시지 내부 주소 직접 구독 차단
		if (destination.startsWith("/queue/")) {
			log.warn("[구독 차단] 직접 queue 구독: {}", destination);
			return null;
		}

		LoginUser user = LoginUser.of(accessor.getUser());

		// 관리자 전용 채널: 관리자만 구독 가능
		if (destination.startsWith("/topic/admin/")) {
			if (user != null && user.isAdmin())
				return message;
			log.warn("[구독 차단] 관리자 채널: {}, user={}", destination, accessor.getUser());
			return null;
		}

		// 채팅방 채널: 들어갈 수 있는 방만 구독 가능
		Matcher m = ROOM_TOPIC.matcher(destination);
		if (!m.matches())
			return message;

		Long roomId = Long.valueOf(m.group(1));
		if (user != null && roomService.canJoin(roomId, user.getId()))
			return message;

		log.warn("[구독 차단] destination={}, user={}", destination, accessor.getUser());
		return null;
	}
}