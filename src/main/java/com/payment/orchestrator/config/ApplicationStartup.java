package com.payment.orchestrator.config;

import com.payment.orchestrator.service.PspRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationStartup {

    private final PspRoutingService pspRoutingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        pspRoutingService.initializeCircuitBreakers();
    }
}
