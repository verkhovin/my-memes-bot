package dev.resolvt.moimemibot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_NONE;

public class SuggestionsBot {
    private final TelegramBot telegramBot;
    private final List<Long> ownerChatIds;
    private final Long targetChannelId;
    private final Long ownerRecommendationChatId;

    private final Map<String, MediaGroup> mediaGroupHolder = new ConcurrentHashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(SuggestionsBot.class);

    public SuggestionsBot(TelegramBot telegramBot,
                          @Value("${telegram.senderId-chat-id}") List<Long> ownerChatIds,
                          @Value("${telegram.target-channel-id}") Long targetChannelId) {
        this.telegramBot = telegramBot;
        this.ownerChatIds = ownerChatIds;
        this.targetChannelId = targetChannelId;
        ownerRecommendationChatId = ownerChatIds.get(0);
        telegramBot.setUpdatesListener(this::processUpdates);
    }

    public void flushMediaGroups() {
        long now = Instant.now().getEpochSecond();
        List<MediaGroup> mediaGroupsToSend = mediaGroupHolder.values().stream()
                .filter(mediaGroup -> now - mediaGroup.receivedSeconds() > 2)
                .toList();
        mediaGroupsToSend.forEach(mediaGroup -> {
            processMessages(mediaGroup.messages());
            mediaGroupHolder.remove(mediaGroup.mediaGroupId());
        });
    }

    private int processUpdates(List<Update> updates) {
        if (updates.isEmpty()) {
            return CONFIRMED_UPDATES_NONE;
        }

        List<Message> messages = updates.stream()
                .map(Update::message)
                .filter(message -> message != null && message.mediaGroupId() == null)
                .toList();
        List<Message> mediaGroupMessages = updates.stream()
                .map(Update::message)
                .filter(message -> message != null && message.mediaGroupId() != null)
                .toList();
        List<CallbackQuery> callbackQueries = updates.stream()
                .map(Update::callbackQuery)
                .filter(Objects::nonNull)
                .toList();

        processMessages(messages);
        holdMediaGroupMessages(mediaGroupMessages);
        processCallBackQueries(callbackQueries);

        return updates.get(updates.size() - 1).updateId();
    }

    private void holdMediaGroupMessages(List<Message> messages) {
        Map<String, List<Message>> messagesByMediaGroup = messages.stream()
                .collect(Collectors.groupingBy(Message::mediaGroupId));
        messagesByMediaGroup.values()
                .forEach(messageGroup -> {
                    MediaGroup mediaGroup = new MediaGroup(messageGroup);
                    mediaGroupHolder.merge(mediaGroup.mediaGroupId(), mediaGroup, (holdedMediaGroup, discoveredMediaGroupPart) -> new MediaGroup(
                            Stream.concat(holdedMediaGroup.messages().stream(), discoveredMediaGroupPart.messages().stream()).toList()
                    ));
                });
    }

    private void processMessages(List<Message> messages) {
        List<MediaGroup> mediaGroups = messages.stream().filter(message -> message.mediaGroupId() != null)
                .collect(Collectors.groupingBy(Message::mediaGroupId))
                .values().stream().map(MediaGroup::new).toList();
        List<Message> singleMessages = messages.stream().filter(message -> message.mediaGroupId() == null)
                .toList();

        mediaGroups.forEach(mediaGroup -> {
            if (isOwner(mediaGroup.senderId())) {
                // if owner sends media group -> send to channel immediately in separate messages
                mediaGroup.messages().forEach(this::publishToChannel);
            } else {
                // otherwise send to channel as mediagroup
                try {
                    buildMediaGroupToSuggest(mediaGroup).forEach(telegramBot::execute);
                } catch (RuntimeException e) {
                    //
                }

            }
        });

        singleMessages.forEach(message -> {
            LOG.info("Got message {}", message);
            if (isOwner(message.chat().id())) {
                publishToChannel(message);
            } else {
                telegramBot.execute(buildMediaMessageSendRequest(
                        message,
                        ownerRecommendationChatId,
                        buildSuggestionMessageCaption(message),
                        buildPostKeyboard()
                ));
            }

        });

    }

    @NotNull
    private List<BaseRequest<?, ?>> buildMediaGroupToSuggest(MediaGroup mediaGroup) {
        Message firstMessage = mediaGroup.messages().get(0);
        String caption = buildSuggestionMessageCaption(firstMessage);
        InputMedia[] mediaObjects = mediaGroup.messages().stream().map(mediaMessage -> {
            if (mediaMessage.photo() != null) {
                return new InputMediaPhoto(getPhoto(mediaMessage).fileId());
            } else if (mediaMessage.video() != null) {
                return new InputMediaVideo(mediaMessage.video().fileId());
            } else if (mediaMessage.animation() != null) {
                return new InputMediaAnimation(mediaMessage.animation().fileId());
            } else if (mediaMessage.document() != null) {
                return new InputMediaDocument(mediaMessage.document().fileId());
            } else if (mediaMessage.audio() != null) {
                return new InputMediaDocument(mediaMessage.audio().fileId());
            } else {
                telegramBot.execute(new SendMessage(ownerRecommendationChatId, "Unsupported media from @" + mediaGroup.senderId()));
                throw new RuntimeException("Unsupported media " + ownerRecommendationChatId);
            }
        }).toArray(InputMedia[]::new);
        mediaObjects[0] = mediaObjects[0].caption(caption);
        return List.of(
                new SendMediaGroup(ownerRecommendationChatId, mediaObjects)
        );
    }

    private void processCallBackQueries(List<CallbackQuery> callbackQueries) {
        callbackQueries.forEach(callbackQuery -> {
            if("post".equals(callbackQuery.data())) {
                publishToChannel(callbackQuery.message());
            }
        });
    }

    private InlineKeyboardMarkup buildPostKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("Post").callbackData("post")
        );
    }

    @NotNull
    private static String buildSuggestionMessageCaption(Message message) {
        String sign = "from @" + message.from().username();
        if (message.caption() == null) {
            return sign;
        }
        return message.caption() + "\n" + sign;
    }

    private boolean isOwner(Long chatId) {
        return ownerChatIds.contains(chatId);
    }

    public void publishToChannel(Message message) {
        final BaseRequest<?, ?> sendRequest = buildMediaMessageSendRequest(message, targetChannelId, message.caption(), null);
        telegramBot.execute(sendRequest);
        telegramBot.execute(new DeleteMessage(ownerRecommendationChatId, message.messageId()));
    }

    private BaseRequest<?, ?> buildMediaMessageSendRequest(Message message, long channelId, String caption, Keyboard replyMarkup) {
        final BaseRequest<?, ?> sendRequest;
        if (message.photo() != null) {
            sendRequest = new SendPhoto(channelId, getPhoto(message).fileId());
        } else if (message.video() != null) {
            sendRequest = new SendVideo(channelId, message.video().fileId());
        } else if (message.animation() != null) {
            sendRequest = new SendAnimation(channelId, message.animation().fileId());
        } else {
            sendRequest = new SendMessage(message.chat().id(), "Message contained unsupported media type");
        }
        if (caption != null) {
            sendRequest.getParameters().put("caption", caption);
        }
        if (replyMarkup != null) {
            sendRequest.getParameters().put("reply_markup", replyMarkup);
        }
        return sendRequest;
    }

    private static PhotoSize getPhoto(Message message) {
        return message.photo()[message.photo().length - 1];
    }
}
