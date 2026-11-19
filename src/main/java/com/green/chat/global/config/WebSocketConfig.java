package com.green.chat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.green.chat.global.websocket.RoomSubscribeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final RoomSubscribeInterceptor roomSubscribeInterceptor;

	public WebSocketConfig(RoomSubscribeInterceptor roomSubscribeInterceptor) {
		this.roomSubscribeInterceptor = roomSubscribeInterceptor;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws-chat");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue")
		.setHeartbeatValue(new long[] { 10000, 10000 }) // 서버↔브라우저 10초마다 생존 신호
		.setTaskScheduler(heartbeatScheduler());
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	// 하트비트를 보내는 작업용 스레드
	@Bean
	public ThreadPoolTaskScheduler heartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("ws-heartbeat-");
		scheduler.initialize();
		return scheduler;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(roomSubscribeInterceptor);
	}
}