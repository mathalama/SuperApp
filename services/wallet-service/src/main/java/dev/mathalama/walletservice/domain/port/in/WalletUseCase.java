package dev.mathalama.walletservice.domain.port.in;

import dev.mathalama.walletservice.domain.model.Wallet;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletUseCase {
    
    // Создание кошелька при регистрации
    Wallet createWalletForUser(UUID userId);

    // Получение баланса
    Wallet getWalletByUserId(UUID userId);

    // Пополнение кошелька
    Wallet deposit(UUID userId, BigDecimal amount, String referenceId);

    // Списание с кошелька
    Wallet withdraw(UUID userId, BigDecimal amount, String referenceId);
}
