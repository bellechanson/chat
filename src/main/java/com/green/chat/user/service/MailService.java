package com.green.chat.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.green.chat.user.entity.VerificationPurpose;

@Service
public class MailService {

	private final JavaMailSender mailSender;
	private final String from;

	public MailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	public void sendCode(String to, VerificationPurpose purpose, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject("[실시간 채팅] " + purpose.getLabel() + " 인증번호");
		message.setText("""
				안녕하세요.

				[실시간 채팅] %s 인증번호는 아래와 같습니다.

				    %s

				5분 안에 입력해 주세요.
				본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
				""".formatted(purpose.getLabel(), code));
		mailSender.send(message);
	}
}