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
@Table(name = "dm_message")
public class DmMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dm_message_seq_gen")
	@SequenceGenerator(name = "dm_message_seq_gen", sequenceName = "DM_MESSAGE_SEQ", allocationSize = 1)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id")
	private DmConversation conversation;

	// 닉네임이 아니라 id로 저장 → 닉네임을 바꿔도 영향 없음
	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	// 글 메시지: 내용 / 파일 메시지: "사진" 또는 "파일: 이름" (미리보기·알림용)
	@Column(nullable = false, length = 1000)
	private String content;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	// 상대가 읽은 시각 (안 읽었으면 null → 화면에 "1")
	@Column(name = "read_at")
	private LocalDateTime readAt;

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

	protected DmMessage() {
	}

	public DmMessage(DmConversation conversation, Long senderId, String content) {
		this(conversation, senderId, content, null);
	}

	public DmMessage(DmConversation conversation, Long senderId, String content, Attachment attachment) {
		this.conversation = conversation;
		this.senderId = senderId;
		this.content = content;
		this.attachment = attachment;
		this.createdAt = LocalDateTime.now();
	}

	public void delete() {
		this.deleted = true;
	}

	// 내용 수정 (글 메시지만, 검사는 DmService에서)
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

	public DmConversation getConversation() {
		return conversation;
	}

	public Long getSenderId() {
		return senderId;
	}

	public String getContent() {
		return content;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getReadAt() {
		return readAt;
	}

	public boolean isDeleted() {
		return Boolean.TRUE.equals(deleted);
	}

	public Attachment getAttachment() {
		return attachment;
	}
}