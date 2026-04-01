package com.mtbs.app.config;

import com.mtbs.shared.multitenancy.TenantAwareTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration.
 *
 * Registers a {@link ThreadPoolTaskExecutor} wired with
 * {@link TenantAwareTaskDecorator} so all {@code @Async} methods automatically
 * inherit the TenantContext (tenantId + schemaName) from the calling thread.
 *
 * THREAD POOL SIZING (defaults — tune via application.yaml):
 *   core-pool-size:  5   — always-alive threads for steady-state load
 *   max-pool-size:   20  — burst capacity (e.g. mass email sends)
 *   queue-capacity:  100 — tasks queued before new threads are created
 *
 * OVERRIDE IN YAML:
 *   app.async.core-pool-size: 5
 *   app.async.max-pool-size: 20
 *   app.async.queue-capacity: 100
 *
 * THREAD NAMING:
 *   Threads are named "mtbs-async-{N}" — visible in thread dumps and MDC logs.
 *
 * IMPLEMENTS AsyncConfigurer:
 *   Makes this executor the default for all @Async methods that don't specify
 *   a named executor. NotificationService uses @Async without a qualifier, so
 *   it will use this executor automatically.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mtbs-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // KEY: propagates TenantContext from calling thread → worker thread
        executor.setTaskDecorator(new TenantAwareTaskDecorator());

        executor.initialize();
        return executor;
    }
}