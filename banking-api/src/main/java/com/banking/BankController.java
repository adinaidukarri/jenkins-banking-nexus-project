package com.banking;

/**
 * BankController — The Bank Teller at the counter.
 *
 * Analogy:
 *   The teller (BankController) doesn't know HOW interest is calculated.
 *   They simply pick up the rule book (AccountService from Nexus JAR)
 *   and apply it when serving customers.
 *
 *   The rule book was shipped from the warehouse (Nexus) —
 *   the teller didn't write it, they just USE it.
 *
 * This demonstrates:
 *   banking-api depends on banking-core JAR from Nexus (not local source).
 */
public class BankController {

    public static void main(String[] args) {

        // The teller picks up the "rule book" (AccountService) from Nexus
        AccountService service = new AccountService();

        System.out.println("=== Banking Application Started ===\n");

        // --- Scenario 1: Calculate Interest ---
        double principal = 10000;
        double rate = 5.0;
        int years = 3;
        double interest = service.calculateInterest(principal, rate, years);
        System.out.printf("Interest on ₹%.0f at %.1f%% for %d years = ₹%.2f%n",
                principal, rate, years, interest);

        // --- Scenario 2: Check Loan Eligibility ---
        double customerBalance = 6000;
        boolean eligible = service.isEligibleForLoan(customerBalance);
        System.out.printf("Customer with balance ₹%.0f is loan eligible: %s%n",
                customerBalance, eligible ? "✅ YES" : "❌ NO");

        // --- Scenario 3: Get Account Tier ---
        double[] balances = {3000, 15000, 75000};
        System.out.println("\nAccount Tier Classification:");
        for (double balance : balances) {
            String tier = service.getAccountTier(balance);
            System.out.printf("  Balance ₹%.0f → %s tier%n", balance, tier);
        }

        System.out.println("\n=== All operations complete ===");
    }
}
