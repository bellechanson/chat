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

// 사용자가 방에서 마지막으로 읽은 메시지 번호
@Entity
@Table(name = "room_read", uniqueConstraints = @UniqueConstraint(columnNames = { "room_id", "user_id" }))
public class RoomRead {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_read_seq_gen")
	@SequenceGenerator(name = "room_read_seq_gen", sequenceName = "ROOM_READ_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "last_read_id", nullable = false)
	private Long lastReadId; // 메시지가 없는 방이면 0

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected RoomRead() {
	}

	public RoomRead(Long roomId, Long userId) {
		this.roomId = roomId;
		this.userId = userId;
		this.lastReadId = 0L;
		this.updatedAt = LocalDateTime.now();
	}

	// 더 최신 번호일 때만 갱신 (번호가 뒤로 가지 않게)
	public void markRead(long messageId) {
		if (messageId > lastReadId)
			lastReadId = messageId;
		updatedAt = LocalDateTime.now();
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

	public Long getLastReadId() {
		return lastReadId;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}