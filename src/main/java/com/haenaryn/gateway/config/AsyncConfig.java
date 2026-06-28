package com.haenaryn.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 비동기 DB 작업 전용 스레드풀
    @Bean(name = "dbTaskExecutor")
    public Executor dbTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);     // 기본 스레드 수
        executor.setMaxPoolSize(10);     // 최대 스레드 수 (HikariCP 기본 10과 맞춤)
        executor.setQueueCapacity(100);  // 대기 큐 (초과 시 CallerRunsPolicy)
        executor.setThreadNamePrefix("db-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // 종료 시 진행 중 작업 완료 대기
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
