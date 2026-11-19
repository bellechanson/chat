package com.green.chat.global.security;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

// 접속자가 바뀌면 관리자 화면으로 최신 목록 전송
@Component
public class OnlineBroadcaster {

	private final LoginSessionManager sessionManager;
	private final SimpMessageSendingOperations messagingTemplate;

	public OnlineBroadcaster(LoginSessionManager sessionManager, SimpMessageSendingOperations messagingTemplate) {
		this.sessionManager = sessionManager;
		this.messagingTemplate = messagingTemplate;
	}

	@EventListener
	public void onChanged(OnlineUsersChangedEvent event) {
		messagingTemplate.convertAndSend("/topic/admin/online", sessionManager.onlineUserIds());
	}
}