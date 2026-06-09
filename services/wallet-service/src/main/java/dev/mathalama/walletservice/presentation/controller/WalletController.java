package dev.mathalama.walletservice.presentation.controller;

import dev.mathalama.walletservice.domain.model.Wallet;
import dev.mathalama.walletservice.domain.port.in.WalletUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletUseCase walletUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<Wallet> getWallet(@PathVariable UUID userId) {
        Wallet wallet = walletUseCase.getWalletByUserId(userId);
        return ResponseEntity.ok(wallet);
    }
    
    @PostMapping("/{userId}/deposit")
    public ResponseEntity<Wallet> deposit(
            @PathVariable UUID userId, 
            @RequestParam BigDecimal amount, 
            @RequestParam String referenceId) {
        Wallet wallet = walletUseCase.deposit(userId, amount, referenceId);
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/{userId}/withdraw")
    public ResponseEntity<Wallet> withdraw(
            @PathVariable UUID userId, 
            @RequestParam BigDecimal amount, 
            @RequestParam String referenceId) {
        Wallet wallet = walletUseCase.withdraw(userId, amount, referenceId);
        return ResponseEntity.ok(wallet);
    }
}
