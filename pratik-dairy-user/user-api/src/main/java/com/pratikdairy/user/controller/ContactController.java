package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.ContactMessageDto;
import com.pratikdairy.user.dto.ContactMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface ContactController {

    // Public - the website's contact form. Stored for the shop (and emailed to it when a mail server is set up).
    @PostMapping(path = "contact")
    ResponseEntity<Void> submit(@Valid @RequestBody ContactMessageRequest request);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/contact")
    ResponseEntity<List<ContactMessageDto>> findAll();

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PatchMapping(path = "admin/contact/{id}/handled")
    ResponseEntity<ContactMessageDto> markHandled(@PathVariable("id") String id,
                                                  @RequestParam(name = "handled", defaultValue = "true") boolean handled);
}
