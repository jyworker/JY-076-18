package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.enums.RuleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BinRangeRule extends BaseRule {

    private static final long serialVersionUID = 1L;

    private final List<BinRange> binRanges = new CopyOnWriteArrayList<>();
    private boolean whitelistMode;

    public static class BinRange implements Serializable, Comparable<BinRange> {
        private static final long serialVersionUID = 1L;
        private final String startBin;
        private final String endBin;
        private final long startNum;
        private final long endNum;
        private String description;
        private String issuer;
        private String cardType;
        private String country;

        public BinRange(String startBin, String endBin) {
            this.startBin = normalizeBin(startBin);
            this.endBin = normalizeBin(endBin);
            this.startNum = Long.parseLong(this.startBin);
            this.endNum = Long.parseLong(this.endBin);
        }

        private static String normalizeBin(String bin) {
            if (bin == null) {
                return "000000";
            }
            bin = bin.replaceAll("\\D", "");
            if (bin.length() < 6) {
                bin = String.format("%-6s", bin).replace(' ', '0');
            } else if (bin.length() > 6) {
                bin = bin.substring(0, 6);
            }
            return bin;
        }

        public boolean matches(String bin) {
            if (bin == null) {
                return false;
            }
            long binNum = Long.parseLong(normalizeBin(bin));
            return binNum >= startNum && binNum <= endNum;
        }

        public String getStartBin() {
            return startBin;
        }

        public String getEndBin() {
            return endBin;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getCardType() {
            return cardType;
        }

        public void setCardType(String cardType) {
            this.cardType = cardType;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        @Override
        public int compareTo(BinRange other) {
            return Long.compare(this.startNum, other.startNum);
        }

        @Override
        public String toString() {
            return startBin + "-" + endBin;
        }
    }

    public BinRangeRule() {
        super();
        this.ruleType = RuleType.BIN_RANGE;
        this.whitelistMode = false;
    }

    public BinRangeRule(String ruleId, String ruleName) {
        super(ruleId, ruleName);
        this.ruleType = RuleType.BIN_RANGE;
        this.whitelistMode = false;
    }

    @Override
    public boolean isApplicable(RuleContext context) {
        return context.getCardBin() != null;
    }

    @Override
    public boolean matches(RuleContext context) {
        String cardBin = context.getCardBin();
        if (cardBin == null) {
            return false;
        }

        boolean matched = binRanges.stream().anyMatch(range -> range.matches(cardBin));

        if (whitelistMode) {
            return !matched;
        }
        return matched;
    }

    @Override
    public String getExplanation(RuleContext context) {
        String cardBin = context.getCardBin();
        BinRange matchedRange = findMatchingRange(cardBin);
        if (matchedRange != null) {
            return String.format("BIN range rule: BIN %s %s range %s (%s)",
                    cardBin,
                    whitelistMode ? "not in whitelist" : "matches blocked",
                    matchedRange,
                    matchedRange.getDescription() != null ? matchedRange.getDescription() : "");
        }
        return String.format("BIN range rule: BIN %s %s any configured range",
                cardBin,
                whitelistMode ? "not in whitelist" : "matches");
    }

    @Override
    public String getHitDetail(RuleContext context) {
        String cardBin = context.getCardBin();
        BinRange matchedRange = findMatchingRange(cardBin);
        return String.format("Rule=%s, BIN=%s, MatchedRange=%s, Mode=%s",
                ruleId, cardBin, matchedRange, whitelistMode ? "WHITELIST" : "BLACKLIST");
    }

    private BinRange findMatchingRange(String bin) {
        if (bin == null) {
            return null;
        }
        for (BinRange range : binRanges) {
            if (range.matches(bin)) {
                return range;
            }
        }
        return null;
    }

    public void addBinRange(String startBin, String endBin) {
        binRanges.add(new BinRange(startBin, endBin));
        sortRanges();
    }

    public void addBinRange(BinRange range) {
        if (range != null) {
            binRanges.add(range);
            sortRanges();
        }
    }

    public void removeBinRange(String startBin, String endBin) {
        String start = BinRange.normalizeBin(startBin);
        String end = BinRange.normalizeBin(endBin);
        binRanges.removeIf(r -> r.getStartBin().equals(start) && r.getEndBin().equals(end));
    }

    private void sortRanges() {
        List<BinRange> sorted = new ArrayList<>(binRanges);
        Collections.sort(sorted);
        binRanges.clear();
        binRanges.addAll(sorted);
    }

    public List<BinRange> getBinRanges() {
        return new ArrayList<>(binRanges);
    }

    public int getRangeCount() {
        return binRanges.size();
    }

    public boolean isWhitelistMode() {
        return whitelistMode;
    }

    public void setWhitelistMode(boolean whitelistMode) {
        this.whitelistMode = whitelistMode;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .appendSuper(super.toString())
                .append("rangeCount", binRanges.size())
                .append("whitelistMode", whitelistMode)
                .toString();
    }
}
