package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.user.dto.AdminNotificationRequest;
import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.NotificationDto;
import com.pratikdairy.user.model.Notification;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.NotificationRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.NotificationService;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
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
            throw new UnauthorizedException("User not authenticated");
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUsername()));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(request.getType());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setReferenceId(request.getReferenceId());

        return toDto(notificationRepository.saveAndFlush(notification));
    }

    @Override
    @Transactional
    public int sendAsAdmin(AdminNotificationRequest request) {
        List<User> recipients;
        if (request.getAudience() == AdminNotificationRequest.Audience.USER) {
            if (request.getUsername() == null || request.getUsername().isBlank()) {
                throw new BadRequestException("Enter the username to send this to.");
            }
            User user = userRepository.findByUsername(request.getUsername().trim())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUsername().trim()));
            recipients = List.of(user);
        } else {
            recipients = userRepository.findByRoleIgnoreCaseAndDeletedFalse("CUSTOMER");
        }
        List<Notification> created = recipients.stream().map(user -> {
            Notification n = new Notification();
            n.setUser(user);
            n.setType(request.getType() == null ? com.pratikdairy.user.enums.NotificationType.SYSTEM : request.getType());
            n.setTitle(request.getTitle().trim());
            n.setMessage(request.getMessage() == null || request.getMessage().isBlank() ? null : request.getMessage().trim());
            return n;
        }).toList();
        notificationRepository.saveAllAndFlush(created);
        log.info("Admin notification '{}' sent to {} customer(s)", request.getTitle(), created.size());
        return created.size();
    }

    private Notification getOwnedOrThrow(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
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