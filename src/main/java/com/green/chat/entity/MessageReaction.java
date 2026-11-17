package com.green.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 메시지 이모지 반응: 한 사람은 한 메시지에 반응 하나 (scope + message_id + user_id가 겹치지 않음)
//   scope: "ROOM"(채팅방 메시지) / "DM"(1:1 메시지) — 두 메시지 테이블의 번호가 겹칠 수 있어서 구분
//   emoji: 이모지 글자 대신 이름("like", "heart" …)으로 저장 → 오라클 글자 설정과 상관없이 안전
@Entity
@Table(name = "message_reaction", uniqueConstraints = @UniqueConstraint(columnNames = { "scope", "message_id",
		"user_id" }))
public class MessageReaction {

	public static final String ROOM = "ROOM";
	public static final String DM = "DM";

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_reaction_seq_gen")
	@SequenceGenerator(name = "message_reaction_seq_gen", sequenceName = "MESSAGE_REACTION_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, length = 4)
	private String scope;

	@Column(name = "message_id", nullable = false)
	private Long messageId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 10)
	private String emoji;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected MessageReaction() {
	}

	public MessageReaction(String scope, Long messageId, Long userId, String emoji) {
		this.scope = scope;
		this.messageId = messageId;
		this.userId = userId;
		this.emoji = emoji;
		this.createdAt = LocalDateTime.now();
	}

	// 다른 이모지로 바꾸기 (한 사람당 하나라서 새로 만들지 않고 바꿈)
	public void change(String emoji) {
		this.emoji = emoji;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getScope() {
		return scope;
	}

	public Long getMessageId() {
		return messageId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEmoji() {
		return emoji;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}