package com.green.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.green.chat.dto.DmConversationView;
import com.green.chat.dto.DmEditedEvent;
import com.green.chat.dto.DmMessageDto;
import com.green.chat.dto.DmPage;
import com.green.chat.dto.ReactionDto;
import com.green.chat.dto.ReactionEvent;
import com.green.chat.entity.Attachment;
import com.green.chat.entity.ChatUser;
import com.green.chat.entity.DmConversation;
import com.green.chat.entity.DmMessage;
import com.green.chat.entity.MessageReaction;
import com.green.chat.repository.ChatUserRepository;
import com.green.chat.repository.DmConversationRepository;
import com.green.chat.repository.DmMessageRepository;
import com.green.chat.util.Formats;

@Service
@Transactional(readOnly = true)
public class DmService {

	private static final int PAGE_SIZE = 50;
	private static final int MAX_CONTENT = 1000;

	// 보내기 결과: 화면용 메시지 + 상대 로그인 아이디(전송 대상)
	public record Sent(DmMessageDto message, String partnerUsername) {
	}

	// 수정 결과: 화면에 보낼 신호 + 상대 로그인 아이디(전송 대상)
	public record Edited(DmEditedEvent event, String partnerUsername) {
	}

	// 반응 결과: 화면에 보낼 신호 + 상대 로그인 아이디(전송 대상)
	public record Reacted(ReactionEvent event, String partnerUsername) {
	}

	private final DmConversationRepository conversationRepository;
	private final DmMessageRepository messageRepository;
	private final ChatUserRepository userRepository;
	private final FileStorage fileStorage;
	private final ReactionService reactionService;

	public DmService(DmConversationRepository conversationRepository, DmMessageRepository messageRepository,
			ChatUserRepository userRepository, FileStorage fileStorage, ReactionService reactionService) {
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.fileStorage = fileStorage;
		this.reactionService = reactionService;
	}

	// ── 대화 시작: 있으면 기존 대화, 없으면 새로 ──

	// 사용자 목록에서 클릭
	@Transactional
	public DmConversation startById(Long myId, Long partnerId) {
		ChatUser partner = userRepository.findById(partnerId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
		return open(myId, partner);
	}

	// 채팅방 접속자 옆 [1:1] 버튼
	@Transactional
	public DmConversation startByNickname(Long myId, String nickname) {
		if (nickname == null || nickname.isBlank()) {
			throw new IllegalArgumentException("대화할 상대의 닉네임을 입력하세요.");
		}
		ChatUser partner = userRepository.findByNickname(nickname.trim())
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
		return open(myId, partner);
	}

	private DmConversation open(Long myId, ChatUser partner) {
		if (partner.getId().equals(myId)) {
			throw new IllegalArgumentException("자기 자신과는 대화할 수 없습니다.");
		}
		if (partner.isWithdrawn()) {
			throw new IllegalArgumentException("탈퇴한 사용자와는 대화할 수 없습니다.");
		}
		if (partner.isSuspended()) {
			throw new IllegalArgumentException("정지된 사용자와는 대화할 수 없습니다.");
		}
		long user1 = Math.min(myId, partner.getId());
		long user2 = Math.max(myId, partner.getId());
		DmConversation c = conversationRepository.findByUser1IdAndUser2Id(user1, user2)
				.orElseGet(() -> conversationRepository.save(new DmConversation(user1, user2)));
		c.showFor(myId); // 예전에 나갔던 대화면 이전 대화와 함께 다시 보이게
		return c;
	}

	// 대화 상대로 고를 수 있는 사용자 (나, 정지·탈퇴한 사용자 제외)
	public List<ChatUser> selectableUsers(Long myId) {
		return userRepository.findAllByWithdrawnAtIsNullOrderByNicknameAsc().stream()
				.filter(u -> !u.getId().equals(myId)).filter(u -> !u.isSuspended()).toList();
	}

	// 참여자일 때만 대화방 반환
	public Optional<DmConversation> findForMember(Long conversationId, Long userId) {
		return conversationRepository.findById(conversationId).filter(c -> c.isMember(userId));
	}

	public String partnerNickname(DmConversation c, Long userId) {
		return userRepository.findById(c.partnerOf(userId)).map(ChatUser::getDisplayName)
				.orElse(ChatUser.WITHDRAWN_NAME);
	}

	// 상대가 탈퇴했는지 (DB에서 직접 지운 예전 회원도 탈퇴로 봄) → 대화 화면 입력칸 막기
	public boolean isPartnerGone(DmConversation c, Long userId) {
		return userRepository.findById(c.partnerOf(userId)).map(ChatUser::isWithdrawn).orElse(true);
	}

	// 나갔던 대화를 주소로 직접 열면 다시 목록에 보이게
	@Transactional
	public void reopen(Long conversationId, Long userId) {
		findForMember(conversationId, userId).filter(c -> c.isHiddenFor(userId)).ifPresent(c -> c.showFor(userId));
	}

	// ── 대화방 나가기: 내 목록에서만 숨김 (메시지·상대 화면은 그대로, 상대에게 알리지 않음) ──
	@Transactional
	public void leave(Long conversationId, Long userId) {
		member(conversationId, userId).hideFor(userId); // 변경 감지로 UPDATE
	}

	// 참여자일 때만 상대 로그인 아이디 (입력 중 신호 전달용)
	public Optional<String> partnerUsername(Long conversationId, Long userId) {
		return findForMember(conversationId, userId).flatMap(c -> userRepository.findById(c.partnerOf(userId)))
				.map(ChatUser::getUsername);
	}

	// ── 대화 목록 (메시지가 하나라도 있고, 내가 나가지 않은 대화만) ──
	public List<DmConversationView> conversations(Long userId) {
		List<DmConversation> convs = conversationRepository.findMine(userId).stream()
				.filter(c -> !c.isHiddenFor(userId)).toList();
		if (convs.isEmpty())
			return List.of();

		// 상대 이름 한 번에 조회 (탈퇴했으면 "(탈퇴한 사용자)")
		Map<Long, String> names = userRepository.findAllById(convs.stream().map(c -> c.partnerOf(userId)).toList())
				.stream().collect(Collectors.toMap(ChatUser::getId, ChatUser::getDisplayName));

		// 마지막 메시지 한 번에 조회
		Map<Long, DmMessage> last = messageRepository
				.findLastMessages(convs.stream().map(DmConversation::getId).toList()).stream()
				.collect(Collectors.toMap(m -> m.getConversation().getId(), m -> m));

		// 안 읽은 수 한 번에 조회
		Map<Long, Long> unread = new HashMap<>();
		for (Object[] row : messageRepository.countUnreadByUser(userId)) {
			unread.put((Long) row[0], (Long) row[1]);
		}

		return convs.stream().filter(c -> last.containsKey(c.getId())) // 빈 대화는 목록에서 제외
				.map(c -> {
					DmMessage m = last.get(c.getId());
					return new DmConversationView(c.getId(), c.partnerOf(userId),
							names.getOrDefault(c.partnerOf(userId), ChatUser.WITHDRAWN_NAME),
							m.isDeleted() ? "삭제된 메시지입니다" : m.getContent(), unread.getOrDefault(c.getId(), 0L));
				}).toList();
	}

	public long totalUnread(Long userId) {
		return messageRepository.countAllUnread(userId, Boolean.TRUE);
	}

	// ── 이전 대화 (커서 페이징) ──
	public DmPage messages(Long conversationId, Long userId, Long beforeId) {
		DmConversation c = member(conversationId, userId);

		List<DmMessage> rows = (beforeId == null)
				? messageRepository.findTop51ByConversationIdOrderByIdDesc(conversationId)
				: messageRepository.findTop51ByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId);

		boolean hasMore = rows.size() > PAGE_SIZE;
		List<DmMessage> page = new ArrayList<>(hasMore ? rows.subList(0, PAGE_SIZE) : rows);
		Collections.reverse(page);

		Map<Long, String> names = nicknames(c);
		// 이 묶음의 반응을 한 번에 가져와서 메시지마다 붙임
		Map<Long, List<ReactionDto>> reactions = reactionService.summaries(MessageReaction.DM,
				page.stream().map(DmMessage::getId).toList());
		return new DmPage(page.stream()
				.map(m -> DmMessageDto.from(m, names.getOrDefault(m.getSenderId(), ChatUser.WITHDRAWN_NAME),
						reactions.getOrDefault(m.getId(), List.of())))
				.toList(), hasMore);
	}

	// ── 보내기 ──
	@Transactional
	public Sent send(Long conversationId, Long senderId, String senderNickname, String content) {
		DmConversation c = member(conversationId, senderId);
		checkPartner(c, senderId);

		String text = Formats.cut(content, MAX_CONTENT);
		if (text.isEmpty())
			throw new IllegalArgumentException("빈 메시지입니다.");

		return saveAndReturn(c, senderId, senderNickname, new DmMessage(c, senderId, text));
	}

	// ── 파일 보내기 ──
	@Transactional
	public Sent sendFile(Long conversationId, Long senderId, String senderNickname, MultipartFile file) {
		DmConversation c = member(conversationId, senderId);
		checkPartner(c, senderId);
		Attachment att = fileStorage.store("dm/" + conversationId, file);
		return saveAndReturn(c, senderId, senderNickname, new DmMessage(c, senderId, att.label(), att));
	}

	private Sent saveAndReturn(DmConversation c, Long senderId, String senderNickname, DmMessage message) {
		DmMessage saved = messageRepository.save(message);
		c.touch(); // 목록 정렬용 시각 갱신 + 나갔던 사람에게 다시 보이게 (변경 감지로 UPDATE)

		String partnerUsername = userRepository.findById(c.partnerOf(senderId)).map(ChatUser::getUsername).orElse(null);
		return new Sent(DmMessageDto.from(saved, senderNickname), partnerUsername);
	}

	// 내려받을 파일 (참여자만, 이 대화의 삭제 안 된 파일 메시지만)
	public Optional<Attachment> attachment(Long conversationId, Long messageId, Long userId) {
		if (findForMember(conversationId, userId).isEmpty())
			return Optional.empty();
		return messageRepository.findById(messageId).filter(m -> m.getConversation().getId().equals(conversationId))
				.filter(m -> !m.isDeleted()).map(DmMessage::getAttachment);
	}

	// ── 읽음 처리: 새로 읽은 게 있으면 상대 로그인 아이디 반환 ──
	@Transactional
	public Optional<String> markRead(Long conversationId, Long userId) {
		Optional<DmConversation> found = findForMember(conversationId, userId);
		if (found.isEmpty())
			return Optional.empty();

		int updated = messageRepository.markRead(conversationId, userId, LocalDateTime.now());
		if (updated == 0)
			return Optional.empty(); // 이미 다 읽음 → 알릴 필요 없음

		return userRepository.findById(found.get().partnerOf(userId)).map(ChatUser::getUsername);
	}

	// ── 메시지 삭제 (내 메시지만): 알려 줄 상대 로그인 아이디 반환 ──
	@Transactional
	public String delete(Long conversationId, Long messageId, Long userId) {
		DmConversation c = member(conversationId, userId);
		DmMessage m = messageRepository.findById(messageId)
				.filter(msg -> msg.getConversation().getId().equals(conversationId))
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
		if (!m.getSenderId().equals(userId)) {
			throw new IllegalArgumentException("내 메시지만 삭제할 수 있습니다.");
		}
		m.delete(); // 변경 감지로 UPDATE
		return userRepository.findById(c.partnerOf(userId)).map(ChatUser::getUsername).orElse(null);
	}

	// ── 메시지 수정 (내 글 메시지만, 사진·파일 메시지는 수정 불가) ──
	@Transactional
	public Edited edit(Long conversationId, Long messageId, Long userId, String content) {
		DmConversation c = member(conversationId, userId);
		checkPartner(c, userId); // 상대가 탈퇴했으면 수정도 막음 (대화가 끝난 상태)

		DmMessage m = messageRepository.findById(messageId)
				.filter(msg -> msg.getConversation().getId().equals(conversationId))
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
		if (!m.getSenderId().equals(userId)) {
			throw new IllegalArgumentException("내 메시지만 수정할 수 있습니다.");
		}
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지는 수정할 수 없습니다.");
		}
		if (m.getAttachment() != null) {
			throw new IllegalArgumentException("사진·파일 메시지는 수정할 수 없습니다.");
		}
		String text = Formats.cut(content, MAX_CONTENT);
		if (text.isEmpty()) {
			throw new IllegalArgumentException("빈 메시지로는 수정할 수 없습니다.");
		}
		if (text.equals(m.getContent())) {
			throw new IllegalArgumentException("바뀐 내용이 없습니다.");
		}

		m.edit(text); // 변경 감지로 UPDATE (lastMessageAt은 그대로 → 목록 순서·안 읽은 수 안 바뀜)
		boolean last = !messageRepository.existsByConversationIdAndIdGreaterThan(conversationId, messageId);
		DmEditedEvent event = new DmEditedEvent(conversationId, messageId, text, Formats.fullTime(m.getEditedAt()),
				last);
		String partnerUsername = userRepository.findById(c.partnerOf(userId)).map(ChatUser::getUsername).orElse(null);
		return new Edited(event, partnerUsername);
	}

	// ── 이모지 반응 (상대·내 메시지 모두, 사진·파일 포함): 같은 걸 다시 누르면 취소, 다른 걸 누르면 바뀜 ──
	@Transactional
	public Reacted react(Long conversationId, Long messageId, Long userId, String emoji) {
		DmConversation c = member(conversationId, userId);
		checkPartner(c, userId); // 상대가 탈퇴했으면 막음 (대화가 끝난 상태)

		DmMessage m = messageRepository.findById(messageId)
				.filter(msg -> msg.getConversation().getId().equals(conversationId))
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메시지입니다."));
		if (m.isDeleted()) {
			throw new IllegalArgumentException("삭제된 메시지에는 반응할 수 없습니다.");
		}

		List<ReactionDto> reactions = reactionService.toggle(MessageReaction.DM, messageId, userId, emoji);
		String partnerUsername = userRepository.findById(c.partnerOf(userId)).map(ChatUser::getUsername).orElse(null);
		return new Reacted(new ReactionEvent(conversationId, messageId, reactions), partnerUsername);
	}

	// 탈퇴한 사람에게는 보낼 수 없음
	private void checkPartner(DmConversation c, Long senderId) {
		if (isPartnerGone(c, senderId)) {
			throw new IllegalArgumentException("탈퇴한 사용자에게는 메시지를 보낼 수 없습니다.");
		}
	}

	// 참여자가 아니면 예외
	private DmConversation member(Long conversationId, Long userId) {
		return findForMember(conversationId, userId)
				.orElseThrow(() -> new IllegalArgumentException("대화방에 참여하지 않았습니다."));
	}

	private Map<Long, String> nicknames(DmConversation c) {
		return userRepository.findAllById(List.of(c.getUser1Id(), c.getUser2Id())).stream()
				.collect(Collectors.toMap(ChatUser::getId, ChatUser::getDisplayName));
	}
}