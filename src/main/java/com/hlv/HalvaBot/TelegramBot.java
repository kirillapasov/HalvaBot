package com.hlv.HalvaBot;
import com.hlv.HalvaBot.Model.UserState;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


//Todo Добавить возможность загружать сканы документов для оформления,
// возможность рассчитывать рассрочку, запись пользователей в БД
@Component
@AllArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig botConfig;
    private Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private Map<Long, Map<String, String>> userData = new  ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();


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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            UserState userState = userStates.getOrDefault(chatId, UserState.DEFAULT);


            if (message.hasPhoto() && userState == UserState.AWAITING_DOCUMENT) {
                saveDocumentPhoto(chatId, message);
                userStates.put(chatId, UserState.DEFAULT);
                sendMessageWithKeyboard(chatId, "Идём дальше");
                return;
            }

            if (message.hasText()) {
                String messageText = message.getText();

                switch (messageText) {
                    case "/start" -> startCommandReceived(chatId);

                    case "\uD83D\uDCB8 Оставь заявку на халву" -> {
                        userStates.put(chatId, UserState.AWAITING_NAME);
                        sendMessage(chatId, "Пожалуйста, введите ваше ФИО:");
                    }

                    case "\uD83D\uDCB8 Оставь заявку на рефинансирование" -> {
                        userStates.put(chatId, UserState.AWAITING_NAME_REFINANCE);
                        sendMessage(chatId, "Пожалуйста, введите ваше ФИО для заявки на рефинансирование:");
                    }

                    case "ℹ️ Узнать о халве" -> {
                        String HALVAINFO = """
                                Халва от Совкомбанка — универсальная карта, сочетающая лучшие качества дебетовой
                                 и кредитной карты.

                                1. Свои и заемные средства в одном месте: Храните зарплату и накопления, оплачивайте
                                 покупки и при необходимости используйте заемные средства на выгодных условиях. Все
                                  в одной карте.

                                2. Покупки в рассрочку без переплат: В 250,000+ магазинах-партнерах можно оплатить
                                 товар частями, без процентов. Срок рассрочки — до трех лет, что дает свободу в выборе
                                  крупных покупок.

                                3. Доход на остаток и кешбэк: Ежемесячные проценты на остаток собственных средств и
                                 кешбэк за покупки.

                                4. Простота получения и использования: Оформление онлайн, поддержка MirPay для оплаты
                                 смартфоном, и бесплатное обслуживание.

                                Халва — лучшее из двух миров, объединяющее возможности дебетовой и кредитной карт в 
                                одном удобном продукте. Узнать подробнее: https://halvacard.ru/cards/main/halva""";

                        sendMessage(chatId, HALVAINFO);
                        sendPhoto(chatId, "C:\\Users\\Kiril\\Desktop\\HalvaBot\\src\\main\\resources\\halva.jpg");
                        sendMessageWithKeyboard(chatId, "Идём дальше");
                    }

                    case "📄 Отправить документы сотруднику" -> {
                        userStates.put(chatId, UserState.AWAITING_DOCUMENT);
                        sendMessage(chatId, "Пожалуйста, отправьте фото вашего документа:");
                    }
                    case "💳 Рассчитать ежемесячный платеж" -> {
                        userStates.put(chatId, UserState.AWAITING_PAYMENT_AMOUNT);
                        sendMessage(chatId, "Пожалуйста, введите сумму покупки:");
                    }

                    default -> handleUserState(chatId, userState, messageText);
                }
            }
        }
    }

    private void handleUserState(Long chatId, UserState userState, String messageText) {
        switch (userState) {
            case AWAITING_NAME -> {
                saveUserData(chatId, "ФИО", messageText);
                userStates.put(chatId, UserState.AWAITING_PHONE);
                sendMessage(chatId, "Пожалуйста, введите ваш номер телефона:");
            }
            case AWAITING_PHONE -> {
                saveUserData(chatId, "Номер телефона", messageText);
                userStates.put(chatId, UserState.AWAITING_WORKPLACE);
                sendMessage(chatId, "Пожалуйста, введите ваше место работы:");
            }
            case AWAITING_WORKPLACE -> {
                saveUserData(chatId, "Место работы", messageText);
                userStates.put(chatId, UserState.AWAITING_POSITION);
                sendMessage(chatId, "Пожалуйста, введите вашу должность:");
            }
            case AWAITING_POSITION -> {
                saveUserData(chatId, "Должность", messageText);
                userStates.put(chatId, UserState.AWAITING_MARITAL_STATUS);
                sendMessage(chatId, "Укажите семейное положение (женат/не женат):");
            }
            case AWAITING_MARITAL_STATUS -> {
                saveUserData(chatId, "Семейное положение", messageText);
                userStates.put(chatId, UserState.AWAITING_INCOME);
                sendMessage(chatId, "Пожалуйста, введите ваш среднемесячный доход:");
            }
            case AWAITING_INCOME -> {
                saveUserData(chatId, "Среднемесячный доход", messageText);
                userStates.put(chatId, UserState.AWAITING_CREDIT_LIMIT);
                sendMessage(chatId, "Пожалуйста, введите желаемый лимит рассрочки:");
            }
            case AWAITING_CREDIT_LIMIT -> {
                saveUserData(chatId, "Желаемый лимит рассрочки", messageText);
                saveDataToFile(chatId);
                userStates.put(chatId, UserState.DEFAULT);
                sendMessage(chatId, "Спасибо! Ваша заявка на Халву сохранена. Мы свяжемся с вами в ближайшее время.");
                sendMessageWithKeyboard(chatId, "Идём дальше");
            }

            case AWAITING_NAME_REFINANCE -> {
                saveUserData(chatId, "ФИО", messageText);
                userStates.put(chatId, UserState.AWAITING_PHONE_REFINANCE);
                sendMessage(chatId, "Пожалуйста, введите ваш номер телефона:");
            }
            case AWAITING_PHONE_REFINANCE -> {
                saveUserData(chatId, "Номер телефона", messageText);
                userStates.put(chatId, UserState.AWAITING_ACCOUNT_NUMBERS);
                sendMessage(chatId, "Введите номера счетов для рефинансирования (через запятую):");
            }
            case AWAITING_ACCOUNT_NUMBERS -> {
                saveUserData(chatId, "Счета для рефинансирования", messageText);
                userStates.put(chatId, UserState.AWAITING_REFINANCE_AMOUNT);
                sendMessage(chatId, "Введите общую сумму для рефинансирования:");
            }
            case AWAITING_REFINANCE_AMOUNT -> {
                saveUserData(chatId, "Сумма для рефинансирования", messageText);
                saveDataToFile(chatId);
                userStates.put(chatId, UserState.DEFAULT);
                sendMessage(chatId, "Спасибо! Ваша заявка на рефинансирование сохранена. Мы свяжемся с вами в ближайшее время.");
                sendMessageWithKeyboard(chatId, "Идём дальше");
            }
            case AWAITING_PAYMENT_AMOUNT -> {
                saveUserData(chatId, "Сумма покупки", messageText);
                userStates.put(chatId, UserState.AWAITING_PAYMENT_MONTHS);
                sendMessage(chatId, "Пожалуйста, введите количество месяцев рассрочки:");
            }
            case AWAITING_PAYMENT_MONTHS -> {
                saveUserData(chatId, "Количество месяцев", messageText);
                userStates.put(chatId, UserState.AWAITING_PAYMENT_SUBSCRIPTION);
                sendMessage(chatId, "Есть ли у вас подписка? (да/нет):");
            }
            case AWAITING_PAYMENT_SUBSCRIPTION -> {
                saveUserData(chatId, "Подписка", messageText);
                userStates.put(chatId, UserState.AWAITING_PAYMENT_SUPER_OPTION);
                sendMessage(chatId, "Есть ли у вас супер опция? (да/нет):");
            }
            case AWAITING_PAYMENT_SUPER_OPTION -> {
                saveUserData(chatId, "Супер опция", messageText);
                userStates.put(chatId, UserState.AWAITING_PAYMENT_CARD_TYPE);
                sendMessage(chatId, "Какой тип карты? (обычная/социальная):");
            }
            case AWAITING_PAYMENT_CARD_TYPE -> {
                saveUserData(chatId, "Тип карты", messageText);
                calculateMonthlyPayment(chatId); // После всех данных - расчет
                userStates.put(chatId, UserState.DEFAULT);
            }
            default -> sendMessage(chatId, "Я вас не понял. Для начала введите /start.");
        }
    }

    private void startCommandReceived(Long chatId) {
        String answer = "Здравствуйте, используйте команды ниже," +
                " чтобы оставить заявку на карту рассрочки халва или рефинансирование." +
                " Кроме этого вы можете получить информацию о продукте или отправить документы сотруднику";
        sendMessageWithKeyboard(chatId, answer);
    }

    private void sendMessage(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void saveDataToFile(Long chatId) {
        Map<String, String> userApplicationData = userData.get(chatId);
        if (userApplicationData != null) {
            String fileName = "userApplications.txt";
            try (FileWriter writer = new FileWriter(fileName, true)) {
                writer.write("Заявка от пользователя с Chat ID: " + chatId + "\n");
                userApplicationData.forEach((key, value) -> {
                    try {
                        writer.write(key + ": " + value + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                writer.write("\n-----------------\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveUserData(Long chatId, String key, String value) {
        userData.computeIfAbsent(chatId, k -> new HashMap<>()).put(key, value);
    }
    private void sendMessageWithKeyboard(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();
        row1.add(new KeyboardButton("\uD83D\uDCB8 Оставь заявку на халву"));
        row1.add(new KeyboardButton("\uD83D\uDCB8 Оставь заявку на рефинансирование"));
        row2.add(new KeyboardButton("ℹ️ Узнать о халве"));
        row2.add(new KeyboardButton("📄 Отправить документы сотруднику"));
        row3.add(new KeyboardButton("\uD83D\uDCB3 Рассчитать ежемесячный платеж"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

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


    private void sendPhoto(Long chatId, String filePath) {
        File imageFile = new File(filePath);
        if (!imageFile.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }
        InputFile inputFile = new InputFile(imageFile);
        SendPhoto sendPhotoRequest = new SendPhoto();
        sendPhotoRequest.setChatId(String.valueOf(chatId));
        sendPhotoRequest.setPhoto(inputFile);

        try {
            execute(sendPhotoRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveDocumentPhoto(Long chatId, Message message) {
        executorService.submit(() -> {
            try {
                List<PhotoSize> photos = message.getPhoto();
                PhotoSize photo = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElse(null);

                if (photo == null) {
                    sendMessage(chatId, "Не удалось получить фотографию.");
                    return;
                }

                String fileId = photo.getFileId();
                org.telegram.telegrambots.meta.api.objects.File file = execute(new GetFile(fileId));
                String filePath = file.getFilePath();

                if (filePath == null || filePath.isEmpty()) {
                    sendMessage(chatId, "Не удалось получить путь к файлу.");
                    return;
                }
                System.out.println("File path: " + filePath);

                InputStream fileStream = downloadFileAsStream(file);

                String fileName = "документ" + chatId + ".jpg";

                java.io.File outputFile = new java.io.File("C:\\Users\\Kiril\\Desktop\\Сканы доков\\" + fileName);
                if (outputFile.exists()) {
                    int counter = 1;
                    while (outputFile.exists()) {
                        String newFileName = fileName.replace(".jpg", " (" + counter + ").jpg");
                        outputFile = new java.io.File("C:\\Users\\Kiril\\Desktop\\Сканы доков\\скан" + newFileName);
                        counter++;
                    }
                }

                System.out.println("Saving file to: " + outputFile.getAbsolutePath());

                try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                    System.out.println("File saved successfully.");
                }

                sendMessage(chatId, "Документ успешно получен и сохранен.");
            } catch (TelegramApiException | IOException e) {
                e.printStackTrace();
                sendMessage(chatId, "Ошибка при загрузке документа. Попробуйте снова.");
            }
        });
    }
    private void calculateMonthlyPayment(Long chatId) {
        Map<String, String> userApplicationData = userData.get(chatId);

        if (userApplicationData != null) {
            try {
                // Получаем введенные данные
                double amount = Double.parseDouble(userApplicationData.get("Сумма покупки"));
                int months = Integer.parseInt(userApplicationData.get("Количество месяцев"));
                boolean hasSubscription = "да".equalsIgnoreCase(userApplicationData.get("Подписка"));
                boolean hasSuperOption = "да".equalsIgnoreCase(userApplicationData.get("Супер опция"));
                String cardType = userApplicationData.get("Тип карты");

                // Проверка на наличие супер опции без подписки
                if (hasSuperOption && !hasSubscription) {
                    sendMessage(chatId, "Ошибка! Супер опция доступна только при наличии подписки.");
                    return;
                }

                // Стоимость подписки и супер опции
                double subscriptionCost = hasSubscription ? 399 : 0;
                double superOptionCost = (hasSuperOption && hasSubscription) ? 399 : 0;

                // Скидка для социальной карты
                if ("социальная".equalsIgnoreCase(cardType)) {
                    subscriptionCost -= 100;
                    superOptionCost -= 100;
                }

                // Рассчитываем ежемесячный платеж
                double monthlyPayment = (amount / months) + subscriptionCost + superOptionCost;

                // Отправка результата
                sendMessage(chatId, String.format("Ваш ежемесячный платеж: %.2f руб.", monthlyPayment));
                sendMessage(chatId, "Если вы новый пользователь в первый месяц подписка и супер опция для вас бесплатны");
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Ошибка в введенных данных. Пожалуйста, введите корректные значения.");
            }
        } else {
            sendMessage(chatId, "Не удалось получить данные для расчета.");
        }
    }
}
