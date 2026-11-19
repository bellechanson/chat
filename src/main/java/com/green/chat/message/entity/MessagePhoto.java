package com.green.chat.message.entity;

import org.hibernate.type.NumericBooleanConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

// 사진 묶음 메시지의 사진 한 장 (한 메시지에 최대 30장)
//   scope: "ROOM"(채팅방 메시지) / "DM"(1:1 메시지) — MessageReaction과 같은 방식
//   메시지 쪽에는 내용 "사진"만 있고 첨부 칸(attachment)은 비어 있음 → 사진은 이 테이블에서 순서대로
//   한 장만 지우면 deleted만 표시 (파일은 방 삭제 때 폴더째 지움)
@Entity
@Table(name = "message_photo", indexes = @Index(name = "ix_message_photo_msg", columnList = "scope, message_id"))
public class MessagePhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_photo_seq_gen")
	@SequenceGenerator(name = "message_photo_seq_gen", sequenceName = "MESSAGE_PHOTO_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, length = 4)
	private String scope;

	@Column(name = "message_id", nullable = false)
	private Long messageId;

	// 묶음 안 순서 (0부터, 보낸 순서 그대로)
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	// 파일 이름·저장 이름·종류·크기 (메시지 테이블과 같은 컬럼 이름)
	@Embedded
	private Attachment attachment;

	@Convert(converter = NumericBooleanConverter.class)
	@Column(name = "deleted")
	private Boolean deleted = false;

	protected MessagePhoto() {
	}

	public MessagePhoto(String scope, Long messageId, int sortOrder, Attachment attachment) {
		this.scope = scope;
		this.messageId = messageId;
		this.sortOrder = sortOrder;
		this.attachment = attachment;
	}

	public void delete() {
		this.deleted = true;
	}

	public boolean isDeleted() {
		return Boolean.TRUE.equals(deleted);
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

	public int getSortOrder() {
		return sortOrder;
	}

	public Attachment getAttachment() {
		return attachment;
	}
}