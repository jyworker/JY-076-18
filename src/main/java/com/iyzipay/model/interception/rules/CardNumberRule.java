package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.enums.RuleType;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CardNumberRule extends BaseRule {

    private static final long serialVersionUID = 1L;

    private final Set<String> blockedCardNumbers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> blockedCardTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> blockedCardUserKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public CardNumberRule() {
        super();
        this.ruleType = RuleType.CARD_NUMBER;
    }

    public CardNumberRule(String ruleId, String ruleName) {
        super(ruleId, ruleName);
        this.ruleType = RuleType.CARD_NUMBER;
    }

    @Override
    public boolean isApplicable(RuleContext context) {
        return context.getPaymentCard() != null;
    }

    @Override
    public boolean matches(RuleContext context) {
        String cardNumber = context.getCardNumber();
        String cardToken = context.getCardToken();
        String cardUserKey = context.getCardUserKey();

        if (cardNumber != null && blockedCardNumbers.contains(maskCardNumber(cardNumber))) {
            return true;
        }
        if (cardToken != null && blockedCardTokens.contains(cardToken)) {
            return true;
        }
        if (cardUserKey != null && blockedCardUserKeys.contains(cardUserKey)) {
            return true;
        }
        return false;
    }

    @Override
    public String getExplanation(RuleContext context) {
        StringBuilder sb = new StringBuilder("Card blacklisted: ");
        String cardNumber = context.getCardNumber();
        String cardToken = context.getCardToken();
        String cardUserKey = context.getCardUserKey();

        if (cardNumber != null && blockedCardNumbers.contains(maskCardNumber(cardNumber))) {
            sb.append("card number ").append(maskCardNumber(cardNumber));
        } else if (cardToken != null && blockedCardTokens.contains(cardToken)) {
            sb.append("card token ").append(cardToken);
        } else if (cardUserKey != null && blockedCardUserKeys.contains(cardUserKey)) {
            sb.append("card user key ").append(cardUserKey);
        }
        return sb.toString();
    }

    @Override
    public String getHitDetail(RuleContext context) {
        return String.format("Rule=%s, CardNumber=%s, CardToken=%s, CardUserKey=%s",
                ruleId,
                maskCardNumber(context.getCardNumber()),
                context.getCardToken(),
                context.getCardUserKey());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return cardNumber;
        }
        return cardNumber.substring(0, 6) + "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    public void addBlockedCardNumber(String cardNumber) {
        if (cardNumber != null) {
            blockedCardNumbers.add(maskCardNumber(cardNumber));
            blockedCardNumbers.add(cardNumber);
        }
    }

    public void removeBlockedCardNumber(String cardNumber) {
        if (cardNumber != null) {
            blockedCardNumbers.remove(maskCardNumber(cardNumber));
            blockedCardNumbers.remove(cardNumber);
        }
    }

    public void addBlockedCardToken(String cardToken) {
        if (cardToken != null) {
            blockedCardTokens.add(cardToken);
        }
    }

    public void removeBlockedCardToken(String cardToken) {
        if (cardToken != null) {
            blockedCardTokens.remove(cardToken);
        }
    }

    public void addBlockedCardUserKey(String cardUserKey) {
        if (cardUserKey != null) {
            blockedCardUserKeys.add(cardUserKey);
        }
    }

    public void removeBlockedCardUserKey(String cardUserKey) {
        if (cardUserKey != null) {
            blockedCardUserKeys.remove(cardUserKey);
        }
    }

    public int getBlockedCardCount() {
        return blockedCardNumbers.size();
    }

    public int getBlockedTokenCount() {
        return blockedCardTokens.size();
    }

    public int getBlockedUserKeyCount() {
        return blockedCardUserKeys.size();
    }

    public boolean isCardNumberBlocked(String cardNumber) {
        return cardNumber != null && (blockedCardNumbers.contains(cardNumber)
                || blockedCardNumbers.contains(maskCardNumber(cardNumber)));
    }

    public boolean isCardTokenBlocked(String cardToken) {
        return cardToken != null && blockedCardTokens.contains(cardToken);
    }

    public boolean isCardUserKeyBlocked(String cardUserKey) {
        return cardUserKey != null && blockedCardUserKeys.contains(cardUserKey);
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .appendSuper(super.toString())
                .append("blockedCardCount", blockedCardNumbers.size())
                .append("blockedTokenCount", blockedCardTokens.size())
                .append("blockedUserKeyCount", blockedCardUserKeys.size())
                .toString();
    }
}
