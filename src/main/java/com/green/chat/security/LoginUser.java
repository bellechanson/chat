package com.green.chat.security;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.green.chat.entity.ChatUser;

public class LoginUser implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final Long id;
	private final String username;
	private final String password;
	private final String nickname;
	private final boolean admin;
	private final boolean suspended;

	private LoginUser(Long id, String username, String password, String nickname, boolean admin, boolean suspended) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.nickname = nickname;
		this.admin = admin;
		this.suspended = suspended;
	}

	// 회원 엔티티 → 로그인 정보
	public static LoginUser from(ChatUser user) {
		return new LoginUser(user.getId(), user.getUsername(), user.getPassword(), user.getNickname(), user.isAdmin(),
				user.isSuspended());
	}

	// WebSocket 쪽 Principal → 로그인 정보 (로그인 안 됐으면 null)
	public static LoginUser of(Principal principal) {
		if (principal instanceof Authentication auth && auth.getPrincipal() instanceof LoginUser user) {
			return user;
		}
		return null;
	}

	public Long getId() {
		return id;
	}

	public String getNickname() {
		return nickname;
	}

	public boolean isAdmin() {
		return admin;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (admin) {
			return List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	// false면 스프링 시큐리티가 로그인을 막고 LockedException을 냄
	@Override
	public boolean isAccountNonLocked() {
		return !suspended;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}