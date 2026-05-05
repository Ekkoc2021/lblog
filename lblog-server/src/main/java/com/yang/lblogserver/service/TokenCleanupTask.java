package com.yang.lblogserver.service;

import com.yang.lblogserver.security.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupTask.class);

    private final TokenRepository tokenRepository;

    public TokenCleanupTask(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpired();
        if (deleted > 0) {
            log.info("Cleaned up {} expired tokens", deleted);
        }
    }
}
