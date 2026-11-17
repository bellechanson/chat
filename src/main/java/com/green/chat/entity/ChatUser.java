package com.green.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.type.NumericBooleanConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "chat_user")
public class ChatUser {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_user_seq_gen")
	@SequenceGenerator(name = "chat_user_seq_gen", sequenceName = "CHAT_USER_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String username;

	@Column(nullable = false, length = 100)
	private String password;

	@Column(nullable = false, unique = true, length = 20)
	private String nickname;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	// 권한. 기존 회원은 null → 일반 사용자로 취급
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Role role;

	// 정지 여부 (오라클 11g에 boolean이 없어서 1/0 숫자로 저장)
	@Convert(converter = NumericBooleanConverter.class)
	@Column(name = "suspended")
	private Boolean suspended;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected ChatUser() {
	}

	public ChatUser(String username, String password, String nickname, String email) {
		this.username = username;
		this.password = password;
		this.nickname = nickname;
		this.email = email;
		this.role = Role.USER;
		this.suspended = false;
		this.createdAt = LocalDateTime.now();
	}

	// 비밀번호 변경 (암호화된 값을 받음)
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	// 닉네임 변경
	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public boolean isSuspended() {
		return Boolean.TRUE.equals(suspended);
	}

	public void changeRole(Role role) {
		this.role = role;
	}

	public void suspend() {
		this.suspended = true;
	}

	public void unsuspend() {
		this.suspended = false;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	public Role getRole() {
		return role;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getNickname() {
		return nickname;
	}

	public String getEmail() {
		return email;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}