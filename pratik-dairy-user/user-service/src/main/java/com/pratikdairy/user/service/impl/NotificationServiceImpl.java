package com.pratikdairy.user.service.impl;

import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.NotificationDto;
import com.pratikdairy.user.model.Notification;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.NotificationRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }

    @Override
    public List<NotificationDto> findAll() {
        return notificationRepository.findByUser_UsernameOrderByCreatedAtDesc(getUsername()).stream()
                .map(this::toDto).toList();
    }

    @Override
    @Transactional
    public NotificationDto markRead(String id) {
        Notification notification = getOwnedOrThrow(id);
        notification.setRead(true);
        return toDto(notificationRepository.saveAndFlush(notification));
    }

    @Override
    @Transactional
    public void markAllRead() {
        String username = getUsername();
        List<Notification> unread = notificationRepository.findByUser_UsernameOrderByCreatedAtDesc(username).stream()
                .filter(n -> !n.isRead())
                .toList();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAllAndFlush(unread);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Notification notification = getOwnedOrThrow(id);
        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public NotificationDto create(CreateNotificationRequest request) {
        log.info("Inside @class NotificationServiceImpl @method create @param request: {}", request);
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.getUsername()));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(request.getType());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setReferenceId(request.getReferenceId());

        return toDto(notificationRepository.saveAndFlush(notification));
    }

    private Notification getOwnedOrThrow(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found with id: " + id));
        if (!notification.getUser().getUsername().equals(getUsername())) {
            throw new AccessDeniedException("This notification does not belong to the current user");
        }
        return notification;
    }

    private NotificationDto toDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setRead(notification.isRead());
        dto.setReferenceId(notification.getReferenceId());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}