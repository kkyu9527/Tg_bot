package com.kixyu.tgbot.service.relay;

import com.kixyu.tgbot.domain.entity.Message.ContentType;
import org.springframework.stereotype.Component;
import com.pengrad.telegrambot.model.Audio;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Video;
import com.pengrad.telegrambot.model.request.InputMedia;
import com.pengrad.telegrambot.model.request.InputMediaAudio;
import com.pengrad.telegrambot.model.request.InputMediaDocument;
import com.pengrad.telegrambot.model.request.InputMediaPhoto;
import com.pengrad.telegrambot.model.request.InputMediaVideo;

import java.util.Comparator;

@Component
public class TelegramMessageMediaMapper {

    /**
     * 根据 Telegram 消息内容推断业务侧的内容类型。
     *
     * @param message Telegram 消息
     * @return 推断得到的内容类型
     */
    public ContentType inferContentType(Message message) {
        if (message == null) {
            return ContentType.TEXT;
        }
        if (message.text() != null) {
            return ContentType.TEXT;
        }
        if (message.photo() != null && message.photo().length > 0) {
            return ContentType.PHOTO;
        }
        if (message.video() != null) {
            return ContentType.VIDEO;
        }
        if (message.document() != null) {
            return ContentType.DOCUMENT;
        }
        if (message.audio() != null) {
            return ContentType.AUDIO;
        }
        if (message.voice() != null) {
            return ContentType.VOICE;
        }
        if (message.sticker() != null) {
            return ContentType.STICKER;
        }
        if (message.location() != null) {
            return ContentType.LOCATION;
        }
        if (message.venue() != null) {
            return ContentType.VENUE;
        }
        if (message.contact() != null) {
            return ContentType.CONTACT;
        }
        if (message.animation() != null) {
            return ContentType.ANIMATION;
        }
        if (message.poll() != null) {
            return ContentType.POLL;
        }
        if (message.dice() != null) {
            return ContentType.DICE;
        }
        if (message.game() != null) {
            return ContentType.GAME;
        }
        return ContentType.TEXT;
    }

    /**
     * 将 Telegram 消息转换为媒体组发送所需的 InputMedia。
     *
     * <p>仅支持图片/视频/文件/音频等可作为媒体组的内容；不可转换则返回 null。</p>
     *
     * @param message Telegram 消息
     * @return 可发送的 InputMedia；不可转换则返回 null
     */
    public InputMedia<?> toInputMedia(Message message) {
        if (message == null) {
            return null;
        }

        if (message.photo() != null && message.photo().length > 0) {
            PhotoSize best = java.util.Arrays.stream(message.photo())
                    .max(Comparator.comparingLong(p -> p.fileSize() == null ? 0L : p.fileSize().longValue()))
                    .orElse(message.photo()[message.photo().length - 1]);
            if (best != null && best.fileId() != null) {
                InputMediaPhoto mediaPhoto = new InputMediaPhoto(best.fileId());
                if (message.caption() != null && !message.caption().isBlank()) {
                    mediaPhoto.caption(message.caption());
                }
                return mediaPhoto;
            }
        }

        Video video = message.video();
        if (video != null && video.fileId() != null) {
            InputMediaVideo mediaVideo = new InputMediaVideo(video.fileId());
            if (message.caption() != null && !message.caption().isBlank()) {
                mediaVideo.caption(message.caption());
            }
            return mediaVideo;
        }

        Document document = message.document();
        if (document != null && document.fileId() != null) {
            InputMediaDocument mediaDocument = new InputMediaDocument(document.fileId());
            if (message.caption() != null && !message.caption().isBlank()) {
                mediaDocument.caption(message.caption());
            }
            return mediaDocument;
        }

        Audio audio = message.audio();
        if (audio != null && audio.fileId() != null) {
            InputMediaAudio mediaAudio = new InputMediaAudio(audio.fileId());
            if (message.caption() != null && !message.caption().isBlank()) {
                mediaAudio.caption(message.caption());
            }
            return mediaAudio;
        }

        return null;
    }
}
