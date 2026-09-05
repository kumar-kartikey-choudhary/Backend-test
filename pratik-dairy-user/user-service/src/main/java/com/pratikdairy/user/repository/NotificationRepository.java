package com.pratikdairy.user.repository;

import com.pratikdairy.user.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUser_UsernameOrderByCreatedAtDesc(String username);
}