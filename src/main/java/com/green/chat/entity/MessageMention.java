package com.green.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

// 채팅방 메시지가 이 사람을 부름: @멘션 + 내 메시지에 단 답장 (MentionService)
//   → 알림을 꺼 둔 방이어도 데스크톱 알림, 사이드바 "@" (안 읽은 메시지 중에 있으면)
//   room_id   : 사이드바 "@" 를 방별로 빨리 찾으려고 함께 저장
//   user_id   : 불린 사람 (보낸 사람 자신은 넣지 않음)
@Entity
@Table(name = "message_mention")
public class MessageMention {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_mention_seq_gen")
	@SequenceGenerator(name = "message_mention_seq_gen", sequenceName = "MESSAGE_MENTION_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "message_id", nullable = false)
	private Long messageId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	protected MessageMention() {
	}

	public MessageMention(Long roomId, Long messageId, Long userId) {
		this.roomId = roomId;
		this.messageId = messageId;
		this.userId = userId;
	}

	public Long getId() {
		return id;
	}

	public Long getRoomId() {
		return roomId;
	}

	public Long getMessageId() {
		return messageId;
	}

	public Long getUserId() {
		return userId;
	}
}