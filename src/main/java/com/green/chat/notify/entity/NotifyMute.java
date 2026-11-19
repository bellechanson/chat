package com.green.chat.notify.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 방별 알림 끄기: 이 사람은 이 방(채팅방 또는 1:1 대화)의 데스크톱 알림·앱 아이콘 뱃지를 받지 않음
//   scope      : "ROOM"(채팅방) / "DM"(1:1 대화) — 두 번호가 겹칠 수 있어서 구분 (MessageReaction.ROOM/DM)
//   target_id  : 채팅방 번호 또는 1:1 대화방 번호
//   mutedUntil : 이 시각까지 끔, null이면 계속 끔 (시각이 지나면 저절로 다시 켜진 것으로 봄)
@Entity
@Table(name = "notify_mute", uniqueConstraints = @UniqueConstraint(columnNames = { "scope", "target_id", "user_id" }))
public class NotifyMute {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notify_mute_seq_gen")
	@SequenceGenerator(name = "notify_mute_seq_gen", sequenceName = "NOTIFY_MUTE_SEQ", allocationSize = 1)
	private Long id;

	@Column(nullable = false, length = 4)
	private String scope;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "muted_until")
	private LocalDateTime mutedUntil;

	protected NotifyMute() {
	}

	public NotifyMute(String scope, Long targetId, Long userId) {
		this.scope = scope;
		this.targetId = targetId;
		this.userId = userId;
	}

	// 끄는 시간 바꾸기 (null이면 계속)
	public void until(LocalDateTime mutedUntil) {
		this.mutedUntil = mutedUntil;
	}

	// 지금 꺼져 있는지 (시간이 지났으면 false)
	public boolean isActive(LocalDateTime now) {
		return mutedUntil == null || mutedUntil.isAfter(now);
	}

	public Long getId() {
		return id;
	}

	public String getScope() {
		return scope;
	}

	public Long getTargetId() {
		return targetId;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDateTime getMutedUntil() {
		return mutedUntil;
	}
}