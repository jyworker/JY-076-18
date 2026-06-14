package com.iyzipay.model.interception;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.enums.RuleAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RuleHitResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean blocked;
    private boolean reviewRequired;
    private boolean challengeRequired;
    private RuleAction finalAction;
    private String explanation;
    private final List<String> hitRuleIds = new ArrayList<>();
    private final List<String> hitDetails = new ArrayList<>();
    private String ruleSetVersion;
    private long evaluationTimeMs;
    private long evaluationStartTime;
    private int totalRulesEvaluated;
    private int matchedRulesCount;
    private boolean grayApplied;
    private String grayRuleId;

    public RuleHitResult() {
        this.blocked = false;
        this.reviewRequired = false;
        this.challengeRequired = false;
        this.finalAction = RuleAction.ALLOW;
        this.evaluationStartTime = System.currentTimeMillis();
    }

    public void markEvaluationComplete() {
        this.evaluationTimeMs = System.currentTimeMillis() - evaluationStartTime;
    }

    public void addHitRule(String ruleId, String detail, String explanation) {
        if (!hitRuleIds.contains(ruleId)) {
            hitRuleIds.add(ruleId);
            hitDetails.add(detail);
            matchedRulesCount++;
            if (this.explanation == null || this.explanation.isEmpty()) {
                this.explanation = explanation;
            } else {
                this.explanation += "; " + explanation;
            }
        }
    }

    public void merge(RuleHitResult other) {
        if (other == null) {
            return;
        }
        for (int i = 0; i < other.hitRuleIds.size(); i++) {
            if (!this.hitRuleIds.contains(other.hitRuleIds.get(i))) {
                this.hitRuleIds.add(other.hitRuleIds.get(i));
                this.hitDetails.add(other.hitDetails.get(i));
                this.matchedRulesCount++;
            }
        }
        if (other.blocked) {
            this.blocked = true;
        }
        if (other.reviewRequired) {
            this.reviewRequired = true;
        }
        if (other.challengeRequired) {
            this.challengeRequired = true;
        }
        if (other.finalAction != null && other.finalAction.ordinal() > this.finalAction.ordinal()) {
            this.finalAction = other.finalAction;
        }
        if (other.explanation != null && !other.explanation.isEmpty()) {
            if (this.explanation == null || this.explanation.isEmpty()) {
                this.explanation = other.explanation;
            } else {
                this.explanation += "; " + other.explanation;
            }
        }
        if (other.grayApplied) {
            this.grayApplied = true;
        }
        if (other.grayRuleId != null) {
            this.grayRuleId = other.grayRuleId;
        }
        this.totalRulesEvaluated += other.totalRulesEvaluated;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
        if (blocked) {
            this.finalAction = RuleAction.BLOCK;
        }
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
        if (reviewRequired && !blocked) {
            this.finalAction = RuleAction.REVIEW;
        }
    }

    public boolean isChallengeRequired() {
        return challengeRequired;
    }

    public void setChallengeRequired(boolean challengeRequired) {
        this.challengeRequired = challengeRequired;
        if (challengeRequired && !blocked && !reviewRequired) {
            this.finalAction = RuleAction.CHALLENGE;
        }
    }

    public RuleAction getFinalAction() {
        return finalAction;
    }

    public void setFinalAction(RuleAction finalAction) {
        this.finalAction = finalAction;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getHitRuleIds() {
        return new ArrayList<>(hitRuleIds);
    }

    public List<String> getHitDetails() {
        return new ArrayList<>(hitDetails);
    }

    public String getRuleSetVersion() {
        return ruleSetVersion;
    }

    public void setRuleSetVersion(String ruleSetVersion) {
        this.ruleSetVersion = ruleSetVersion;
    }

    public long getEvaluationTimeMs() {
        return evaluationTimeMs;
    }

    public void setEvaluationTimeMs(long evaluationTimeMs) {
        this.evaluationTimeMs = evaluationTimeMs;
    }

    public int getTotalRulesEvaluated() {
        return totalRulesEvaluated;
    }

    public void setTotalRulesEvaluated(int totalRulesEvaluated) {
        this.totalRulesEvaluated = totalRulesEvaluated;
    }

    public int getMatchedRulesCount() {
        return matchedRulesCount;
    }

    public boolean isGrayApplied() {
        return grayApplied;
    }

    public void setGrayApplied(boolean grayApplied) {
        this.grayApplied = grayApplied;
    }

    public String getGrayRuleId() {
        return grayRuleId;
    }

    public void setGrayRuleId(String grayRuleId) {
        this.grayRuleId = grayRuleId;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("blocked", blocked)
                .append("finalAction", finalAction)
                .append("explanation", explanation)
                .append("hitRuleIds", hitRuleIds)
                .append("evaluationTimeMs", evaluationTimeMs)
                .append("matchedRulesCount", matchedRulesCount)
                .append("grayApplied", grayApplied)
                .toString();
    }
}
