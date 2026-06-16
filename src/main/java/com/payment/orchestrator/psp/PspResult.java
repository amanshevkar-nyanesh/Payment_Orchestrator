package com.payment.orchestrator.psp;

import com.payment.orchestrator.domain.AttemptStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PspResult {

    private String pspName;
    private AttemptStatus status;
    private String pspReference;
    private String errorCode;
    private String errorMessage;
    private long latencyMs;
}
