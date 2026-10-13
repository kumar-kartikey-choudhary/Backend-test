package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.AdminNotificationRequest;
import com.pratikdairy.user.dto.CreateNotificationRequest;
import com.pratikdairy.user.dto.SentNotificationsDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import com.pratikdairy.user.dto.NotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-USER", contextId = "notificationClient", path = "users/notifications", primary = false, url = "${user.url}")
public interface NotificationController {

    @GetMapping
    ResponseEntity<List<NotificationDto>> findAll();

    @PatchMapping(path = "{id}/read")
    ResponseEntity<NotificationDto> markRead(@PathVariable(name = "id") String id);

    @PatchMapping(path = "read-all")
    ResponseEntity<Void> markAllRead();

    @DeleteMapping(path = "{id}")
    void delete(@PathVariable(name = "id") String id);

    // Admin: send a notification to one customer or to every customer. Shows up in their bell and account page.
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/send")
    ResponseEntity<SentNotificationsDto> sendAsAdmin(@Valid @RequestBody AdminNotificationRequest request);

    // Service-to-service only: creates a notification on behalf of the named user. Not for the
    // browser - the user SecurityConfig requires ROLE_SERVICE for /users/notifications/internal, which
    // only a caller presenting the shared internal key (see InternalApiKeyFilter) is given, and the
    // gateway doesn't route internal paths at all.
    @PostMapping(path = "internal", name = "createNotificationInternal")
    ResponseEntity<NotificationDto> create(@RequestBody CreateNotificationRequest request);
}