package com.green.chat.room.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

// 방별 현재 접속자 관리 (서버 메모리)
@Service
public class PresenceService {

	// roomId → (WebSocket sessionId → 닉네임)
	private final Map<Long, Map<String, String>> rooms = new HashMap<>();
	// sessionId → roomId
	private final Map<String, Long> sessionRoom = new HashMap<>();

	public record Leave(Long roomId, String nickname, boolean lastSession) {
	}

	public synchronized boolean join(Long roomId, String sessionId, String nickname) {
		Map<String, String> sessions = rooms.computeIfAbsent(roomId, k -> new HashMap<>());
		boolean alreadyHere = sessions.containsValue(nickname);
		sessions.put(sessionId, nickname);
		sessionRoom.put(sessionId, roomId);
		return !alreadyHere;
	}

	public synchronized Leave leave(String sessionId) {
		Long roomId = sessionRoom.remove(sessionId);
		if (roomId == null)
			return null;

		Map<String, String> sessions = rooms.get(roomId);
		if (sessions == null)
			return null;

		String nickname = sessions.remove(sessionId);
		if (nickname == null)
			return null;

		boolean stillHere = sessions.containsValue(nickname);
		if (sessions.isEmpty())
			rooms.remove(roomId);

		return new Leave(roomId, nickname, !stillHere);
	}

	// 강퇴: 해당 닉네임의 모든 탭(세션)을 방에서 제거
	// 이후 그 브라우저가 연결을 끊어도 leave()가 null을 반환해서 퇴장 알림이 중복되지 않음
	public synchronized void kick(Long roomId, String nickname) {
		Map<String, String> sessions = rooms.get(roomId);
		if (sessions == null)
			return;

		Iterator<Map.Entry<String, String>> it = sessions.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> e = it.next();
			if (e.getValue().equals(nickname)) {
				sessionRoom.remove(e.getKey());
				it.remove();
			}
		}
		if (sessions.isEmpty())
			rooms.remove(roomId);
	}

	// 방 삭제: 방의 모든 접속 정보 제거
	public synchronized void removeRoom(Long roomId) {
		Map<String, String> sessions = rooms.remove(roomId);
		if (sessions != null) {
			sessions.keySet().forEach(sessionRoom::remove);
		}
	}

	public synchronized List<String> users(Long roomId) {
		Map<String, String> sessions = rooms.get(roomId);
		if (sessions == null)
			return List.of();
		return sessions.values().stream().distinct().sorted().toList();
	}

	public synchronized Map<Long, Integer> counts() {
		Map<Long, Integer> result = new HashMap<>();
		rooms.forEach((roomId, sessions) -> result.put(roomId, (int) sessions.values().stream().distinct().count()));
		return result;
	}
}