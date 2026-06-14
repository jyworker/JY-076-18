package com.iyzipay.model.interception;

import com.iyzipay.model.Buyer;
import com.iyzipay.model.InterceptionResponse;
import com.iyzipay.model.PaymentCard;
import com.iyzipay.model.interception.enums.FrequencyUnit;
import com.iyzipay.model.interception.rules.BinRangeRule;
import com.iyzipay.model.interception.rules.CountryRule;
import com.iyzipay.model.interception.rules.DeviceAssociationRule;
import com.iyzipay.model.interception.rules.FrequencyRule;
import com.iyzipay.request.CreatePaymentRequest;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class CardInterceptionCenterTest {

    private CardInterceptionCenter center;

    @Before
    public void setUp() {
        center = CardInterceptionCenter.getInstance();
        center.setEnabled(true);
        center.resetStats();
        center.clearCache();
        center.clearAllRules();
        center.getRuleEvaluator().setCacheEnabled(false);
    }

    @Test
    public void testCardNumberBlacklist() {
        String testCard = "4111111111111111";
        String testToken = "test_token_123";

        center.addToBlacklist(testCard, testToken, null);

        assertTrue("Card should be blacklisted",
                center.isBlacklisted(testCard, null, null));
        assertTrue("Token should be blacklisted",
                center.isBlacklisted(null, testToken, null));
        assertFalse("Unknown card should not be blacklisted",
                center.isBlacklisted("5500000000000000", null, null));

        center.removeFromBlacklist(testCard, testToken, null);

        assertFalse("Card should be removed from blacklist",
                center.isBlacklisted(testCard, null, null));
    }

    @Test
    public void testBinRangeRule() {
        BinRangeRule binRule = center.createBinRangeRule("TEST_BIN_RULE", "Test BIN Rule");
        binRule.addBinRange("411111", "411111");
        binRule.setDescription("Block Visa test cards");
        binRule.setPriority(50);
        binRule.activate();
        center.addRule(binRule);

        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber("4111111111111111");
        context.setPaymentCard(card);
        context.setRequestId("test-bin-001");

        RuleHitResult result = center.getRuleEvaluator().evaluate(context);

        assertTrue("Should match BIN rule", result.getMatchedRulesCount() > 0);
        assertTrue("Hit rules should contain BIN rule",
                result.getHitRuleIds().contains("TEST_BIN_RULE"));
        assertNotNull("Should have explanation", result.getExplanation());
        assertTrue("Explanation should contain BIN", result.getExplanation().contains("BIN"));

        center.deleteRule("TEST_BIN_RULE");
    }

    @Test
    public void testCountryRule() {
        CountryRule countryRule = center.createCountryRule("TEST_COUNTRY_RULE", "Test Country Rule");
        countryRule.addBlockedCountry("US");
        countryRule.addBlockedCountry("GB");
        countryRule.setPriority(30);
        countryRule.activate();
        center.addRule(countryRule);

        RuleContext context = new RuleContext();
        Buyer buyer = new Buyer();
        buyer.setCountry("US");
        context.setBuyer(buyer);
        context.setRequestId("test-country-001");

        RuleHitResult result = center.getRuleEvaluator().evaluate(context);

        assertTrue("Should match country rule", result.getMatchedRulesCount() > 0);
        assertTrue("Should be blocked", result.isBlocked());

        RuleContext context2 = new RuleContext();
        Buyer buyer2 = new Buyer();
        buyer2.setCountry("TR");
        context2.setBuyer(buyer2);
        context2.setRequestId("test-country-002");

        RuleHitResult result2 = center.getRuleEvaluator().evaluate(context2);
        assertFalse("Should not be blocked for TR", result2.isBlocked());

        center.deleteRule("TEST_COUNTRY_RULE");
    }

    @Test
    public void testFrequencyRule() {
        FrequencyRule freqRule = center.createFrequencyRule("TEST_FREQ_RULE", "Test Frequency Rule");
        freqRule.setThreshold(3);
        freqRule.setTimeUnit(FrequencyUnit.PER_MINUTE);
        freqRule.setDimension("CARD_NUMBER");
        freqRule.setPriority(40);
        freqRule.activate();
        center.addRule(freqRule);

        String testCard = "5500000000000000";

        RuleHitResult lastResult = null;
        for (int i = 0; i < 5; i++) {
            RuleContext context = new RuleContext();
            PaymentCard card = new PaymentCard();
            card.setCardNumber(testCard);
            context.setPaymentCard(card);
            context.setRequestId("test-freq-" + i);
            lastResult = center.getRuleEvaluator().evaluate(context);
            if (i < 3) {
                assertFalse("Should not block before threshold at i=" + i, lastResult.isBlocked());
            }
        }

        assertTrue("Should block after exceeding threshold", lastResult.isBlocked());
        assertTrue("Explanation should mention frequency",
                lastResult.getExplanation() != null && lastResult.getExplanation().contains("Frequency"));

        center.deleteRule("TEST_FREQ_RULE");
    }

    @Test
    public void testDeviceAssociationRule() {
        DeviceAssociationRule deviceRule = center.createDeviceAssociationRule("TEST_DEVICE_RULE", "Test Device Rule");
        deviceRule.setMaxCardsPerDevice(2);
        deviceRule.setMaxDevicesPerCard(2);
        deviceRule.setPriority(35);
        deviceRule.activate();
        center.addRule(deviceRule);

        String deviceId = "device_001";

        evaluateWithCardAndDevice("4111111111111111", deviceId, "req-1");
        evaluateWithCardAndDevice("5500000000000000", deviceId, "req-2");

        RuleHitResult result = evaluateWithCardAndDevice("340000000000009", deviceId, "req-3");

        assertTrue("Should block device with too many cards", result.isBlocked());
        assertTrue("Explanation should mention device or cards",
                result.getExplanation() != null &&
                        (result.getExplanation().contains("Device") || result.getExplanation().contains("cards")));

        center.deleteRule("TEST_DEVICE_RULE");
    }

    private RuleHitResult evaluateWithCardAndDevice(String cardNumber, String deviceId, String requestId) {
        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber(cardNumber);
        context.setPaymentCard(card);
        context.setDeviceId(deviceId);
        context.setRequestId(requestId);
        return center.getRuleEvaluator().evaluate(context);
    }

    @Test
    public void testGrayReleaseBasic() {
        BinRangeRule rule = center.createBinRangeRule("GRAY_TEST_RULE", "Gray Test Rule");
        rule.addBinRange("411111", "411111");
        rule.setPriority(20);
        center.addRule(rule);

        center.startGrayRelease("GRAY_TEST_RULE", 100, "test_operator");

        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber("4111111111111111");
        context.setPaymentCard(card);
        context.setRequestId("gray-test-100pct");

        RuleHitResult result = center.getRuleEvaluator().evaluate(context);
        assertTrue("Should be blocked with 100% gray", result.isBlocked());
        assertTrue("Should have gray applied", result.isGrayApplied());

        center.promoteFromGray("GRAY_TEST_RULE", "test_operator");

        RuleContext context2 = new RuleContext();
        PaymentCard card2 = new PaymentCard();
        card2.setCardNumber("4111111111111111");
        context2.setPaymentCard(card2);
        context2.setRequestId("post-gray-test");
        RuleHitResult result2 = center.getRuleEvaluator().evaluate(context2);

        assertTrue("Should be blocked after promotion", result2.isBlocked());
        assertFalse("Should not be in gray anymore", result2.isGrayApplied());

        center.deleteRule("GRAY_TEST_RULE");
    }

    @Test
    public void testVersionManagement() {
        BinRangeRule rule = center.createBinRangeRule("VERSION_TEST_RULE", "Version Test Rule v1");
        rule.addBinRange("411111", "411111");
        rule.activate();
        center.addRule(rule);

        int v1 = rule.getCurrentVersion() != null ? rule.getCurrentVersion().getVersion() : 1;
        assertTrue("Should have version >= 1", v1 >= 1);

        rule.addBinRange("550000", "550000");
        rule.setRuleName("Version Test Rule v2");
        int v2 = rule.incrementVersion("test_operator", "Added 550000 BIN range");
        center.updateRule(rule);

        assertTrue("Version should increment", v2 > v1);
        assertEquals("Current version should be v2", v2, rule.getCurrentVersion().getVersion());

        assertTrue("Version history should have at least 2 entries",
                rule.getVersionHistory().size() >= 2);

        center.deleteRule("VERSION_TEST_RULE");
    }

    @Test
    public void testRuleActivation() {
        BinRangeRule rule = center.createBinRangeRule("ACTIVATION_TEST_RULE", "Activation Test Rule");
        rule.addBinRange("411111", "411111");
        rule.setPriority(20);
        center.addRule(rule);

        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber("4111111111111111");
        context.setPaymentCard(card);
        context.setRequestId("activation-test-inactive");

        RuleHitResult resultInactive = center.getRuleEvaluator().evaluate(context);
        assertFalse("Should not be blocked when rule is inactive", resultInactive.isBlocked());

        center.activateRule("ACTIVATION_TEST_RULE", "test_operator");

        RuleHitResult resultActive = center.getRuleEvaluator().evaluate(context);
        assertTrue("Should be blocked when rule is active", resultActive.isBlocked());

        center.deactivateRule("ACTIVATION_TEST_RULE", "test_operator");

        RuleHitResult resultDeactivated = center.getRuleEvaluator().evaluate(context);
        assertFalse("Should not be blocked after deactivation", resultDeactivated.isBlocked());

        center.deleteRule("ACTIVATION_TEST_RULE");
    }

    @Test
    public void testFastIntercept() {
        String testCard = "4111111111111111";
        center.addToBlacklist(testCard, null, null);

        CreatePaymentRequest request = new CreatePaymentRequest();
        PaymentCard card = new PaymentCard();
        card.setCardNumber(testCard);
        request.setPaymentCard(card);
        request.setPrice(new BigDecimal("100.00"));
        request.setCurrency("TRY");

        long startTime = System.nanoTime();
        InterceptionResponse response = center.fastIntercept(request, null);
        long fastTime = System.nanoTime() - startTime;

        assertTrue("Fast intercept should block", response.isBlocked());
        assertNotNull("Should have hit rule IDs", response.getHitRuleIds());
        assertTrue("Hit rule IDs should not be empty", !response.getHitRuleIds().isEmpty());
        assertNotNull("Should have explanation", response.getExplanation());

        startTime = System.nanoTime();
        InterceptionResponse fullResponse = center.intercept(request, null);
        long fullTime = System.nanoTime() - startTime;

        assertNotNull("Full intercept should return response", fullResponse);

        center.removeFromBlacklist(testCard, null, null);
    }

    @Test
    public void testExplanationOutput() {
        BinRangeRule binRule = center.createBinRangeRule("EXPLAIN_TEST_RULE", "Explanation Test Rule");
        binRule.addBinRange("411111", "411111");
        binRule.activate();
        center.addRule(binRule);

        CountryRule countryRule = center.createCountryRule("EXPLAIN_COUNTRY_RULE", "Country Explanation Rule");
        countryRule.addBlockedCountry("US");
        countryRule.activate();
        center.addRule(countryRule);

        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber("4111111111111111");
        context.setPaymentCard(card);
        Buyer buyer = new Buyer();
        buyer.setCountry("US");
        context.setBuyer(buyer);
        context.setRequestId("explain-test-001");

        RuleHitResult result = center.getRuleEvaluator().evaluate(context);

        assertTrue("Should have explanation", result.getExplanation() != null && !result.getExplanation().isEmpty());
        assertTrue("Should mention BIN rule", result.getExplanation().contains("BIN"));
        assertEquals("Should have 2 hit rules", 2, result.getMatchedRulesCount());
        assertEquals("Should have 2 hit details", 2, result.getHitDetails().size());
        assertNotNull("Hit details should not be null", result.getHitDetails());
        assertTrue("Hit details should have entries", result.getHitDetails().size() > 0);

        center.deleteRule("EXPLAIN_TEST_RULE");
        center.deleteRule("EXPLAIN_COUNTRY_RULE");
    }

    @Test
    public void testPerformance() {
        int iterations = 1000;
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            RuleContext context = new RuleContext();
            PaymentCard card = new PaymentCard();
            card.setCardNumber("4111111111111111");
            context.setPaymentCard(card);
            context.setRequestId("perf-test-" + i);

            long startTime = System.nanoTime();
            RuleHitResult result = center.getRuleEvaluator().evaluate(context);
            totalTime += System.nanoTime() - startTime;
        }

        double avgTimeMs = (totalTime / 1_000_000.0) / iterations;
        assertTrue("Average evaluation time should be < 10ms", avgTimeMs < 10.0);

        System.out.printf("Performance test: avg=%.3fms over %d iterations%n", avgTimeMs, iterations);
    }

    @Test
    public void testRuleSetVersion() {
        String initialVersion = center.getRuleSetVersion();
        assertNotNull("Initial version should not be null", initialVersion);

        BinRangeRule rule = center.createBinRangeRule("VERSION_CHECK_RULE", "Version Check Rule");
        rule.addBinRange("411111", "411111");
        center.addRule(rule);

        String versionAfterAdd = center.getRuleSetVersion();
        assertNotEquals("Version should change after adding rule", initialVersion, versionAfterAdd);

        rule.activate();
        center.updateRule(rule);

        String versionAfterUpdate = center.getRuleSetVersion();
        assertNotEquals("Version should change after updating rule", versionAfterAdd, versionAfterUpdate);

        center.deleteRule("VERSION_CHECK_RULE");

        String versionAfterDelete = center.getRuleSetVersion();
        assertNotEquals("Version should change after deleting rule", versionAfterUpdate, versionAfterDelete);
    }

    @Test
    public void testStatsSummary() {
        String stats = center.getStatsSummary();
        assertNotNull("Stats summary should not be null", stats);
        assertTrue("Stats should contain activeRules", stats.contains("activeRules"));
        assertTrue("Stats should contain totalRules", stats.contains("totalRules"));

        for (int i = 0; i < 10; i++) {
            RuleContext context = new RuleContext();
            PaymentCard card = new PaymentCard();
            card.setCardNumber("4111111111111111");
            context.setPaymentCard(card);
            context.setRequestId("stats-test-" + i);
            center.getRuleEvaluator().evaluate(context);
        }

        String statsAfter = center.getStatsSummary();
        assertTrue("Stats should contain evaluation info",
                statsAfter.contains("totalEvaluations") || statsAfter.contains("avgTime"));
    }
}
