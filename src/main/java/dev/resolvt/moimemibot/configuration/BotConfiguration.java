package dev.resolvt.moimemibot.configuration;

import com.pengrad.telegrambot.TelegramBot;
import dev.resolvt.moimemibot.telegram.SuggestionsBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BotConfiguration {
    private final String token;
    private final Long ownerChatId;
    private final Long targetChannelId;

    public BotConfiguration(@Value("${telegram.token}") String token,
                            @Value("${telegram.owner-chat-id}") Long ownerChatId,
                            @Value("${telegram.target-channel-id}") Long targetChannelId) {
        this.token = token;
        this.ownerChatId = ownerChatId;
        this.targetChannelId = targetChannelId;
    }

    @Bean
    public List<SuggestionsBot> botHandlers() {
        return List.of(
                initializeBotHandler()
        );
    }

    private SuggestionsBot initializeBotHandler() {
        // TODO source data from db
        TelegramBot telegramBot = new TelegramBot(token);
        return new SuggestionsBot(telegramBot, ownerChatId, targetChannelId);
    }
}
