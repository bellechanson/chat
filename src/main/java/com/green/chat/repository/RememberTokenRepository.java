package com.green.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.green.chat.entity.RememberToken;

public interface RememberTokenRepository extends JpaRepository<RememberToken, String> {

	// 이 회원의 모든 기기 로그인 유지 해제
	@Modifying
	@Query("delete from RememberToken t where t.username = :username")
	void deleteByUsername(@Param("username") String username);
}