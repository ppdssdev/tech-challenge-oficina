package br.com.fiap.techchallenge.oficina.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class BusinessMetricsConfiguration {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler businessMetricsScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("business-metrics-");
        return scheduler;
    }
}
