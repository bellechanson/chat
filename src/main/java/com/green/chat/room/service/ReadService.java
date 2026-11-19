package com.green.chat.room.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.room.entity.RoomRead;
import com.green.chat.room.repository.ChatMessageRepository;
import com.green.chat.room.repository.RoomReadRepository;

@Service
@Transactional(readOnly = true)
public class ReadService {

	private final RoomReadRepository readRepository;
	private final ChatMessageRepository messageRepository;

	public ReadService(RoomReadRepository readRepository, ChatMessageRepository messageRepository) {
		this.readRepository = readRepository;
		this.messageRepository = messageRepository;
	}

	// 이 방의 최신 메시지까지 읽음 처리
	@Transactional
	public void markRead(Long roomId, Long userId) {
		Long maxId = messageRepository.findMaxIdByRoomId(roomId);
		RoomRead read = readRepository.findByRoomIdAndUserId(roomId, userId)
				.orElseGet(() -> new RoomRead(roomId, userId));
		read.markRead(maxId == null ? 0L : maxId);
		readRepository.save(read);
	}

	// 방별 안 읽은 수 (roomId → 개수). 들어가 본 방만 포함, 다 읽었으면 0
	public Map<Long, Long> unreadCounts(Long userId) {
		Map<Long, Long> result = new HashMap<>();
		readRepository.findByUserId(userId).forEach(r -> result.put(r.getRoomId(), 0L));
		for (Object[] row : readRepository.countUnreadByUser(userId)) {
			result.put((Long) row[0], (Long) row[1]);
		}
		return result;
	}
}