package com.green.chat.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.green.chat.security.LoginUser;

import jakarta.annotation.PreDestroy;

// 화면을 켜 두고 있는 사람 = WebSocket 연결이 하나라도 있는 사람
@Component
public class ConnectionTracker {

	// 페이지 이동 때 "끊김 → 재연결" 사이를 나감으로 보지 않기 위한 대기 시간
	private static final long OFFLINE_GRACE_MS = 3000;

	// userId → 그 사람의 WebSocket 세션들 (탭·창 여러 개)
	private final Map<Long, Set<String>> sessionsByUser = new HashMap<>();
	// sessionId → userId (끊길 때 누구인지 찾기용)
	private final Map<String, Long> userBySession = new HashMap<>();

	private final SimpMessageSendingOperations messagingTemplate;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public ConnectionTracker(SimpMessageSendingOperations messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	// WebSocket 연결 완료
	@EventListener
	public void onConnected(SessionConnectedEvent event) {
		LoginUser user = LoginUser.of(event.getUser());
		if (user == null)
			return;

		String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
		boolean becameOnline;
		synchronized (this) {
			Set<String> sessions = sessionsByUser.computeIfAbsent(user.getId(), k -> new HashSet<>());
			becameOnline = sessions.isEmpty(); // 첫 연결이면 "접속"
			sessions.add(sessionId);
			userBySession.put(sessionId, user.getId());
		}
		if (becameOnline)
			broadcast();
	}

	// WebSocket 연결 끊김
	@EventListener
	public void onDisconnected(SessionDisconnectEvent event) {
		Long userId;
		synchronized (this) {
			userId = userBySession.remove(event.getSessionId());
			if (userId == null)
				return; // 이미 처리된 세션
			Set<String> sessions = sessionsByUser.get(userId);
			if (sessions != null)
				sessions.remove(event.getSessionId());
		}

		// 3초 뒤에도 연결이 하나도 없으면 그때 "나감"
		scheduler.schedule(() -> {
			boolean offline;
			synchronized (this) {
				Set<String> sessions = sessionsByUser.get(userId);
				offline = sessions == null || sessions.isEmpty();
				if (offline)
					sessionsByUser.remove(userId);
			}
			if (offline)
				broadcast();
		}, OFFLINE_GRACE_MS, TimeUnit.MILLISECONDS);
	}

	// 지금 화면을 켜 두고 있는 회원 id
	public synchronized Set<Long> onlineUserIds() {
		return sessionsByUser.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	private void broadcast() {
		messagingTemplate.convertAndSend("/topic/online", onlineUserIds());
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}
}