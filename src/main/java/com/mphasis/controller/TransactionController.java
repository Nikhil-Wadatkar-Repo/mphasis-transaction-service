package com.mphasis.controller;

import com.mphasis.dto.ProcessingSummary;
import com.mphasis.dto.TransactionRequest;
import com.mphasis.dto.TransactionResult;
import com.mphasis.service.TransactionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResult> submit(@RequestBody(required = false) TransactionRequest request) {
        TransactionResult result = transactionService.submit(request);
        HttpStatus status = result.status() == com.mphasis.model.TransactionStatus.FAILED
                ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/transactions/batch")
    public List<TransactionResult> submitBatch(@RequestBody(required = false) List<TransactionRequest> requests) {
        return transactionService.submitBatch(requests);
    }

    @GetMapping("/transactions")
    public List<TransactionResult> getTransactions() {
        return transactionService.getTransactions();
    }

    @GetMapping("/transactions/failed")
    public List<TransactionResult> getFailedTransactions() {
        return transactionService.getFailedTransactions();
    }

    @GetMapping("/summary")
    public ProcessingSummary getSummary() {
        return transactionService.getSummary();
    }

    @PostMapping("/transactions/{transactionId}/retry")
    public TransactionResult retry(@PathVariable String transactionId) {
        return transactionService.retry(transactionId);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
