package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.TooManyRequestsException;
import com.pratikdairy.user.dto.ContactMessageDto;
import com.pratikdairy.user.dto.ContactMessageRequest;
import com.pratikdairy.user.mail.EmailSender;
import com.pratikdairy.user.model.ContactMessage;
import com.pratikdairy.user.repository.ContactMessageRepository;
import com.pratikdairy.user.service.ContactService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@Slf4j
public class ContactServiceImpl implements ContactService {

    // The form is public, so it is limited per sender: a handful of messages an hour is plenty for a customer.
    static final int MAX_MESSAGES_PER_HOUR = 3;

    private final ContactMessageRepository repository;
    private final EmailSender emailSender;

    // Where messages are emailed. Blank = they are only stored and shown on the admin Messages page.
    @Value("${app.contact.recipient:}")
    private String recipient = "";

    @Autowired
    public ContactServiceImpl(ContactMessageRepository repository, EmailSender emailSender) {
        this.repository = repository;
        this.emailSender = emailSender;
    }

    @Override
    @Transactional
    public void submit(ContactMessageRequest request) {
        String email = request.getEmail().trim();
        if (repository.countByEmailIgnoreCaseAndCreatedAtAfter(email, ZonedDateTime.now().minusHours(1)) >= MAX_MESSAGES_PER_HOUR) {
            throw new TooManyRequestsException("You've sent several messages recently. Please wait a while before sending another, or call us.");
        }
        ContactMessage message = new ContactMessage();
        message.setName(request.getName().trim());
        message.setEmail(email);
        message.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        message.setMessage(request.getMessage().trim());
        repository.saveAndFlush(message);
        log.info("Contact message stored from {}", email);

        if (recipient != null && !recipient.isBlank()) {
            emailSender.sendContactMessage(recipient.trim(), message.getName(), message.getEmail(), message.getPhone(), message.getMessage());
        }
    }

    @Override
    public List<ContactMessageDto> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public ContactMessageDto markHandled(String id, boolean handled) {
        ContactMessage message = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message", id));
        message.setHandled(handled);
        return toDto(repository.saveAndFlush(message));
    }

    private ContactMessageDto toDto(ContactMessage m) {
        ContactMessageDto dto = new ContactMessageDto();
        dto.setId(m.getId());
        dto.setName(m.getName());
        dto.setEmail(m.getEmail());
        dto.setPhone(m.getPhone());
        dto.setMessage(m.getMessage());
        dto.setHandled(m.isHandled());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
