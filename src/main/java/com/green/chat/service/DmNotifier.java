package com.green.chat.service;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import com.green.chat.dto.DmMessageDto;
import com.green.chat.dto.NotifyEvent;
import com.green.chat.dto.PinDto;
import com.green.chat.dto.PinEvent;
import com.green.chat.util.Formats;

// 새 DM(글, 파일)을 나와 상대 화면에 보내고, 상대에게 데스크톱 알림
@Component
public class DmNotifier {

	private final SimpMessageSendingOperations messagingTemplate;
	private final MuteService muteService;

	public DmNotifier(SimpMessageSendingOperations messagingTemplate, MuteService muteService) {
		this.messagingTemplate = messagingTemplate;
		this.muteService = muteService;
	}

	public void messageSent(String myUsername, String myNickname, DmService.Sent sent) {
		DmMessageDto m = sent.message();

		// 나(다른 탭 포함)와 상대에게만 전송
		messagingTemplate.convertAndSendToUser(myUsername, "/queue/dm", m);
		if (sent.partnerUsername() == null)
			return;

		messagingTemplate.convertAndSendToUser(sent.partnerUsername(), "/queue/dm", m);
		// 상대가 이 대화 알림을 꺼 뒀으면 데스크톱 알림은 보내지 않음 (메시지는 위에서 이미 보냄)
		if (muteService.mutedUsernames(MuteService.DM, m.conversationId()).contains(sent.partnerUsername()))
			return;
		// 상대에게 데스크톱 알림 (같은 대화는 한 칸에서 교체)
		messagingTemplate.convertAndSendToUser(sent.partnerUsername(), "/queue/notify", new NotifyEvent(myNickname,
				Formats.preview(m.content()), "/dm/" + m.conversationId(), "dm-" + m.conversationId(), m.id()));
	}

	// 공지가 바뀜 (pin이 null이면 해제) → 나(다른 탭 포함)와 상대의 공지 띠
	// 새 공지면 상대에게 데스크톱 알림 (상대가 이 대화 알림을 꺼 뒀으면 안 보냄)
	public void pinChanged(Long conversationId, String myUsername, String myNickname, String partnerUsername,
			PinDto pin) {
		PinEvent event = new PinEvent(conversationId, pin);
		messagingTemplate.convertAndSendToUser(myUsername, "/queue/dm-pin", event);
		if (partnerUsername == null)
			return;
		messagingTemplate.convertAndSendToUser(partnerUsername, "/queue/dm-pin", event);
		if (pin == null || muteService.mutedUsernames(MuteService.DM, conversationId).contains(partnerUsername))
			return;
		messagingTemplate.convertAndSendToUser(partnerUsername, "/queue/notify",
				new NotifyEvent("📌 " + myNickname + "님이 공지를 고정했어요",
						pin.sender() + ": " + Formats.preview(pin.content()), "/dm/" + conversationId,
						"dm-" + conversationId + "-pin", pin.messageId()));
	}
}