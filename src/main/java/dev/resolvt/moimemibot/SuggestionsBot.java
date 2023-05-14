package dev.resolvt.moimemibot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import dev.resolvt.moimemibot.service.MemeSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_NONE;

@Service
public class SuggestionsBot {
    private final TelegramBot telegramBot;
    private final MemeSuggestionService memeSuggestionService;

    private static Logger LOG = LoggerFactory.getLogger(SuggestionsBot.class);

    public SuggestionsBot(TelegramBot telegramBot, MemeSuggestionService memeSuggestionService) {
        this.telegramBot = telegramBot;
        this.memeSuggestionService = memeSuggestionService;
        telegramBot.setUpdatesListener(this::processMessage);
    }

    private int processMessage(List<Update> updates) {
        if(updates.isEmpty()) {
            return CONFIRMED_UPDATES_NONE;
        }
        updates.forEach(this::processUpdate);
        return updates.get(updates.size() - 1).updateId();
    }

    private void processUpdate(Update update) {
        Message message = update.message();
        if (message != null && message.photo() != null) {
            memeSuggestionService.suggestMeme(message);
        }
        CallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery != null && "post".equals(callbackQuery.data())) {
                memeSuggestionService.approveMeme(callbackQuery.message());
        }
    }

}
