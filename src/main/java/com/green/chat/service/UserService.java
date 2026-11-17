package com.green.chat.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.green.chat.dto.SignupForm;
import com.green.chat.entity.ChatUser;
import com.green.chat.repository.ChatMessageRepository;
import com.green.chat.repository.ChatUserRepository;
import com.green.chat.repository.RememberTokenRepository;
import com.green.chat.security.LoginUser;

@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

	private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$";

	private final ChatUserRepository userRepository;
	private final ChatMessageRepository messageRepository;
	private final RememberTokenRepository rememberTokenRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(ChatUserRepository userRepository, ChatMessageRepository messageRepository,
			RememberTokenRepository rememberTokenRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.messageRepository = messageRepository;
		this.rememberTokenRepository = rememberTokenRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		ChatUser user = userRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + username));
		return LoginUser.from(user);
	}

	public ChatUser getUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	// ── 회원가입 ──
	@Transactional
	public void register(SignupForm form) {
		String username = trim(form.username());
		String nickname = trim(form.nickname());
		String email = VerificationService.normalize(form.email());

		if (!username.matches("^[a-zA-Z0-9]{4,20}$")) {
			throw new IllegalArgumentException("아이디는 영문/숫자 4~20자로 입력하세요.");
		}
		validatePassword(form.password(), form.passwordConfirm());
		validateNickname(nickname);
		if (!isValidEmail(email)) {
			throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
		}
		if (userRepository.existsByUsername(username)) {
			throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
		}
		if (userRepository.existsByEmail(email)) {
			throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
		}

		userRepository.save(new ChatUser(username, passwordEncoder.encode(form.password()), nickname, email));
	}

	// ── 닉네임 변경 (예전 메시지도 함께 변경) ──
	@Transactional
	public ChatUser changeNickname(Long userId, String newNickname) {
		String nickname = trim(newNickname);
		validateNickname(nickname);

		ChatUser user = getUser(userId);
		String oldNickname = user.getNickname();
		if (nickname.equals(oldNickname)) {
			throw new IllegalArgumentException("현재 닉네임과 같습니다.");
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
		}

		user.changeNickname(nickname); // 변경 감지로 UPDATE
		messageRepository.updateSender(oldNickname, nickname); // 예전 메시지 일괄 변경
		return user;
	}

	// ── 비밀번호 변경 (로그인 상태, 현재 비밀번호 확인) ──
	@Transactional
	public ChatUser changePassword(Long userId, String currentPassword, String newPassword, String newPasswordConfirm) {
		ChatUser user = getUser(userId);
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
		}
		validatePassword(newPassword, newPasswordConfirm);
		if (passwordEncoder.matches(newPassword, user.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호와 다른 비밀번호를 입력하세요.");
		}
		user.changePassword(passwordEncoder.encode(newPassword));
		return user;
	}

	// ── 비밀번호 재설정 (이메일 인증 후) → 모든 기기의 로그인 유지 해제 ──
	@Transactional
	public void resetPassword(String username, String email, String password, String passwordConfirm) {
		validatePassword(password, passwordConfirm);
		ChatUser user = userRepository.findByUsernameAndEmail(trim(username), email)
				.orElseThrow(() -> new IllegalArgumentException("아이디와 이메일이 일치하는 계정이 없습니다."));
		user.changePassword(passwordEncoder.encode(password));
		rememberTokenRepository.deleteByUsername(user.getUsername());
	}

	public boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	public boolean existsByUsernameAndEmail(String username, String email) {
		return userRepository.findByUsernameAndEmail(trim(username), email).isPresent();
	}

	public Optional<String> findUsernameByEmail(String email) {
		return userRepository.findByEmail(email).map(ChatUser::getUsername);
	}

	// chulsoo → chul***
	public static String maskUsername(String username) {
		int visible = (username.length() + 1) / 2;
		return username.substring(0, visible) + "*".repeat(username.length() - visible);
	}

	public static boolean isValidEmail(String email) {
		return email != null && email.length() <= 100 && email.matches(EMAIL_REGEX);
	}

	private void validatePassword(String password, String confirm) {
		if (password == null || password.length() < 6 || password.length() > 50) {
			throw new IllegalArgumentException("비밀번호는 6~50자로 입력하세요.");
		}
		if (!password.equals(confirm)) {
			throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
		}
	}

	private void validateNickname(String nickname) {
		if (nickname.length() < 2 || nickname.length() > 20) {
			throw new IllegalArgumentException("닉네임은 2~20자로 입력하세요.");
		}
	}

	private String trim(String v) {
		return v == null ? "" : v.trim();
	}
}