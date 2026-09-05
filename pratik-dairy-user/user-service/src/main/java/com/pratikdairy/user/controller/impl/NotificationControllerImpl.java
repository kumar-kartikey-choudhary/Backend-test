package com.pratikdairy.user.controller.impl;

import com.pratikdairy.user.controller.NotificationController;
import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.NotificationDto;
import com.pratikdairy.user.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Primary
@RequestMapping("users/notifications")
public class NotificationControllerImpl implements NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationControllerImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResponseEntity<List<NotificationDto>> findAll() {
        return ResponseEntity.ok(notificationService.findAll());
    }

    @Override
    public ResponseEntity<NotificationDto> markRead(String id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @Override
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok().build();
    }

    @Override
    public void delete(String id) {
        notificationService.delete(id);
    }

    @Override
    public ResponseEntity<NotificationDto> create(CreateNotificationRequest request) {
        return new ResponseEntity<>(notificationService.create(request), HttpStatus.CREATED);
    }
}