package com.pratikdairy.user.controller.impl;

import com.pratikdairy.user.controller.ContactController;
import com.pratikdairy.user.dto.ContactMessageDto;
import com.pratikdairy.user.dto.ContactMessageRequest;
import com.pratikdairy.user.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("users")
public class ContactControllerImpl implements ContactController {

    private final ContactService contactService;

    @Autowired
    public ContactControllerImpl(ContactService contactService) {
        this.contactService = contactService;
    }

    @Override
    public ResponseEntity<Void> submit(ContactMessageRequest request) {
        contactService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<List<ContactMessageDto>> findAll() {
        return ResponseEntity.ok(contactService.findAll());
    }

    @Override
    public ResponseEntity<ContactMessageDto> markHandled(String id, boolean handled) {
        return ResponseEntity.ok(contactService.markHandled(id, handled));
    }
}
