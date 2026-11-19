package com.green.chat.room.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 강퇴 기록: 이 방(roomId)에 이 사용자(userId)는 입장 불가
@Entity
@Table(name = "room_ban", uniqueConstraints = @UniqueConstraint(columnNames = { "room_id", "user_id" }))
public class RoomBan {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_ban_seq_gen")
	@SequenceGenerator(name = "room_ban_seq_gen", sequenceName = "ROOM_BAN_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected RoomBan() {
	}

	public RoomBan(Long roomId, Long userId) {
		this.roomId = roomId;
		this.userId = userId;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Long getRoomId() {
		return roomId;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}