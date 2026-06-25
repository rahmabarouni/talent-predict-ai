package com.talentpredict.modules.notification.services;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.talentpredict.modules.notification.dto.NotificationDto;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationSseService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 hour timeout
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(userId);
        });
        emitter.onError((e) -> {
            emitter.completeWithError(e);
            emitters.remove(userId);
        });

        // Send initial heartbeat to prevent proxy timeout
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    public void sendNotification(UUID userId, NotificationDto.Response notificationResponse) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .data(notificationResponse));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }
}
