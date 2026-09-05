package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.NotificationDto;

import java.util.List;

public interface NotificationService {

    List<NotificationDto> findAll();

    NotificationDto markRead(String id);

    void markAllRead();

    void delete(String id);

    NotificationDto create(CreateNotificationRequest request);
}