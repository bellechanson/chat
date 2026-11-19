package com.green.chat.notify.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.dm.repository.DmConversationRepository;
import com.green.chat.global.util.Formats;
import com.green.chat.message.entity.MessageReaction;
import com.green.chat.notify.dto.MuteState;
import com.green.chat.notify.entity.NotifyMute;
import com.green.chat.notify.repository.NotifyMuteRepository;
import com.green.chat.room.repository.ChatRoomRepository;
import com.green.chat.room.repository.RoomBanRepository;

// 방별 알림 끄기 (채팅방, 1:1 대화 공통) — 꺼 둔 방은 데스크톱 알림과 앱 아이콘 뱃지에서 빠짐
@Service
@Transactional(readOnly = true)
public class MuteService {

	public static final String ROOM = MessageReaction.ROOM;
	public static final String DM = MessageReaction.DM;

	private static final LocalTime MORNING = LocalTime.of(9, 0); // "아침까지" = 다음 아침 9시

	private final NotifyMuteRepository muteRepository;
	private final ChatRoomRepository roomRepository;
	private final RoomBanRepository banRepository;
	private final DmConversationRepository conversationRepository;

	public MuteService(NotifyMuteRepository muteRepository, ChatRoomRepository roomRepository,
			RoomBanRepository banRepository, DmConversationRepository conversationRepository) {
		this.muteRepository = muteRepository;
		this.roomRepository = roomRepository;
		this.banRepository = banRepository;
		this.conversationRepository = conversationRepository;
	}

	// 끄기/다시 켜기 → 바뀐 상태 (내 다른 탭에도 보냄)
	@Transactional
	public MuteState set(String scope, Long targetId, Long userId, String duration) {
		checkAccess(scope, targetId, userId);
		String d = duration == null ? "" : duration;

		if (d.equals("off")) {
			muteRepository.findByScopeAndTargetIdAndUserId(scope, targetId, userId).ifPresent(muteRepository::delete);
			return new MuteState(scope, targetId, false, null);
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime until = switch (d) {
		case "1h" -> now.plusHours(1);
		case "8h" -> now.plusHours(8);
		case "morning" -> nextMorning(now);
		case "forever" -> null;
		default -> throw new IllegalArgumentException("알 수 없는 시간입니다.");
		};

		NotifyMute mute = muteRepository.findByScopeAndTargetIdAndUserId(scope, targetId, userId)
				.orElseGet(() -> new NotifyMute(scope, targetId, userId));
		mute.until(until);
		muteRepository.save(mute);
		return state(mute);
	}

	// 내가 지금 꺼 둔 방 전부 (화면을 열 때 종 모양 표시용)
	public List<MuteState> mine(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		return muteRepository.findByUserId(userId).stream().filter(m -> m.isActive(now)).map(MuteService::state)
				.toList();
	}

	// 내가 지금 꺼 둔 방 번호 (scope별) — 뱃지 합계에서 빼려고
	public Set<Long> mutedTargets(Long userId, String scope) {
		LocalDateTime now = LocalDateTime.now();
		return muteRepository.findByUserId(userId).stream().filter(m -> m.getScope().equals(scope) && m.isActive(now))
				.map(NotifyMute::getTargetId).collect(Collectors.toSet());
	}

	// 이 방 알림을 꺼 둔 회원의 로그인 아이디 — 데스크톱 알림을 보낼 때 뺌
	public Set<String> mutedUsernames(String scope, Long targetId) {
		return new HashSet<>(muteRepository.findMutedUsernames(scope, targetId, LocalDateTime.now()));
	}

	// 채팅방: 있는 방이고 강퇴당하지 않았을 때, 1:1: 내가 참여한 대화일 때만
	private void checkAccess(String scope, Long targetId, Long userId) {
		boolean ok = switch (scope) {
		case ROOM -> roomRepository.existsById(targetId) && !banRepository.existsByRoomIdAndUserId(targetId, userId);
		case DM -> conversationRepository.findById(targetId).filter(c -> c.isMember(userId)).isPresent();
		default -> false;
		};
		if (!ok)
			throw new IllegalArgumentException("알림을 바꿀 수 없는 방입니다.");
	}

	// 다음 아침 9시 (지금이 새벽이면 오늘 9시, 아니면 내일 9시)
	private static LocalDateTime nextMorning(LocalDateTime now) {
		LocalDateTime today = LocalDate.from(now).atTime(MORNING);
		return now.isBefore(today) ? today : today.plusDays(1);
	}

	private static MuteState state(NotifyMute m) {
		String until = m.getMutedUntil() == null ? null : Formats.fullTime(m.getMutedUntil());
		return new MuteState(m.getScope(), m.getTargetId(), true, until);
	}
}