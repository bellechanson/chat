package com.green.chat.service;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import com.green.chat.dto.DmMessageDto;
import com.green.chat.dto.NotifyEvent;
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
}