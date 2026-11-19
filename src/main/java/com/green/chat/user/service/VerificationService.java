package com.green.chat.user.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.user.entity.EmailVerification;
import com.green.chat.user.entity.VerificationPurpose;
import com.green.chat.user.repository.EmailVerificationRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class VerificationService {

	private static final int EXPIRE_MINUTES = 5; // 인증번호 유효시간
	private static final int MAX_ATTEMPTS = 5; // 최대 입력 시도
	private static final int RESEND_SECONDS = 60; // 재발송 대기시간
	private static final String SESSION_KEY = "verifiedEmail.";

	private final EmailVerificationRepository repository;
	private final MailService mailService;
	private final SecureRandom random = new SecureRandom();

	public VerificationService(EmailVerificationRepository repository, MailService mailService) {
		this.repository = repository;
		this.mailService = mailService;
	}

	// 이메일 정리: 앞뒤 공백 제거 + 소문자
	public static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

	// 인증번호 생성 → 저장 → 메일 발송 (발송 실패 시 저장도 취소됨)
	@Transactional
	public void sendCode(String email, VerificationPurpose purpose) {
		repository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose).ifPresent(last -> {
			if (last.getCreatedAt().plusSeconds(RESEND_SECONDS).isAfter(LocalDateTime.now())) {
				throw new IllegalArgumentException("인증번호는 60초 후에 다시 요청할 수 있습니다.");
			}
		});

		repository.deleteByEmailAndPurpose(email, purpose); // 이전 번호 무효화

		String code = String.format("%06d", random.nextInt(1_000_000)); // 000000 ~ 999999
		repository.save(new EmailVerification(email, purpose, code, EXPIRE_MINUTES));
		mailService.sendCode(email, purpose, code);
	}

	// 인증번호 확인 (틀린 횟수는 저장돼야 하므로 IllegalArgumentException은 롤백하지 않음)
	@Transactional(noRollbackFor = IllegalArgumentException.class)
	public void verify(String email, VerificationPurpose purpose, String code) {
		EmailVerification v = repository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
				.orElseThrow(() -> new IllegalArgumentException("인증번호를 먼저 요청해 주세요."));

		if (v.isExpired()) {
			repository.delete(v);
			throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해 주세요.");
		}
		if (v.getAttempts() >= MAX_ATTEMPTS) {
			repository.delete(v);
			throw new IllegalArgumentException("입력 횟수를 초과했습니다. 다시 요청해 주세요.");
		}
		if (!v.matches(code == null ? "" : code.trim())) {
			v.increaseAttempts();
			throw new IllegalArgumentException("인증번호가 일치하지 않습니다. (" + v.getAttempts() + "/" + MAX_ATTEMPTS + ")");
		}
		repository.delete(v); // 한 번 쓴 번호는 삭제
	}

	// ── 인증 완료 상태를 세션에 기록/확인 ──
	public void markVerified(HttpSession session, VerificationPurpose purpose, String email) {
		session.setAttribute(SESSION_KEY + purpose.name(), email);
	}

	public boolean isVerified(HttpSession session, VerificationPurpose purpose, String email) {
		return !email.isEmpty() && email.equals(session.getAttribute(SESSION_KEY + purpose.name()));
	}

	public void clear(HttpSession session, VerificationPurpose purpose) {
		session.removeAttribute(SESSION_KEY + purpose.name());
	}
}