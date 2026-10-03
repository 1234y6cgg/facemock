package com.mockinterview.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.time.Clock;
@Configuration
public class ReviewConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class) public Clock reviewClock(){return Clock.systemUTC();}
}
