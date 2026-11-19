package com.green.chat.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification")
public class EmailVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "email_verification_seq_gen")
	@SequenceGenerator(name = "email_verification_seq_gen", sequenceName = "EMAIL_VERIFICATION_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, length = 100)
	private String email;

	@Enumerated(EnumType.STRING) // DB에 "SIGNUP" 같은 문자열로 저장
	@Column(nullable = false, length = 20)
	private VerificationPurpose purpose;

	@Column(nullable = false, length = 6)
	private String code;

	@Column(nullable = false)
	private int attempts; // 틀린 횟수

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	protected EmailVerification() {
	}

	public EmailVerification(String email, VerificationPurpose purpose, String code, int expireMinutes) {
		this.email = email;
		this.purpose = purpose;
		this.code = code;
		this.attempts = 0;
		this.createdAt = LocalDateTime.now();
		this.expiresAt = this.createdAt.plusMinutes(expireMinutes);
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	public boolean matches(String input) {
		return code.equals(input);
	}

	public void increaseAttempts() {
		this.attempts++;
	}

	public int getAttempts() {
		return attempts;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}