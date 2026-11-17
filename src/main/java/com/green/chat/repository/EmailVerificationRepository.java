package com.green.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.green.chat.entity.EmailVerification;
import com.green.chat.entity.VerificationPurpose;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	// 해당 이메일·용도의 가장 최근 인증번호
	Optional<EmailVerification> findTopByEmailAndPurposeOrderByIdDesc(String email, VerificationPurpose purpose);

	void deleteByEmailAndPurpose(String email, VerificationPurpose purpose);
}