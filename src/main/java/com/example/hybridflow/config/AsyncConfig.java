package com.example.hybridflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures a dedicated thread pool for Gurobi schedule generation,
 * and exposes a shared ObjectMapper bean for JSON serialisation of fairness scores.
 *
 * Why a named executor instead of the default SimpleAsyncTaskExecutor?
 * - Gurobi is CPU-intensive; unbounded thread creation would cause contention.
 * - Core pool = 2 allows two parallel solves (enough for dev; tune for prod).
 * - CallerRunsPolicy: if the queue is full, the HTTP request thread runs the
 *   job synchronously rather than rejecting it — safe fallback, never drops.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Shared ObjectMapper with Java 8 date/time support.
     * Used by ScheduleGenerationPersistenceService and ScheduleOptimizationRunService
     * to serialise/deserialise fairness score JSON stored in the DB.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(name = "gurobiExecutor")
    public Executor gurobiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("gurobi-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
