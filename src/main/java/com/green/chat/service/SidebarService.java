package com.green.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.security.LoginUser;
import com.green.chat.util.Formats;

// 모든 화면 왼쪽의 공통 사이드바 내용
@Service
@Transactional(readOnly = true)
public class SidebarService {

	public record View(Long myId, String nickname, String initial, boolean admin, List<Room> rooms, List<Dm> dms) {
	}

	// unread: 들어가 본 적 없는 방이면 null (안 읽은 수를 세지 않음)
	// mentioned: 안 읽은 메시지 중에 나를 부른 것(@멘션·답장)이 있으면 true → "@"
	public record Room(Long id, String name, int online, Long unread, boolean mentioned) {
	}

	public record Dm(Long id, Long partnerId, String partnerNickname, String initial, int tint, String lastMessage,
			long unread, boolean online) {
	}

	private final RoomService roomService;
	private final PresenceService presenceService;
	private final ReadService readService;
	private final DmService dmService;
	private final ConnectionTracker connectionTracker;
	private final MentionService mentionService;

	public SidebarService(RoomService roomService, PresenceService presenceService, ReadService readService,
			DmService dmService, ConnectionTracker connectionTracker, MentionService mentionService) {
		this.roomService = roomService;
		this.presenceService = presenceService;
		this.readService = readService;
		this.dmService = dmService;
		this.connectionTracker = connectionTracker;
		this.mentionService = mentionService;
	}

	public View view(LoginUser me) {
		Map<Long, Integer> counts = presenceService.counts();
		Map<Long, Long> unread = readService.unreadCounts(me.getId());
		Set<Long> mentioned = mentionService.unreadRooms(me.getId());
		List<Room> rooms = roomService.findAll().stream().map(r -> new Room(r.getId(), r.getName(),
				counts.getOrDefault(r.getId(), 0), unread.get(r.getId()), mentioned.contains(r.getId()))).toList();

		Set<Long> online = connectionTracker.onlineUserIds();
		List<Dm> dms = dmService.conversations(me.getId()).stream()
				.map(c -> new Dm(c.id(), c.partnerId(), c.partnerNickname(), Formats.initial(c.partnerNickname()),
						Formats.tint(c.partnerNickname()), c.lastMessage(), c.unread(), online.contains(c.partnerId())))
				.toList();

		return new View(me.getId(), me.getNickname(), Formats.initial(me.getNickname()), me.isAdmin(), rooms, dms);
	}
}