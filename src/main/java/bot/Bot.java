package bot;
import bot.commands.AppBotCommand;
import bot.commands.BotCommonCommands;
import bot.functions.FilterOperation;
import bot.functions.ImageOperation;
import bot.utils.ImageUtils;
import bot.utils.PhotoMessageUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Bot extends TelegramLongPollingBot {
   HashMap<String, Message> messages = new HashMap<>();

    @Override
    public String getBotToken() {
        return "7099660916:AAE4uEwBGXOBLbjtK53GnTNNJVwj8XP_eP8";
    }

    @Override
    public String getBotUsername() {
        return "maksimjavatestbot";
    }
    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        try {
            SendMessage responseTextMessage = runCommonCommand(message);
            if (responseTextMessage != null) {
                execute(responseTextMessage);
                return;
            }
            responseTextMessage = runPhotoMessage(message);
            if (responseTextMessage != null) {
                execute(responseTextMessage);
                return;
            }
            Object responseMediaMessage = runPhotoFilter(message);
            if (responseMediaMessage != null) {
                if (responseMediaMessage instanceof SendMediaGroup) {
                    execute((SendMediaGroup) responseMediaMessage);
                }else if (responseMediaMessage instanceof SendMessage) {
                    execute((SendMessage) responseMediaMessage);
                }
            }
        } catch (InvocationTargetException | IllegalAccessException | TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private SendMessage runCommonCommand(Message message) throws InvocationTargetException, IllegalAccessException {
        String text = message.getText();
        BotCommonCommands commands = new BotCommonCommands();
        Method[] classMethods = commands.getClass().getDeclaredMethods();
        for (Method method : classMethods) {
            if (method.isAnnotationPresent(AppBotCommand.class)) {
                AppBotCommand command = method.getAnnotation(AppBotCommand.class);
                if (command.name().equals(text)) {
                    method.setAccessible(true);
                    String responseText = (String) method.invoke(commands);
                    if (responseText != null) {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(message.getChatId());
                        sendMessage.setText(responseText);
                        return sendMessage;
                    }
                }
            }
        }
        return null;
    }

    private SendMessage runPhotoMessage(Message message) {
        List<File> files = getFilesByMessage(message);
        if (files.isEmpty()) {
            return null;
        }
        String chatId = message.getChatId().toString();
        messages.put(chatId, message);
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        ArrayList<KeyboardRow> allKeyboardRows = new ArrayList<>(getKeyboardsRows(FilterOperation.class));

        replyKeyboardMarkup.setKeyboard(allKeyboardRows);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId);
        sendMessage.setText("Выберите фильтр: ");
        return sendMessage;
    }

    private Object runPhotoFilter(Message newMessage) {
        final String text = newMessage.getText();
        ImageOperation operation = ImageUtils.getOperation(text);
        if (operation == null) return null;
        String chatId = newMessage.getChatId().toString();
        Message photoMessage = messages.get(chatId);
        if (photoMessage != null) {
            List<File> files = getFilesByMessage(photoMessage);
            try {
                List<String> paths = PhotoMessageUtils.savePhotos(files, getBotToken());
                return preparePhotoMessage(paths, operation, chatId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Отправьте фото, чтобы воспользоваться фильтром");
            return sendMessage;
        }
        return null;
    }

    private List<File> getFilesByMessage(Message message) {
        List<PhotoSize> photoSizes = message.getPhoto();
        if (photoSizes == null) return new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();
        for (PhotoSize photoSize: photoSizes) {
            final String fileId = photoSize.getFileId();
            try {
                files.add(sendApiMethod(new GetFile(fileId)));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
        return files;
    }

    private SendMediaGroup preparePhotoMessage(List<String> localPaths, ImageOperation operation, String chatId) throws Exception {
        SendMediaGroup mediaGroup = new SendMediaGroup();
        ArrayList<InputMedia> medias = new ArrayList<>();
        for (String path : localPaths) {
            InputMedia inputMedia = new InputMediaPhoto();
                PhotoMessageUtils.processingImage(path, operation);
                inputMedia.setMedia(new java.io.File(path), "path");
                medias.add(inputMedia);
        }
        mediaGroup.setMedias(medias);
        mediaGroup.setChatId(chatId);
        return mediaGroup;
    }

    private ReplyKeyboardMarkup getKeyboard() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        ArrayList<KeyboardRow> allKeyboardRows = new ArrayList<>();
        allKeyboardRows.addAll(getKeyboardsRows(BotCommonCommands.class));
        allKeyboardRows.addAll(getKeyboardsRows(FilterOperation.class));

        replyKeyboardMarkup.setKeyboard(allKeyboardRows);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        return replyKeyboardMarkup;
    }
    private ArrayList<KeyboardRow> getKeyboardsRows(Class someClass) {
        Method[] methods = someClass.getDeclaredMethods();
        ArrayList<AppBotCommand> commands = new ArrayList<>();
        for (Method method : methods) {
            if (method.isAnnotationPresent(AppBotCommand.class)) {
                commands.add(method.getAnnotation(AppBotCommand.class));
            }
        }
        ArrayList<KeyboardRow> keyboardRows = new ArrayList<>();
        int columnCount = 3;
        int rowsCount = commands.size() / columnCount + ((commands.size() % columnCount == 0) ? 0 : 1);
        for (int rowIndex = 0; rowIndex < rowsCount; rowIndex++) {
            KeyboardRow row = new KeyboardRow();
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                int index = rowIndex * columnCount + columnIndex;
                if (index >= commands.size()) continue;
                AppBotCommand command = commands.get(rowIndex * columnCount + columnIndex);
                KeyboardButton keyboardButton = new KeyboardButton(command.name());
                row.add(keyboardButton);
            }
            keyboardRows.add(row);
        }
        return keyboardRows;
    }
}

