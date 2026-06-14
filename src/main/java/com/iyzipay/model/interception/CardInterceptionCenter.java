package com.iyzipay.model.interception;

import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.InterceptionResponse;
import com.iyzipay.model.PaymentCard;
import com.iyzipay.model.interception.engine.RuleEvaluator;
import com.iyzipay.model.interception.engine.RuleManager;
import com.iyzipay.model.interception.enums.RuleAction;
import com.iyzipay.model.interception.enums.RuleType;
import com.iyzipay.model.interception.rules.BaseRule;
import com.iyzipay.model.interception.rules.BinRangeRule;
import com.iyzipay.model.interception.rules.CardNumberRule;
import com.iyzipay.model.interception.rules.CountryRule;
import com.iyzipay.model.interception.rules.DeviceAssociationRule;
import com.iyzipay.model.interception.rules.FrequencyRule;
import com.iyzipay.request.CreatePaymentRequest;
import com.iyzipay.request.InterceptRequest;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public class CardInterceptionCenter implements Serializable {

    private static final long serialVersionUID = 1L;

    private static volatile CardInterceptionCenter instance;

    private final RuleManager ruleManager;
    private final RuleEvaluator ruleEvaluator;
    private boolean enabled;

    private CardInterceptionCenter() {
        this.ruleManager = new RuleManager();
        this.ruleEvaluator = new RuleEvaluator(ruleManager);
        this.enabled = true;
        initializeDefaultRules();
    }

    public static CardInterceptionCenter getInstance() {
        if (instance == null) {
            synchronized (CardInterceptionCenter.class) {
                if (instance == null) {
                    instance = new CardInterceptionCenter();
                }
            }
        }
        return instance;
    }

    private void initializeDefaultRules() {
        CardNumberRule defaultBlacklist = new CardNumberRule("DEFAULT_CARD_BLACKLIST", "Default Card Blacklist");
        defaultBlacklist.setDescription("System default card number blacklist rule");
        defaultBlacklist.setPriority(10);
        defaultBlacklist.activate();
        ruleManager.addRule(defaultBlacklist);
    }

    public InterceptionResponse intercept(CreatePaymentRequest paymentRequest, Options options) {
        return intercept(buildContextFromPaymentRequest(paymentRequest), options);
    }

    public InterceptionResponse intercept(RuleContext context, Options options) {
        if (!enabled) {
            return InterceptionResponse.allow(context.getRequestId(), "Interception disabled");
        }

        if (context.getRequestId() == null) {
            context.setRequestId(UUID.randomUUID().toString());
        }

        RuleHitResult hitResult = ruleEvaluator.evaluate(context);
        return buildResponse(context, hitResult);
    }

    public InterceptionResponse fastIntercept(CreatePaymentRequest paymentRequest, Options options) {
        return fastIntercept(buildContextFromPaymentRequest(paymentRequest), options);
    }

    public InterceptionResponse fastIntercept(RuleContext context, Options options) {
        if (!enabled) {
            return InterceptionResponse.allow(context.getRequestId(), "Interception disabled");
        }

        if (context.getRequestId() == null) {
            context.setRequestId(UUID.randomUUID().toString());
        }

        RuleHitResult hitResult = ruleEvaluator.fastEvaluate(context);
        return buildResponse(context, hitResult);
    }

    public RuleContext buildContextFromPaymentRequest(CreatePaymentRequest request) {
        RuleContext context = new RuleContext();
        context.setRequestId(request.getConversationId());
        context.setPaymentCard(request.getPaymentCard());
        context.setBuyer(request.getBuyer());
        context.setBillingAddress(request.getBillingAddress());
        context.setShippingAddress(request.getShippingAddress());
        context.setAmount(request.getPrice());
        context.setCurrency(request.getCurrency());
        if (request.getPaymentCard() != null && request.getPaymentCard().getMetadata() != null) {
            context.setDeviceId(request.getPaymentCard().getMetadata().get("deviceId"));
            context.setDeviceFingerprint(request.getPaymentCard().getMetadata().get("deviceFingerprint"));
            context.setUserAgent(request.getPaymentCard().getMetadata().get("userAgent"));
        }
        if (request.getBuyer() != null) {
            context.setIpAddress(request.getBuyer().getIp());
        }
        return context;
    }

    public RuleContext buildContextFromInterceptRequest(InterceptRequest request) {
        RuleContext context = new RuleContext();
        context.setRequestId(request.getConversationId());
        context.setCardBin(request.getCardBin());
        context.setAmount(request.getAmount());
        context.setCurrency(request.getCurrency());
        context.setMerchantId(request.getMerchantId());
        context.setDeviceId(request.getDeviceId());
        context.setDeviceFingerprint(request.getDeviceFingerprint());
        context.setIpAddress(request.getIpAddress());

        PaymentCard card = new PaymentCard();
        card.setCardNumber(request.getCardNumber());
        card.setCardToken(request.getCardToken());
        card.setCardUserKey(request.getCardUserKey());
        context.setPaymentCard(card);

        Buyer buyer = new Buyer();
        buyer.setId(request.getBuyerId());
        buyer.setCountry(request.getBuyerCountry());
        buyer.setIp(request.getIpAddress());
        context.setBuyer(buyer);

        if (request.getCardNumber() != null && request.getCardNumber().length() >= 6 && request.getCardBin() == null) {
            context.setCardBin(request.getCardNumber().substring(0, 6));
        }

        return context;
    }

    private InterceptionResponse buildResponse(RuleContext context, RuleHitResult hitResult) {
        InterceptionResponse response = new InterceptionResponse();
        response.setRequestId(context.getRequestId());
        response.setRuleSetVersion(hitResult.getRuleSetVersion());
        response.setEvaluationTimeMs(hitResult.getEvaluationTimeMs());
        response.setBlocked(hitResult.isBlocked());
        response.setReviewRequired(hitResult.isReviewRequired());
        response.setChallengeRequired(hitResult.isChallengeRequired());
        response.setAction(hitResult.getFinalAction() != null ? hitResult.getFinalAction().name() : "ALLOW");
        response.setHitDetails(hitResult.getHitDetails());
        response.setExplanation(hitResult.getExplanation());
        response.setHitRuleIds(hitResult.getHitRuleIds());

        if (hitResult.isBlocked()) {
            response.setErrorCode("INTERCEPT_BLOCKED");
            response.setErrorMessage("Transaction blocked by interception rules");
            response.setErrorGroup("INTERCEPTION");
            response.setStatus("failure");
        } else {
            response.setStatus("success");
        }

        return response;
    }

    public void addToBlacklist(String cardNumber, String cardToken, String cardUserKey) {
        CardNumberRule blacklistRule = getOrCreateBlacklistRule();
        if (cardNumber != null) {
            blacklistRule.addBlockedCardNumber(cardNumber);
        }
        if (cardToken != null) {
            blacklistRule.addBlockedCardToken(cardToken);
        }
        if (cardUserKey != null) {
            blacklistRule.addBlockedCardUserKey(cardUserKey);
        }
        ruleManager.updateRule(blacklistRule);
    }

    public void removeFromBlacklist(String cardNumber, String cardToken, String cardUserKey) {
        CardNumberRule blacklistRule = getOrCreateBlacklistRule();
        if (cardNumber != null) {
            blacklistRule.removeBlockedCardNumber(cardNumber);
        }
        if (cardToken != null) {
            blacklistRule.removeBlockedCardToken(cardToken);
        }
        if (cardUserKey != null) {
            blacklistRule.removeBlockedCardUserKey(cardUserKey);
        }
        ruleManager.updateRule(blacklistRule);
    }

    public boolean isBlacklisted(String cardNumber, String cardToken, String cardUserKey) {
        RuleContext context = new RuleContext();
        PaymentCard card = new PaymentCard();
        card.setCardNumber(cardNumber);
        card.setCardToken(cardToken);
        card.setCardUserKey(cardUserKey);
        context.setPaymentCard(card);

        List<BaseRule> rules = ruleManager.getRulesByType(RuleType.CARD_NUMBER);
        for (BaseRule rule : rules) {
            if (rule.isApplicable(context) && rule.matches(context)) {
                return true;
            }
        }
        return false;
    }

    private CardNumberRule getOrCreateBlacklistRule() {
        BaseRule rule = ruleManager.getRule("DEFAULT_CARD_BLACKLIST");
        if (rule instanceof CardNumberRule) {
            return (CardNumberRule) rule;
        }
        CardNumberRule blacklistRule = new CardNumberRule("DEFAULT_CARD_BLACKLIST", "Default Card Blacklist");
        blacklistRule.activate();
        ruleManager.addRule(blacklistRule);
        return blacklistRule;
    }

    public void addRule(BaseRule rule) {
        ruleManager.addRule(rule);
    }

    public void updateRule(BaseRule rule) {
        ruleManager.updateRule(rule);
    }

    public void deleteRule(String ruleId) {
        ruleManager.deleteRule(ruleId);
    }

    public BaseRule getRule(String ruleId) {
        return ruleManager.getRule(ruleId);
    }

    public BaseRule rollbackRule(String ruleId, int toVersion, String operator) {
        return ruleManager.rollbackRule(ruleId, toVersion, operator);
    }

    public void activateRule(String ruleId, String operator) {
        ruleManager.activateRule(ruleId, operator);
    }

    public void deactivateRule(String ruleId, String operator) {
        ruleManager.deactivateRule(ruleId, operator);
    }

    public void startGrayRelease(String ruleId, int trafficPercentage, String operator) {
        ruleManager.startGrayRelease(ruleId, trafficPercentage, operator);
    }

    public void promoteFromGray(String ruleId, String operator) {
        ruleManager.promoteFromGray(ruleId, operator);
    }

    public List<BaseRule> getAllActiveRules() {
        return ruleManager.getAllActiveRules();
    }

    public List<BaseRule> getAllInactiveRules() {
        return ruleManager.getAllInactiveRules();
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public RuleEvaluator getRuleEvaluator() {
        return ruleEvaluator;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRuleSetVersion() {
        return ruleManager.getRuleSetVersion();
    }

    public String getStatsSummary() {
        return ruleEvaluator.getStatsSummary() +
                ", activeRules=" + ruleManager.getActiveRuleCount() +
                ", totalRules=" + ruleManager.getTotalRuleCount();
    }

    public void resetStats() {
        ruleEvaluator.resetStats();
    }

    public void clearCache() {
        ruleEvaluator.clearCache();
    }

    public void clearAllRules() {
        ruleManager.clearAllRules();
        initializeDefaultRules();
    }

    public BinRangeRule createBinRangeRule(String ruleId, String ruleName) {
        return new BinRangeRule(ruleId, ruleName);
    }

    public CountryRule createCountryRule(String ruleId, String ruleName) {
        return new CountryRule(ruleId, ruleName);
    }

    public FrequencyRule createFrequencyRule(String ruleId, String ruleName) {
        return new FrequencyRule(ruleId, ruleName);
    }

    public DeviceAssociationRule createDeviceAssociationRule(String ruleId, String ruleName) {
        return new DeviceAssociationRule(ruleId, ruleName);
    }

    public CardNumberRule createCardNumberRule(String ruleId, String ruleName) {
        return new CardNumberRule(ruleId, ruleName);
    }
}
