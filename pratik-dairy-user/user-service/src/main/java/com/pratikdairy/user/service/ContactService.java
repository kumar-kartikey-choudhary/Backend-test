package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.ContactMessageDto;
import com.pratikdairy.user.dto.ContactMessageRequest;

import java.util.List;

public interface ContactService {

    void submit(ContactMessageRequest request);

    List<ContactMessageDto> findAll();

    ContactMessageDto markHandled(String id, boolean handled);
}
