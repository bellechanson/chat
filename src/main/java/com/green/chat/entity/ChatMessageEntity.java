package com.green.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.type.NumericBooleanConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_message_seq_gen")
	@SequenceGenerator(name = "chat_message_seq_gen", sequenceName = "CHAT_MESSAGE_SEQ", allocationSize = 1)
	private Long id;

	// 여러 메시지(N) → 하나의 방(1). DB에는 room_id 컬럼(외래키)으로 저장
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id")
	private ChatRoom room;

	@Column(nullable = false, length = 20)
	private String sender;

	// 글 메시지: 내용 / 파일 메시지: "사진" 또는 "파일: 이름" (미리보기·알림용)
	@Column(nullable = false, length = 1000)
	private String content;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	// 삭제된 메시지 (예전 메시지는 null → 삭제 안 됨으로 취급)
	@Convert(converter = NumericBooleanConverter.class)
	@Column(name = "deleted")
	private Boolean deleted = false;

	// 첨부 파일 (글 메시지면 null)
	@Embedded
	private Attachment attachment;

	// 마지막으로 수정한 시각 (수정 안 했으면 null → 화면에 "(수정됨)" 없음)
	@Column(name = "edited_at")
	private LocalDateTime editedAt;

	protected ChatMessageEntity() {
	}

	public ChatMessageEntity(ChatRoom room, String sender, String content) {
		this(room, sender, content, null);
	}

	public ChatMessageEntity(ChatRoom room, String sender, String content, Attachment attachment) {
		this.room = room;
		this.sender = sender;
		this.content = content;
		this.attachment = attachment;
		this.createdAt = LocalDateTime.now();
	}

	public void delete() {
		this.deleted = true;
	}

	// 내용 수정 (글 메시지만, 검사는 ChatService에서)
	public void edit(String content) {
		this.content = content;
		this.editedAt = LocalDateTime.now();
	}

	public LocalDateTime getEditedAt() {
		return editedAt;
	}

	public Long getId() {
		return id;
	}

	public ChatRoom getRoom() {
		return room;
	}

	public String getSender() {
		return sender;
	}

	public String getContent() {
		return content;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public boolean isDeleted() {
		return Boolean.TRUE.equals(deleted);
	}

	public Attachment getAttachment() {
		return attachment;
	}
}