package com.pratikdairy.user.repository;

import com.pratikdairy.user.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, String> {

    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    /** How many messages this address sent since the given moment - used to throttle the public form. */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, ZonedDateTime since);
}
