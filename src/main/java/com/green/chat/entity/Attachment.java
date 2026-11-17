package com.green.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// 메시지에 붙은 파일 정보 (채팅방, DM 메시지 테이블에 컬럼 4개로 들어감)
// 글 메시지는 네 컬럼이 모두 비어 있음 → 읽어 오면 null
@Embeddable
public class Attachment {

	@Column(name = "file_name", length = 200)
	private String name; // 원래 파일 이름 (화면 표시, 내려받을 때 이름)

	@Column(name = "file_key", length = 100)
	private String storedName; // 서버 폴더에 저장한 이름 (UUID.확장자)

	@Column(name = "file_type", length = 100)
	private String contentType; // image/png, application/pdf …

	@Column(name = "file_size")
	private Long size;

	protected Attachment() {
	}

	public Attachment(String name, String storedName, String contentType, long size) {
		this.name = name;
		this.storedName = storedName;
		this.contentType = contentType;
		this.size = size;
	}

	public boolean isImage() {
		return contentType != null && contentType.startsWith("image/");
	}

	// 목록 미리보기, 데스크톱 알림에 쓸 글자
	public String label() {
		return isImage() ? "사진" : "파일: " + name;
	}

	public String getName() {
		return name;
	}

	public String getStoredName() {
		return storedName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSize() {
		return size == null ? 0 : size;
	}
}