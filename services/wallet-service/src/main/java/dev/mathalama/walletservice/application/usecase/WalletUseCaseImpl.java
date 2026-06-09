package dev.mathalama.walletservice.application.usecase;

import dev.mathalama.walletservice.domain.model.TransactionType;
import dev.mathalama.walletservice.domain.model.Wallet;
import dev.mathalama.walletservice.domain.model.WalletStatus;
import dev.mathalama.walletservice.domain.model.WalletTransaction;
import dev.mathalama.walletservice.domain.port.in.WalletUseCase;
import dev.mathalama.walletservice.domain.port.out.WalletRepository;
import dev.mathalama.walletservice.domain.port.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WalletUseCaseImpl implements WalletUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Override
    public Wallet createWalletForUser(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.warn("Wallet already exists for user: {}", userId);
            return walletRepository.findByUserId(userId).get();
        }
        
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("KZT")
                .status(WalletStatus.ACTIVE)
                .build();
                
        return walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + userId));
    }

    @Override
    public Wallet deposit(UUID userId, BigDecimal amount, String referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        Wallet wallet = getWalletByUserId(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet savedWallet = walletRepository.save(wallet);
        
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(savedWallet)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .referenceId(referenceId)
                .build();
        transactionRepository.save(transaction);
        
        return savedWallet;
    }

    @Override
    public Wallet withdraw(UUID userId, BigDecimal amount, String referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        
        Wallet wallet = getWalletByUserId(userId);
        
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet is not active");
        }
        
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        
        wallet.setBalance(wallet.getBalance().subtract(amount));
        Wallet savedWallet = walletRepository.save(wallet);
        
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(savedWallet)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .referenceId(referenceId)
                .build();
        transactionRepository.save(transaction);
        
        return savedWallet;
    }
}
