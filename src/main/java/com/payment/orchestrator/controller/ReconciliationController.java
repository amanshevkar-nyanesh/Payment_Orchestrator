package com.payment.orchestrator.controller;
/*
* This conrolller is used to trigger the reconciliation job manually.
* It is not intended for production use and should be secured appropriately.
*
* */
import com.payment.orchestrator.job.ReconciliationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reconciliation")
public class ReconciliationController {

    private final ReconciliationJob reconciliationJob;

    @PostMapping("/run")
    public String run() {
        reconciliationJob.reconcileStuckPayments();
        return "Triggered";
    }
}