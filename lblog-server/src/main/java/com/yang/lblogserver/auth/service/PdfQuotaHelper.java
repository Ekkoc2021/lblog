package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.mapper.PdfUserQuotaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 独立 bean，确保 insertDefault 不受外层事务回滚影响 */
@Service
public class PdfQuotaHelper {

    private final PdfUserQuotaMapper quotaMapper;

    public PdfQuotaHelper(PdfUserQuotaMapper quotaMapper) {
        this.quotaMapper = quotaMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaultQuota(Long userId) {
        quotaMapper.insertDefault(userId);
    }
}
