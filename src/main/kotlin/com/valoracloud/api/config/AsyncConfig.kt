package com.valoracloud.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    /**
     * Dedicated thread pool for provisioning jobs.
     * Each server provisioning can take 15-25 minutes (Contabo polling + SSH),
     * so we isolate them from the main HTTP thread pool.
     */
    @Bean(name = ["provisioningExecutor"])
    fun provisioningExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 20
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("provisioning-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }
}

