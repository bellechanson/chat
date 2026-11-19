package com.green.chat.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.global.util.Formats;
import com.green.chat.room.entity.ChatRoom;
import com.green.chat.room.entity.RoomBan;
import com.green.chat.room.repository.ChatRoomRepository;
import com.green.chat.room.repository.RoomBanRepository;
import com.green.chat.room.service.PresenceService;
import com.green.chat.user.entity.ChatUser;
import com.green.chat.user.entity.Role;
import com.green.chat.user.repository.ChatUserRepository;

@Service
@Transactional(readOnly = true)
public class AdminService {

	public record RoomRow(Long id, String name, String ownerNickname, int online, long bans, String createdAt) {
	}

	public record BanRow(Long id, Long roomId, String roomName, String nickname, String createdAt) {
	}

	public record UserRow(Long id, String username, String nickname, String email, boolean admin, boolean suspended,
			boolean online, boolean me, String createdAt) {
	}

	private final ChatRoomRepository roomRepository;
	private final RoomBanRepository banRepository;
	private final ChatUserRepository userRepository;
	private final PresenceService presenceService;

	public AdminService(ChatRoomRepository roomRepository, RoomBanRepository banRepository,
			ChatUserRepository userRepository, PresenceService presenceService) {
		this.roomRepository = roomRepository;
		this.banRepository = banRepository;
		this.userRepository = userRepository;
		this.presenceService = presenceService;
	}

	// ───────── 채팅방 ─────────
	public List<RoomRow> rooms() {
		List<ChatRoom> rooms = roomRepository.findAllByOrderByIdDesc();
		Map<Long, String> owners = nicknames(
				rooms.stream().map(ChatRoom::getOwnerId).filter(Objects::nonNull).toList());

		Map<Long, Long> banCounts = new HashMap<>();
		for (Object[] row : banRepository.countGroupByRoom()) {
			banCounts.put((Long) row[0], (Long) row[1]);
		}
		Map<Long, Integer> online = presenceService.counts();

		return rooms.stream()
				.map(r -> new RoomRow(r.getId(), r.getName(),
						r.getOwnerId() == null ? "-" : owners.getOrDefault(r.getOwnerId(), "(탈퇴)"),
						online.getOrDefault(r.getId(), 0), banCounts.getOrDefault(r.getId(), 0L),
						Formats.fullTime(r.getCreatedAt())))
				.toList();
	}

	// ───────── 강퇴 ─────────
	public List<BanRow> bans() {
		List<RoomBan> bans = banRepository.findAllByOrderByIdDesc();
		Map<Long, String> roomNames = roomRepository
				.findAllById(bans.stream().map(RoomBan::getRoomId).distinct().toList()).stream()
				.collect(Collectors.toMap(ChatRoom::getId, ChatRoom::getName));
		Map<Long, String> users = nicknames(bans.stream().map(RoomBan::getUserId).toList());

		return bans.stream()
				.map(b -> new BanRow(b.getId(), b.getRoomId(), roomNames.getOrDefault(b.getRoomId(), "(삭제된 방)"),
						users.getOrDefault(b.getUserId(), "(탈퇴)"), Formats.fullTime(b.getCreatedAt())))
				.toList();
	}

	@Transactional
	public void unban(Long banId) {
		if (!banRepository.existsById(banId)) {
			throw new IllegalArgumentException("이미 해제된 강퇴입니다.");
		}
		banRepository.deleteById(banId);
	}

	// ───────── 회원 ─────────
	public List<UserRow> users(String keyword, Long myId, Set<Long> onlineIds) {
		List<ChatUser> users = (keyword == null || keyword.isBlank())
				? userRepository.findAllByWithdrawnAtIsNullOrderByIdDesc()
				: userRepository.search(keyword.trim());

		return users.stream()
				.map(u -> new UserRow(u.getId(), u.getUsername(), u.getNickname(), u.getEmail(), u.isAdmin(),
						u.isSuspended(), onlineIds.contains(u.getId()), u.getId().equals(myId),
						Formats.fullTime(u.getCreatedAt())))
				.toList();
	}

	// 관리자 지정/해제
	@Transactional
	public ChatUser changeRole(Long adminId, Long targetId, Role role) {
		ChatUser target = target(adminId, targetId, "자기 자신의 권한은 바꿀 수 없습니다.");
		if (role == Role.ADMIN && target.isSuspended()) {
			throw new IllegalArgumentException("정지된 회원은 관리자로 지정할 수 없습니다.");
		}
		target.changeRole(role);
		return target;
	}

	// 정지/해제
	@Transactional
	public ChatUser setSuspended(Long adminId, Long targetId, boolean suspend) {
		ChatUser target = target(adminId, targetId, "자기 자신은 정지할 수 없습니다.");
		if (suspend && target.isAdmin()) {
			throw new IllegalArgumentException("관리자는 정지할 수 없습니다. 먼저 관리자 권한을 해제하세요.");
		}
		if (suspend)
			target.suspend();
		else
			target.unsuspend();
		return target;
	}

	// 강제 로그아웃 대상 확인
	public ChatUser checkLogoutTarget(Long adminId, Long targetId) {
		return target(adminId, targetId, "자기 자신은 강제 로그아웃할 수 없습니다.");
	}

	private ChatUser target(Long adminId, Long targetId, String selfMessage) {
		if (adminId.equals(targetId))
			throw new IllegalArgumentException(selfMessage);
		return userRepository.findById(targetId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
	}

	private Map<Long, String> nicknames(List<Long> ids) {
		if (ids.isEmpty())
			return Map.of();
		return userRepository.findAllById(ids.stream().distinct().toList()).stream()
				.collect(Collectors.toMap(ChatUser::getId, ChatUser::getDisplayName));
	}
}