package dev.resolvt.moimemibot.configuration;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfiguration {
    @Bean
    public TelegramBot telegramBot(@Value("${telegram.token}") String token) {
        return new TelegramBot.Builder(token).build();
    }
}
