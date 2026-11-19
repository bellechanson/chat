package com.green.chat.user.entity;

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

	// 탈퇴 시각 (탈퇴 안 했으면 null). 메시지 기록 때문에 행은 지우지 않고 개인정보만 지움
	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;

	// 화면에 보이는 탈퇴 회원 이름 (닉네임으로는 쓸 수 없게 막아 둠)
	public static final String WITHDRAWN_NAME = "(탈퇴한 사용자)";

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

	// 회원 탈퇴: 아이디·닉네임·이메일을 비워 다시 가입할 수 있게 하고, 로그인은 못 하게 함
	// username "_wd12" : 가입 규칙(영문/숫자)에 맞지 않아 다른 사람이 같은 아이디를 만들 수 없음
	// nickname "탈퇴#12" : 닉네임에 #을 못 쓰게 막아 둬서 겹치지 않음 (화면에는 WITHDRAWN_NAME으로 보임)
	public void withdraw(String encodedRandomPassword) {
		this.username = "_wd" + id;
		this.nickname = "탈퇴#" + id;
		this.email = "wd" + id + "@withdrawn.local";
		this.password = encodedRandomPassword;
		this.role = Role.USER;
		this.suspended = true;
		this.withdrawnAt = LocalDateTime.now();
	}

	public boolean isWithdrawn() {
		return withdrawnAt != null;
	}

	// 다른 사람에게 보이는 이름 (탈퇴했으면 "(탈퇴한 사용자)")
	public String getDisplayName() {
		return isWithdrawn() ? WITHDRAWN_NAME : nickname;
	}

	public LocalDateTime getWithdrawnAt() {
		return withdrawnAt;
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