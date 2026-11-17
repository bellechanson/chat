package com.green.chat.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// "로그인 상태 유지"로 자동 로그인했을 때도 로그인 장부에 기록
// (관리자 화면의 "로그인 중", 강제 로그아웃이 똑같이 동작하도록)
@Component
public class RememberMeSessionRegistrar {

	private final SessionRegistry sessionRegistry;

	public RememberMeSessionRegistrar(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	@EventListener
	public void onAutoLogin(InteractiveAuthenticationSuccessEvent event) {
		if (!(event.getAuthentication() instanceof RememberMeAuthenticationToken auth))
			return;
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs))
			return;

		String sessionId = attrs.getRequest().getSession(true).getId();
		if (sessionRegistry.getSessionInformation(sessionId) == null) { // 이미 기록돼 있으면 그대로
			sessionRegistry.registerNewSession(sessionId, auth.getPrincipal());
		}
	}
}