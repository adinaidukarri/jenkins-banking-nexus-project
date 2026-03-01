package com.banking;

/**
 * AccountService — The bank's internal rule book.
 *
 * Analogy:
 *   This is like the Reserve Bank's guidelines document.
 *   Every branch (banking-api) follows these rules — they don't
 *   rewrite the rules themselves. They pick up this packaged JAR
 *   from Nexus (the warehouse) and use it directly.
 *
 * This class will be compiled into banking-core-1.0-SNAPSHOT.jar
 * and deployed to Nexus for other modules to consume.
 */
public class AccountService {

    /**
     * Calculate simple interest.
     *
     * Example: principal=10000, rate=5%, years=3 → Interest = 1500.0
     *
     * @param principal  The deposit/loan amount
     * @param rate       Annual interest rate (percentage)
     * @param years      Duration in years
     * @return           Interest amount
     */
    public double calculateInterest(double principal, double rate, int years) {
        return principal * rate * years / 100;
    }

    /**
     * Check if a customer is eligible for a loan.
     *
     * Business Rule: Minimum balance of ₹5000 required.
     * (Like a bank's internal policy — defined once, reused everywhere)
     *
     * @param balance  Customer's current account balance
     * @return         true if eligible, false otherwise
     */
    public boolean isEligibleForLoan(double balance) {
        return balance >= 5000;
    }

    /**
     * Get account tier based on balance.
     *
     * Analogy: Like a bank's loyalty program —
     *   Silver (< 10K), Gold (10K-50K), Platinum (> 50K)
     *
     * @param balance  Customer's balance
     * @return         Account tier as String
     */
    public String getAccountTier(double balance) {
        if (balance >= 50000) {
            return "Platinum";
        } else if (balance >= 10000) {
            return "Gold";
        } else {
            return "Silver";
        }
    }
}
