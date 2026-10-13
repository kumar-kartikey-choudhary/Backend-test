package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.AdminNotificationRequest;
import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.NotificationDto;

import java.util.List;

public interface NotificationService {

    List<NotificationDto> findAll();

    NotificationDto markRead(String id);

    void markAllRead();

    void delete(String id);

    NotificationDto create(CreateNotificationRequest request);

    /** Sends an admin's notification to one customer or to every customer; returns how many were created. */
    int sendAsAdmin(AdminNotificationRequest request);
}
