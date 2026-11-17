package com.green.chat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.green.chat.entity.VerificationPurpose;
import com.green.chat.service.UserService;
import com.green.chat.service.VerificationService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/email")
public class EmailApiController {

	private static final Logger log = LoggerFactory.getLogger(EmailApiController.class);

	// 계정 존재 여부를 알려주지 않는 공통 안내 문구
	private static final String GENERIC_SENT = "입력한 정보로 가입된 계정이 있으면 인증번호를 보냈습니다. 메일함(스팸함 포함)을 확인해 주세요.";

	public record EmailRequest(String email, String purpose, String username, String code) {
	}

	public record ApiResult(boolean ok, String message, String data) {
		static ApiResult ok(String message) {
			return new ApiResult(true, message, null);
		}

		static ApiResult ok(String message, String data) {
			return new ApiResult(true, message, data);
		}

		static ApiResult fail(String message) {
			return new ApiResult(false, message, null);
		}
	}

	private final UserService userService;
	private final VerificationService verificationService;

	public EmailApiController(UserService userService, VerificationService verificationService) {
		this.userService = userService;
		this.verificationService = verificationService;
	}

	// 인증번호 발송
	@PostMapping("/send")
	public ResponseEntity<ApiResult> send(@RequestBody EmailRequest req) {
		VerificationPurpose purpose = parsePurpose(req.purpose());
		if (purpose == null)
			return bad("잘못된 요청입니다.");

		String email = VerificationService.normalize(req.email());
		if (!UserService.isValidEmail(email))
			return bad("올바른 이메일 형식이 아닙니다.");

		try {
			if (purpose == VerificationPurpose.SIGNUP) {
				if (userService.existsByEmail(email))
					return bad("이미 사용 중인 이메일입니다.");
				verificationService.sendCode(email, purpose);
				return ResponseEntity.ok(ApiResult.ok("인증번호를 보냈습니다. 5분 안에 입력해 주세요."));
			}
			if (purpose == VerificationPurpose.FIND_ID) {
				if (userService.existsByEmail(email))
					verificationService.sendCode(email, purpose);
				return ResponseEntity.ok(ApiResult.ok(GENERIC_SENT));
			}
			// RESET_PASSWORD
			if (userService.existsByUsernameAndEmail(req.username(), email)) {
				verificationService.sendCode(email, purpose);
			}
			return ResponseEntity.ok(ApiResult.ok(GENERIC_SENT));

		} catch (IllegalArgumentException e) {
			return bad(e.getMessage());
		} catch (MailException e) {
			log.error("메일 발송 실패: {}", email, e);
			return bad("메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
		}
	}

	// 인증번호 확인
	@PostMapping("/verify")
	public ResponseEntity<ApiResult> verify(@RequestBody EmailRequest req, HttpSession session) {
		VerificationPurpose purpose = parsePurpose(req.purpose());
		if (purpose == null)
			return bad("잘못된 요청입니다.");

		String email = VerificationService.normalize(req.email());
		try {
			verificationService.verify(email, purpose, req.code());
		} catch (IllegalArgumentException e) {
			return bad(e.getMessage());
		}

		// 아이디 찾기: 바로 결과 반환
		if (purpose == VerificationPurpose.FIND_ID) {
			return userService.findUsernameByEmail(email)
					.map(u -> ResponseEntity.ok(ApiResult.ok("인증되었습니다.", UserService.maskUsername(u))))
					.orElseGet(() -> bad("가입된 계정을 찾을 수 없습니다."));
		}

		// 회원가입 / 비밀번호 재설정: 세션에 인증 완료 기록
		verificationService.markVerified(session, purpose, email);
		return ResponseEntity.ok(ApiResult.ok("이메일 인증이 완료되었습니다."));
	}

	private VerificationPurpose parsePurpose(String value) {
		try {
			return VerificationPurpose.valueOf(value);
		} catch (Exception e) {
			return null;
		}
	}

	private ResponseEntity<ApiResult> bad(String message) {
		return ResponseEntity.badRequest().body(ApiResult.fail(message));
	}
}