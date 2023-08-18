package dev.resolvt.moimemibot.configuration;

import dev.resolvt.moimemibot.telegram.SuggestionsBot;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.List;

@Configuration
@EnableScheduling
public class BotScheduleConfiguration implements SchedulingConfigurer {
    private final List<SuggestionsBot> bots;

    public BotScheduleConfiguration(List<SuggestionsBot> bots) {
        this.bots = bots;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                () -> bots.forEach(SuggestionsBot::flushMediaGroups),
                100
        );
    }
}
