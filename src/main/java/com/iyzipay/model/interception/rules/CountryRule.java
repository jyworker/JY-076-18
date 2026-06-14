package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.enums.RuleType;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CountryRule extends BaseRule {

    private static final long serialVersionUID = 1L;

    private final Set<String> blockedCountries = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> allowedCountries = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean whitelistMode;
    private boolean checkBillingCountry;
    private boolean checkShippingCountry;
    private boolean checkIpCountry;
    private boolean checkBuyerCountry;

    public CountryRule() {
        super();
        this.ruleType = RuleType.COUNTRY;
        this.whitelistMode = false;
        this.checkBillingCountry = true;
        this.checkShippingCountry = true;
        this.checkIpCountry = false;
        this.checkBuyerCountry = true;
    }

    public CountryRule(String ruleId, String ruleName) {
        super(ruleId, ruleName);
        this.ruleType = RuleType.COUNTRY;
        this.whitelistMode = false;
        this.checkBillingCountry = true;
        this.checkShippingCountry = true;
        this.checkIpCountry = false;
        this.checkBuyerCountry = true;
    }

    @Override
    public boolean isApplicable(RuleContext context) {
        return true;
    }

    @Override
    public boolean matches(RuleContext context) {
        Set<String> countries = collectCountries(context);
        if (countries.isEmpty()) {
            return false;
        }

        if (whitelistMode) {
            for (String country : countries) {
                if (!allowedCountries.contains(normalizeCountry(country))) {
                    return true;
                }
            }
            return false;
        } else {
            for (String country : countries) {
                if (blockedCountries.contains(normalizeCountry(country))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String getExplanation(RuleContext context) {
        Set<String> countries = collectCountries(context);
        StringBuilder sb = new StringBuilder();
        sb.append(whitelistMode ? "Country not in whitelist: " : "Country blocked: ");
        sb.append(countries);
        sb.append(" against ").append(whitelistMode ? "allowed" : "blocked").append(" list: ");
        sb.append(whitelistMode ? allowedCountries : blockedCountries);
        return sb.toString();
    }

    @Override
    public String getHitDetail(RuleContext context) {
        Set<String> countries = collectCountries(context);
        return String.format("Rule=%s, Countries=%s, Mode=%s, Matched=%s",
                ruleId, countries, whitelistMode ? "WHITELIST" : "BLACKLIST",
                findMatchingCountry(countries));
    }

    private String findMatchingCountry(Set<String> countries) {
        Set<String> checkSet = whitelistMode ? allowedCountries : blockedCountries;
        for (String country : countries) {
            String normalized = normalizeCountry(country);
            if (whitelistMode ? !checkSet.contains(normalized) : checkSet.contains(normalized)) {
                return country;
            }
        }
        return null;
    }

    private Set<String> collectCountries(RuleContext context) {
        Set<String> countries = Collections.newSetFromMap(new ConcurrentHashMap<>());

        if (checkBuyerCountry && context.getBuyer() != null && context.getBuyer().getCountry() != null) {
            countries.add(context.getBuyer().getCountry());
        }
        if (checkBillingCountry && context.getBillingAddress() != null
                && context.getBillingAddress().getCountry() != null) {
            countries.add(context.getBillingAddress().getCountry());
        }
        if (checkShippingCountry && context.getShippingAddress() != null
                && context.getShippingAddress().getCountry() != null) {
            countries.add(context.getShippingAddress().getCountry());
        }

        return countries;
    }

    private String normalizeCountry(String country) {
        if (country == null) {
            return "";
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 2) {
            try {
                Locale locale = new Locale("", normalized);
                if (locale.getISO3Country() != null) {
                    return normalized;
                }
            } catch (Exception e) {
            }
        }
        return normalized;
    }

    public void addBlockedCountry(String countryCode) {
        if (countryCode != null) {
            blockedCountries.add(normalizeCountry(countryCode));
        }
    }

    public void removeBlockedCountry(String countryCode) {
        if (countryCode != null) {
            blockedCountries.remove(normalizeCountry(countryCode));
        }
    }

    public void addAllowedCountry(String countryCode) {
        if (countryCode != null) {
            allowedCountries.add(normalizeCountry(countryCode));
        }
    }

    public void removeAllowedCountry(String countryCode) {
        if (countryCode != null) {
            allowedCountries.remove(normalizeCountry(countryCode));
        }
    }

    public Set<String> getBlockedCountries() {
        return Collections.unmodifiableSet(blockedCountries);
    }

    public Set<String> getAllowedCountries() {
        return Collections.unmodifiableSet(allowedCountries);
    }

    public boolean isWhitelistMode() {
        return whitelistMode;
    }

    public void setWhitelistMode(boolean whitelistMode) {
        this.whitelistMode = whitelistMode;
    }

    public boolean isCheckBillingCountry() {
        return checkBillingCountry;
    }

    public void setCheckBillingCountry(boolean checkBillingCountry) {
        this.checkBillingCountry = checkBillingCountry;
    }

    public boolean isCheckShippingCountry() {
        return checkShippingCountry;
    }

    public void setCheckShippingCountry(boolean checkShippingCountry) {
        this.checkShippingCountry = checkShippingCountry;
    }

    public boolean isCheckIpCountry() {
        return checkIpCountry;
    }

    public void setCheckIpCountry(boolean checkIpCountry) {
        this.checkIpCountry = checkIpCountry;
    }

    public boolean isCheckBuyerCountry() {
        return checkBuyerCountry;
    }

    public void setCheckBuyerCountry(boolean checkBuyerCountry) {
        this.checkBuyerCountry = checkBuyerCountry;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .appendSuper(super.toString())
                .append("blockedCountries", blockedCountries.size())
                .append("allowedCountries", allowedCountries.size())
                .append("whitelistMode", whitelistMode)
                .toString();
    }
}
