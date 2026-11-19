package com.green.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

@Entity
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_room_seq_gen")
	@SequenceGenerator(name = "chat_room_seq_gen", sequenceName = "CHAT_ROOM_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, length = 30)
	private String name;

	// 방장 (CHAT_USER.id). 기존 방은 null일 수 있음
	@Column(name = "owner_id")
	private Long ownerId;

	private LocalDateTime createdAt;

	// 공지로 고정한 메시지 번호 (없으면 null) · 고정한 사람 (CHAT_USER.id)
	@Column(name = "pinned_message_id")
	private Long pinnedMessageId;

	@Column(name = "pinned_by_id")
	private Long pinnedById;

	protected ChatRoom() {
	}

	public ChatRoom(String name, Long ownerId) {
		this.name = name;
		this.ownerId = ownerId;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isOwner(Long userId) {
		return ownerId != null && ownerId.equals(userId);
	}

	// 공지 고정 (한 방에 하나 — 새로 고정하면 바뀜, 검사는 PinService에서)
	public void pin(Long messageId, Long userId) {
		this.pinnedMessageId = messageId;
		this.pinnedById = userId;
	}

	public void unpin() {
		this.pinnedMessageId = null;
		this.pinnedById = null;
	}

	public Long getPinnedMessageId() {
		return pinnedMessageId;
	}

	public Long getPinnedById() {
		return pinnedById;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}