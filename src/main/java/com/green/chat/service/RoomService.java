package com.green.chat.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.entity.ChatRoom;
import com.green.chat.entity.ChatUser;
import com.green.chat.entity.RoomBan;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatRoomRepository;
import com.green.chat.repository.ChatUserRepository;
import com.green.chat.repository.MessageReactionRepository;
import com.green.chat.repository.RoomBanRepository;
import com.green.chat.repository.RoomReadRepository;
import com.green.chat.util.Formats;

@Service
@Transactional(readOnly = true)
public class RoomService {

	private static final int MAX_NAME = 30;

	// 새 메시지 알림 대상: 방 이름 + 받을 사람들의 로그인 아이디
	public record NotifyTargets(String roomName, List<String> usernames) {
	}

	private final ChatRoomRepository roomRepository;
	private final ChatMessageRepository messageRepository;
	private final RoomBanRepository banRepository;
	private final RoomReadRepository readRepository;
	private final ChatUserRepository userRepository;
	private final FileStorage fileStorage;
	private final MessageReactionRepository reactionRepository;

	public RoomService(ChatRoomRepository roomRepository, ChatMessageRepository messageRepository,
			RoomBanRepository banRepository, RoomReadRepository readRepository, ChatUserRepository userRepository,
			FileStorage fileStorage, MessageReactionRepository reactionRepository) {
		this.roomRepository = roomRepository;
		this.messageRepository = messageRepository;
		this.banRepository = banRepository;
		this.readRepository = readRepository;
		this.userRepository = userRepository;
		this.fileStorage = fileStorage;
		this.reactionRepository = reactionRepository;
	}

	public List<ChatRoom> findAll() {
		return roomRepository.findAllByOrderByIdDesc();
	}

	public Optional<ChatRoom> findById(Long id) {
		return roomRepository.findById(id);
	}

	@Transactional
	public ChatRoom create(String name, Long ownerId) {
		String trimmed = Formats.cut(name, MAX_NAME);
		if (trimmed.isEmpty())
			return null;
		return roomRepository.save(new ChatRoom(trimmed, ownerId));
	}

	public boolean isBanned(Long roomId, Long userId) {
		return banRepository.existsByRoomIdAndUserId(roomId, userId);
	}

	public boolean canJoin(Long roomId, Long userId) {
		return roomRepository.existsById(roomId) && !isBanned(roomId, userId);
	}

	public String ownerNickname(ChatRoom room) {
		if (room.getOwnerId() == null)
			return null;
		return userRepository.findById(room.getOwnerId()).map(ChatUser::getDisplayName).orElse(null);
	}

	// 방을 관리할 수 있는지: 방장 또는 관리자
	public boolean canManage(ChatRoom room, Long userId, boolean admin) {
		return admin || room.isOwner(userId);
	}

	// 새 메시지 알림을 받을 사람: 이 방에 들어와 본 적 있는 회원 (보낸 사람 제외)
	public NotifyTargets notifyTargets(Long roomId, Long senderId) {
		String roomName = roomRepository.findById(roomId).map(ChatRoom::getName).orElse("채팅방");
		return new NotifyTargets(roomName, readRepository.findMemberUsernames(roomId, senderId));
	}

	@Transactional
	public void kick(Long roomId, Long requesterId, boolean requesterAdmin, String targetNickname) {
		ChatRoom room = roomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
		if (!canManage(room, requesterId, requesterAdmin)) {
			throw new IllegalArgumentException("방장이나 관리자만 강퇴할 수 있습니다.");
		}
		if (targetNickname == null || targetNickname.isBlank()) {
			throw new IllegalArgumentException("강퇴할 사용자를 지정하세요.");
		}
		ChatUser target = userRepository.findByNickname(targetNickname.trim())
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
		if (target.getId().equals(requesterId)) {
			throw new IllegalArgumentException("자기 자신은 강퇴할 수 없습니다.");
		}
		if (target.isAdmin()) {
			throw new IllegalArgumentException("관리자는 강퇴할 수 없습니다.");
		}
		if (!banRepository.existsByRoomIdAndUserId(roomId, target.getId())) {
			banRepository.save(new RoomBan(roomId, target.getId()));
		}
		readRepository.deleteByRoomIdAndUserId(roomId, target.getId());
	}

	@Transactional
	public void delete(Long roomId, Long requesterId, boolean requesterAdmin) {
		ChatRoom room = roomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
		if (!canManage(room, requesterId, requesterAdmin)) {
			throw new IllegalArgumentException("방장이나 관리자만 삭제할 수 있습니다.");
		}
		reactionRepository.deleteRoomReactions(roomId); // 메시지보다 먼저 (메시지 번호로 찾음)
		messageRepository.deleteByRoomId(roomId);
		banRepository.deleteByRoomId(roomId);
		readRepository.deleteByRoomId(roomId);
		roomRepository.delete(room);
		fileStorage.deleteFolder("rooms/" + roomId); // 이 방에서 주고받은 파일도 삭제
	}
}