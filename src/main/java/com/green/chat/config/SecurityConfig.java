package com.green.chat.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.green.chat.security.JpaTokenRepository;
import com.green.chat.security.NotifyingSessionRegistry;
import com.green.chat.service.UserService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final int REMEMBER_SECONDS = 60 * 60 * 24 * 14; // 로그인 유지 14일

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 누가 로그인해 있는지 기록하는 장부 (바뀔 때마다 이벤트 발생)
	@Bean
	public SessionRegistry sessionRegistry(ApplicationEventPublisher publisher) {
		return new NotifyingSessionRegistry(publisher);
	}

	// 세션이 끝나면(로그아웃, 시간 만료) 장부에서도 지워지게 알려 줌
	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry, UserService userService,
			JpaTokenRepository tokenRepository) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/login", "/signup", "/find-id", "/reset-password", "/api/email/**", "/css/**",
						"/js/**", "/manifest.json", "/sw.js", "/offline.html", "/icons/**", // PWA
						"/error")
				.permitAll().requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().authenticated())
				.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/rooms", true)
						// 정지된 계정이면 /login?locked, 그 외 실패는 /login?error
						.failureHandler((request, response,
								ex) -> response.sendRedirect(request.getContextPath()
										+ (ex instanceof LockedException ? "/login?locked" : "/login?error")))
						.permitAll())
				// 로그인 상태 유지: 로그인 화면의 체크박스(name="remember-me")를 켜면 동작
				.rememberMe(remember -> remember.key("chat-remember-me").tokenRepository(tokenRepository)
						.userDetailsService(userService).tokenValiditySeconds(REMEMBER_SECONDS))
				.sessionManagement(session -> session.maximumSessions(-1) // 동시 로그인 수 제한 없음 (장부 기록용으로만 사용)
						.sessionRegistry(sessionRegistry).expiredUrl("/login?expired") // 만료된 세션으로 요청하면 여기로
				).logout(logout -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}
}