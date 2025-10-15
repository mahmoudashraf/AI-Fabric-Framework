package com.easyluxury.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public void sendAgencyApprovedNotification(String email, String agencyName) {
        // TODO: Implement email notification
        log.info("Sending agency approved notification to {} for agency {}", email, agencyName);
    }

    public void sendAgencyRejectedNotification(String email, String agencyName, String reason) {
        // TODO: Implement email notification
        log.info("Sending agency rejected notification to {} for agency {} - Reason: {}", 
                email, agencyName, reason);
    }

    public void sendAgencySubmittedNotification(String email, String agencyName) {
        // TODO: Implement email notification
        log.info("Sending agency submitted notification to {} for agency {}", email, agencyName);
    }
}
