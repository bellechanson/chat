package com.green.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.dto.PinDto;
import com.green.chat.entity.ChatMessageEntity;
import com.green.chat.entity.ChatRoom;
import com.green.chat.entity.ChatUser;
import com.green.chat.entity.DmConversation;
import com.green.chat.entity.DmMessage;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatRoomRepository;
import com.green.chat.repository.ChatUserRepository;
import com.green.chat.repository.DmConversationRepository;
import com.green.chat.repository.DmMessageRepository;
import com.green.chat.util.Formats;

// 공지(고정 메시지): 채팅방은 방장·관리자, 1:1은 둘 중 누구나. 한 방(대화)에 하나
// 고정된 메시지가 삭제되면 공지도 없는 것으로 봄 (불러올 때 걸러 냄 → 삭제 코드는 그대로)
// 내용은 불러올 때마다 메시지에서 새로 읽음 → 수정·닉네임 변경이 그대로 반영됨
@Service
@Transactional(readOnly = true)
public class PinService {

	// 1:1 고정·해제 결과: 화면에 보낼 공지 + 상대 로그인 아이디 (탈퇴했으면 null)
	public record DmPinned(PinDto pin, String partnerUsername) {
	}

	private final ChatRoomRepository roomRepository;
	private final ChatMessageRepository messageRepository;
	private final DmConversationRepository conversationRepository;
	private final DmMessageRepository dmMessageRepository;
	private final ChatUserRepository userRepository;
	private final RoomService roomService;
	private final MentionService mentionService;
	private final DmService dmService;

	public PinService(ChatRoomRepository roomRepository, ChatMessageRepository messageRepository,
			DmConversationRepository conversationRepository, DmMessageRepository dmMessageRepository,
			ChatUserRepository userRepository, RoomService roomService, MentionService mentionService,
			DmService dmService) {
		this.roomRepository = roomRepository;
		this.messageRepository = messageRepository;
		this.conversationRepository = conversationRepository;
		this.dmMessageRepository = dmMessageRepository;
		this.userRepository = userRepository;
		this.roomService = roomService;
		this.mentionService = mentionService;
		this.dmService = dmService;
	}

	// ── 채팅방 ──

	// 지금 공지 (없거나 메시지가 삭제됐으면 비어 있음). 강퇴된 사람은 볼 수 없음
	public Optional<PinDto> roomPin(Long roomId, Long userId) {
		if (!roomService.canJoin(roomId, userId)) {
			throw new IllegalArgumentException("이 방에 들어갈 수 없습니다.");
		}
		ChatRoom room = roomRepository.findById(roomId).orElseThrow();
		if (room.getPinnedMessageId() == null)
			return Optional.empty();
		return messageRepository.findById(room.getPinnedMessageId())
				.filter(m -> m.getRoom().getId().equals(roomId) && !m.isDeleted())
				.map(m -> roomPinOf(m, room.getPinnedById()));
	}

	// 고정 (이미 다른 공지가 있으면 바뀜)
	@Transactional
	public PinDto pinRoom(Long roomId, Long messageId, Long userId, boolean admin) {
		ChatRoom room = manageableRoom(roomId, userId, admin);
		ChatMessageEntity m = messageId == null ? null
				: messageRepository.findById(messageId).filter(e -> e.getRoom().getId().equals(roomId)).orElse(null);
		if (m == null) {
			throw new IllegalArgumentException("존재하지 않는 메시지입니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지는 공지로 고정할 수 없습니다.");
		}
		room.pin(messageId, userId); // 변경 감지로 UPDATE
		return roomPinOf(m, userId);
	}

	@Transactional
	public void unpinRoom(Long roomId, Long userId, boolean admin) {
		ChatRoom room = manageableRoom(roomId, userId, admin);
		if (room.getPinnedMessageId() == null) {
			throw new IllegalArgumentException("고정된 공지가 없습니다.");
		}
		room.unpin();
	}

	private ChatRoom manageableRoom(Long roomId, Long userId, boolean admin) {
		ChatRoom room = roomRepository.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));
		if (!roomService.canManage(room, userId, admin)) {
			throw new IllegalArgumentException("방장과 관리자만 공지를 고정할 수 있습니다.");
		}
		return room;
	}

	private PinDto roomPinOf(ChatMessageEntity m, Long pinnedById) {
		Map<Long, String> mentions = mentionService.mentionsOf(List.of(m)).getOrDefault(m.getId(), Map.of());
		return new PinDto(m.getId(), m.getSender(), m.getContent(), mentions, Formats.day(m.getCreatedAt()),
				Formats.clock(m.getCreatedAt()), nameOf(pinnedById));
	}

	// ── 1:1 대화 ──

	public Optional<PinDto> dmPin(Long conversationId, Long userId) {
		DmConversation c = member(conversationId, userId);
		if (c.getPinnedMessageId() == null)
			return Optional.empty();
		return dmMessageRepository.findById(c.getPinnedMessageId())
				.filter(m -> m.getConversation().getId().equals(conversationId) && !m.isDeleted())
				.map(m -> dmPinOf(m, c.getPinnedById()));
	}

	@Transactional
	public DmPinned pinDm(Long conversationId, Long messageId, Long userId) {
		DmConversation c = member(conversationId, userId);
		DmMessage m = messageId == null ? null
				: dmMessageRepository.findById(messageId)
						.filter(e -> e.getConversation().getId().equals(conversationId)).orElse(null);
		if (m == null) {
			throw new IllegalArgumentException("존재하지 않는 메시지입니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지는 공지로 고정할 수 없습니다.");
		}
		c.pin(messageId, userId); // 변경 감지로 UPDATE
		return new DmPinned(dmPinOf(m, userId), dmService.partnerUsername(conversationId, userId).orElse(null));
	}

	// @return 상대 로그인 아이디 (탈퇴했으면 null)
	@Transactional
	public String unpinDm(Long conversationId, Long userId) {
		DmConversation c = member(conversationId, userId);
		if (c.getPinnedMessageId() == null) {
			throw new IllegalArgumentException("고정된 공지가 없습니다.");
		}
		c.unpin();
		return dmService.partnerUsername(conversationId, userId).orElse(null);
	}

	private DmConversation member(Long conversationId, Long userId) {
		return conversationRepository.findById(conversationId).filter(c -> c.isMember(userId))
				.orElseThrow(() -> new IllegalArgumentException("대화방에 참여하지 않았습니다."));
	}

	private PinDto dmPinOf(DmMessage m, Long pinnedById) {
		return new PinDto(m.getId(), nameOf(m.getSenderId()), m.getContent(), Map.of(), Formats.day(m.getCreatedAt()),
				Formats.clock(m.getCreatedAt()), nameOf(pinnedById));
	}

	// 화면 이름 (탈퇴했으면 "(탈퇴한 사용자)")
	private String nameOf(Long userId) {
		return userId == null ? null : userRepository.findById(userId).map(ChatUser::getDisplayName).orElse(null);
	}
}