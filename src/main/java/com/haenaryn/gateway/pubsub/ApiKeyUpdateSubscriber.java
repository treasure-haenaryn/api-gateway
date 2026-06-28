package com.haenaryn.gateway.pubsub;

import com.haenaryn.gateway.domain.apikey.ApiKeyLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyUpdateSubscriber implements MessageListener {

    private final ApiKeyLoader apiKeyLoader;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String keyHash = new String(message.getBody());
        log.info("[ApiKeyUpdateSubscriber] API Key 변경 이벤트 수신 — keyHash: {}...", keyHash.substring(0, 8));
        apiKeyLoader.refreshApiKey(keyHash);
    }
}
