package com.hlv.HalvaBot;
import com.hlv.HalvaBot.Model.UserState;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig botConfig;
    private Map<Long, UserState> userStates = new HashMap<>();
    private Map<Long, String> userData = new HashMap<>();

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String messageText = message.getText();
            Long chatId = message.getChatId();

            // Получаем текущее состояние пользователя, по умолчанию DEFAULT
            UserState userState = userStates.getOrDefault(chatId, UserState.DEFAULT);

            if (messageText.equals("/start")) {
                startCommandReceived(chatId);
            } else if (messageText.equals("\uD83D\uDCB8 Оставь заявку на халву")) { // Если пользователь отправляет смайлик
                userStates.put(chatId, UserState.AWAITING_NAME);
                sendMessage(chatId, "Пожалуйста, введите ваше имя:");
            } else if (userState == UserState.AWAITING_NAME) {
                // Получаем имя и переходим к следующему состоянию
                userData.put(chatId, "Имя: " + messageText);
                userStates.put(chatId, UserState.AWAITING_CONTACTS);
                sendMessage(chatId, "Пожалуйста, введите ваши контактные данные:");
            } else if (userState == UserState.AWAITING_CONTACTS) {
                // Получаем контакты, сохраняем данные в файл и возвращаем состояние по умолчанию
                userData.put(chatId, userData.get(chatId) + "\nКонтакты: " + messageText);
                saveDataToFile(chatId);
                userStates.put(chatId, UserState.DEFAULT);
                sendMessage(chatId, "Спасибо! Ваши данные сохранены.");
            } else {
                sendMessage(chatId, "Я вас не понял. Для начала введите /start.");
            }
        }
    }

    private void startCommandReceived(Long chatId) {
        String answer = "hello";
        sendMessageWithKeyboard(chatId, answer);
    }

    private void sendMessage(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {

        }
    }
    private void saveDataToFile(Long chatId) {
        String userDataToSave = userData.get(chatId);
        String fileName = "userData.txt"; // Имя файла для сохранения данных
        try (FileWriter writer = new FileWriter(fileName, true)) {
            writer.write("Chat ID: " + chatId + "\n" + userDataToSave + "\n\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void sendMessageWithKeyboard(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("\uD83D\uDCB8 Оставь заявку на халву"));

        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);

        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
