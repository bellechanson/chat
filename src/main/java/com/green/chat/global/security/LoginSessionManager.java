package com.green.chat.global.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

// 로그인 세션 장부(SessionRegistry)를 이용한 관리 기능
@Component
public class LoginSessionManager {

	private final SessionRegistry sessionRegistry;

	public LoginSessionManager(SessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	// 이 회원의 모든 로그인 세션 만료 → 다음 요청 때 로그인 화면으로
	public int expireUser(Long userId) {
		int count = 0;
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (principal instanceof LoginUser user && user.getId().equals(userId)) {
				for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
					session.expireNow();
					count++;
				}
			}
		}
		return count;
	}

	// 지금 로그인 중인 회원 id 목록
	public Set<Long> onlineUserIds() {
		return sessionRegistry.getAllPrincipals().stream().filter(p -> p instanceof LoginUser)
				.filter(p -> !sessionRegistry.getAllSessions(p, false).isEmpty()).map(p -> ((LoginUser) p).getId())
				.collect(Collectors.toSet());
	}
}