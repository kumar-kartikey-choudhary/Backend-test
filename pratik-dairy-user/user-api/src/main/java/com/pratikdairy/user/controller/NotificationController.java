package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.CreateNotificationRequest;
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

    // Service-to-service only (order-service, payment-service, etc.) - creates a notification
    // on behalf of the named user. Not exposed to the browser; the gateway/each service's own
    // SecurityConfig should require an authenticated caller here, same as any other internal
    // endpoint in this codebase (see e.g. ProductController's internal stock endpoints).
    @PostMapping(path = "internal", name = "createNotificationInternal")
    ResponseEntity<NotificationDto> create(@RequestBody CreateNotificationRequest request);
}