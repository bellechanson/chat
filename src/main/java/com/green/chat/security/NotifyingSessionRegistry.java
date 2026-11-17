package com.green.chat.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.session.SessionRegistryImpl;

public class NotifyingSessionRegistry extends SessionRegistryImpl {

	private final ApplicationEventPublisher publisher;

	public NotifyingSessionRegistry(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	// 로그인 성공 시 호출됨
	@Override
	public void registerNewSession(String sessionId, Object principal) {
		super.registerNewSession(sessionId, principal);
		publisher.publishEvent(new OnlineUsersChangedEvent());
	}

	// 로그아웃, 세션 만료 후 정리, 시간 초과로 세션 종료 시 호출됨
	@Override
	public void removeSessionInformation(String sessionId) {
		super.removeSessionInformation(sessionId);
		publisher.publishEvent(new OnlineUsersChangedEvent());
	}
}