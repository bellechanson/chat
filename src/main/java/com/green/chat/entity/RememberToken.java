package com.green.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 로그인 상태 유지 토큰 (기기 하나당 한 줄)
//   series : 기기를 구분하는 값 (쿠키에 들어 있음, 바뀌지 않음)
//   token  : 자동 로그인할 때마다 새로 바뀌는 값 (훔쳐 쓴 쿠키 감지용)
@Entity
@Table(name = "remember_token")
public class RememberToken {

	@Id
	@Column(length = 64)
	private String series;

	@Column(nullable = false, length = 50)
	private String username;

	@Column(nullable = false, length = 64)
	private String token;

	@Column(nullable = false)
	private LocalDateTime lastUsed;

	protected RememberToken() {
	}

	public RememberToken(String series, String username, String token, LocalDateTime lastUsed) {
		this.series = series;
		this.username = username;
		this.token = token;
		this.lastUsed = lastUsed;
	}

	// 자동 로그인 성공 → 새 토큰으로 교체
	public void update(String token, LocalDateTime lastUsed) {
		this.token = token;
		this.lastUsed = lastUsed;
	}

	public String getSeries() {
		return series;
	}

	public String getUsername() {
		return username;
	}

	public String getToken() {
		return token;
	}

	public LocalDateTime getLastUsed() {
		return lastUsed;
	}
}