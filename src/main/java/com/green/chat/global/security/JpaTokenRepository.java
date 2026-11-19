package com.green.chat.global.security;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.user.entity.RememberToken;
import com.green.chat.user.repository.RememberTokenRepository;

// 스프링 시큐리티의 "로그인 상태 유지" 토큰을 우리 DB(JPA)에 저장
@Component
@Transactional
public class JpaTokenRepository implements PersistentTokenRepository {

	private final RememberTokenRepository repository;

	public JpaTokenRepository(RememberTokenRepository repository) {
		this.repository = repository;
	}

	// 로그인 유지 체크하고 로그인 → 새 토큰 저장
	@Override
	public void createNewToken(PersistentRememberMeToken token) {
		repository.save(new RememberToken(token.getSeries(), token.getUsername(), token.getTokenValue(),
				toLocal(token.getDate())));
	}

	// 자동 로그인 성공 → 토큰 교체
	@Override
	public void updateToken(String series, String tokenValue, Date lastUsed) {
		repository.findById(series).ifPresent(t -> t.update(tokenValue, toLocal(lastUsed)));
	}

	@Override
	@Transactional(readOnly = true)
	public PersistentRememberMeToken getTokenForSeries(String seriesId) {
		return repository.findById(seriesId).map(t -> new PersistentRememberMeToken(t.getUsername(), t.getSeries(),
				t.getToken(), Date.from(t.getLastUsed().atZone(ZoneId.systemDefault()).toInstant()))).orElse(null);
	}

	// 로그아웃, 강제 로그아웃 등 → 이 회원의 모든 기기 토큰 삭제
	@Override
	public void removeUserTokens(String username) {
		repository.deleteByUsername(username);
	}

	private LocalDateTime toLocal(Date date) {
		return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
	}
}