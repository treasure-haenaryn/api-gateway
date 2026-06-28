package com.haenaryn.gateway.config;

import com.haenaryn.gateway.pubsub.ApiKeyUpdateSubscriber;
import com.haenaryn.gateway.pubsub.CacheEventPublisher;
import com.haenaryn.gateway.pubsub.RouteUpdateSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RouteUpdateSubscriber routeUpdateSubscriber,
            ApiKeyUpdateSubscriber apiKeyUpdateSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 라우팅 변경 이벤트 구독
        container.addMessageListener(
                new MessageListenerAdapter(routeUpdateSubscriber),
                new PatternTopic(CacheEventPublisher.ROUTE_UPDATED_CHANNEL)
        );

        // API Key 변경 이벤트 구독
        container.addMessageListener(
                new MessageListenerAdapter(apiKeyUpdateSubscriber),
                new PatternTopic(CacheEventPublisher.APIKEY_UPDATED_CHANNEL)
        );

        return container;
    }
}
