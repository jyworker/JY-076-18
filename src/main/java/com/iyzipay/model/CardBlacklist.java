package com.iyzipay.model;

import com.iyzipay.HttpClient;
import com.iyzipay.IyzipayResource;
import com.iyzipay.Options;
import com.iyzipay.model.interception.CardInterceptionCenter;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.RuleHitResult;
import com.iyzipay.model.interception.rules.CardNumberRule;
import com.iyzipay.request.CardBlacklistRequest;
import com.iyzipay.request.CreatePaymentRequest;
import com.iyzipay.request.InterceptRequest;
import com.iyzipay.request.RetrieveCardBlacklistRequest;

public class CardBlacklist extends IyzipayResource {
    private String cardNumber;
    private boolean blacklisted;

    private static final CardInterceptionCenter interceptionCenter = CardInterceptionCenter.getInstance();

    public static IyzipayResource create(CardBlacklistRequest request, Options options) {
        interceptionCenter.addToBlacklist(null, request.getCardToken(), request.getCardUserKey());

        String path = "/cardstorage/blacklist/card";
        return HttpClient.create().post(options.getBaseUrl() + path,
                getHttpProxy(options),
                getHttpHeadersV2(path, request, options),
                request,
                IyzipayResource.class);
    }

    public static IyzipayResource update(CardBlacklistRequest request, Options options) {
        interceptionCenter.removeFromBlacklist(null, request.getCardToken(), request.getCardUserKey());

        String path = "/cardstorage/blacklist/card/inactive";
        return HttpClient.create().post(options.getBaseUrl() + path,
                getHttpProxy(options),
                getHttpHeadersV2(path, request, options),
                request,
                IyzipayResource.class);
    }

    public static CardBlacklist retrieve(RetrieveCardBlacklistRequest request, Options options) {
        CardBlacklist result = new CardBlacklist();
        String cardNumber = request.getCardNumber();
        String cardToken = request.getCardToken();
        String cardUserKey = request.getCardUserKey();

        boolean isBlacklisted = interceptionCenter.isBlacklisted(cardNumber, cardToken, cardUserKey);
        result.setBlacklisted(isBlacklisted);
        result.setCardNumber(cardNumber);
        result.setStatus("success");

        String path = "/cardstorage/blacklist/card/retrieve";
        return HttpClient.create().post(options.getBaseUrl() + path,
                getHttpProxy(options),
                getHttpHeadersV2(path, request, options),
                request,
                CardBlacklist.class);
    }

    public static InterceptionResponse interceptPayment(CreatePaymentRequest paymentRequest, Options options) {
        return interceptionCenter.intercept(paymentRequest, options);
    }

    public static InterceptionResponse interceptPayment(InterceptRequest interceptRequest, Options options) {
        RuleContext context = interceptionCenter.buildContextFromInterceptRequest(interceptRequest);
        return interceptionCenter.intercept(context, options);
    }

    public static InterceptionResponse fastIntercept(CreatePaymentRequest paymentRequest, Options options) {
        return interceptionCenter.fastIntercept(paymentRequest, options);
    }

    public static RuleHitResult evaluateRules(InterceptRequest interceptRequest) {
        RuleContext context = interceptionCenter.buildContextFromInterceptRequest(interceptRequest);
        return interceptionCenter.getRuleEvaluator().evaluate(context);
    }

    public static void addCardToBlacklist(String cardNumber, String cardToken, String cardUserKey) {
        interceptionCenter.addToBlacklist(cardNumber, cardToken, cardUserKey);
    }

    public static void removeCardFromBlacklist(String cardNumber, String cardToken, String cardUserKey) {
        interceptionCenter.removeFromBlacklist(cardNumber, cardToken, cardUserKey);
    }

    public static boolean isCardBlacklisted(String cardNumber, String cardToken, String cardUserKey) {
        return interceptionCenter.isBlacklisted(cardNumber, cardToken, cardUserKey);
    }

    public static CardInterceptionCenter getInterceptionCenter() {
        return interceptionCenter;
    }

    public static CardNumberRule getDefaultBlacklistRule() {
        return (CardNumberRule) interceptionCenter.getRule("DEFAULT_CARD_BLACKLIST");
    }

    public static String getRuleSetVersion() {
        return interceptionCenter.getRuleSetVersion();
    }

    public static String getStatsSummary() {
        return interceptionCenter.getStatsSummary();
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(boolean blacklisted) {
        this.blacklisted = blacklisted;
    }
}
