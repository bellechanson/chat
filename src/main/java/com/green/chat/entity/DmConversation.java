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

// 두 사람의 1:1 대화방. 항상 작은 id가 user1
@Entity
@Table(name = "dm_conversation", uniqueConstraints = @UniqueConstraint(columnNames = { "user1_id", "user2_id" }))
public class DmConversation {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dm_conversation_seq_gen")
	@SequenceGenerator(name = "dm_conversation_seq_gen", sequenceName = "DM_CONVERSATION_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "user1_id", nullable = false)
	private Long user1Id;

	@Column(name = "user2_id", nullable = false)
	private Long user2Id;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	// 마지막 메시지 시각 (목록 정렬용)
	@Column(nullable = false)
	private LocalDateTime lastMessageAt;

	protected DmConversation() {
	}

	public DmConversation(Long userA, Long userB) {
		this.user1Id = Math.min(userA, userB);
		this.user2Id = Math.max(userA, userB);
		this.createdAt = LocalDateTime.now();
		this.lastMessageAt = this.createdAt;
	}

	public boolean isMember(Long userId) {
		return userId.equals(user1Id) || userId.equals(user2Id);
	}

	public Long partnerOf(Long userId) {
		return userId.equals(user1Id) ? user2Id : user1Id;
	}

	// 새 메시지가 오면 시각 갱신
	public void touch() {
		this.lastMessageAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUser1Id() {
		return user1Id;
	}

	public Long getUser2Id() {
		return user2Id;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getLastMessageAt() {
		return lastMessageAt;
	}
}