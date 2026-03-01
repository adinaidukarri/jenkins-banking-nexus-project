package com.banking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BankControllerTest — Quality check at the bank counter.
 *
 * Analogy:
 *   Before a teller starts their shift, a supervisor runs through
 *   test scenarios to make sure they're applying the rules correctly.
 *   These are automated — Jenkins runs them on every build.
 */
class BankControllerTest {

    private AccountService service;

    @BeforeEach
    void setUp() {
        // Fresh rule book for each test — no shared state
        service = new AccountService();
    }

    @Test
    void testCalculateInterest() {
        // 10,000 at 5% for 3 years = 1500
        double result = service.calculateInterest(10000, 5, 3);
        assertEquals(1500.0, result, "Interest calculation failed");
    }

    @Test
    void testCalculateInterestZeroRate() {
        // 0% interest = no interest charged
        double result = service.calculateInterest(10000, 0, 5);
        assertEquals(0.0, result, "Zero rate should return 0 interest");
    }

    @Test
    void testLoanEligibilityApproved() {
        // Balance >= 5000 → eligible
        assertTrue(service.isEligibleForLoan(6000), "Customer with ₹6000 should be eligible");
    }

    @Test
    void testLoanEligibilityRejected() {
        // Balance < 5000 → not eligible
        assertFalse(service.isEligibleForLoan(3000), "Customer with ₹3000 should NOT be eligible");
    }

    @Test
    void testLoanEligibilityBoundary() {
        // Exactly 5000 → eligible (boundary condition)
        assertTrue(service.isEligibleForLoan(5000), "Customer with exactly ₹5000 should be eligible");
    }

    @Test
    void testAccountTierPlatinum() {
        assertEquals("Platinum", service.getAccountTier(75000));
    }

    @Test
    void testAccountTierGold() {
        assertEquals("Gold", service.getAccountTier(20000));
    }

    @Test
    void testAccountTierSilver() {
        assertEquals("Silver", service.getAccountTier(2000));
    }
}
