package dev.resolvt.moimemibot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MemeSuggestionService {
    private final Long ownerChatId;
    private final Long targetChannelId;

    private final TelegramBot telegramBot;

    public MemeSuggestionService(@Value("${telegram.owner-chat-id}") Long ownerChatId,
                                 @Value("${telegram.target-channel-id}") Long targetChannelId, TelegramBot telegramBot) {
        this.ownerChatId = ownerChatId;
        this.targetChannelId = targetChannelId;
        this.telegramBot = telegramBot;
    }

    public void suggestMeme(Message message) {
        if (message.chat().id().equals(ownerChatId)) {
            approveMeme(message);
            return;
        }
        PhotoSize photo = getPhoto(message);
        telegramBot.execute(new SendPhoto(ownerChatId, photo.fileId())
                .caption("from @" + message.from().username())
                .replyMarkup(new InlineKeyboardMarkup(
                        new InlineKeyboardButton("Post")
                                .callbackData("post")
                ))
        );
    }

    public void approveMeme(Message message) {
        PhotoSize photo = getPhoto(message);
        telegramBot.execute(new SendPhoto(targetChannelId, photo.fileId()));
        telegramBot.execute(new DeleteMessage(ownerChatId, message.messageId()));
    }

    private static PhotoSize getPhoto(Message message) {
        return message.photo()[message.photo().length - 1];
    }
}
