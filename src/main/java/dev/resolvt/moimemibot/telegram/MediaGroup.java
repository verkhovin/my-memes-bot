package dev.resolvt.moimemibot.telegram;

import com.pengrad.telegrambot.model.Message;

import java.util.List;

record MediaGroup(Long senderId, String senderName, String mediaGroupId, List<Message> messages, Integer receivedSeconds) {
    public MediaGroup(List<Message> messages) {
        this(messages.get(0).chat().id(), messages.get(0).chat().username(),
                messages.get(0).mediaGroupId(), messages, messages.get(0).date());
    }
}