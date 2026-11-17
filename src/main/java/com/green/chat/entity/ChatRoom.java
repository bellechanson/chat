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