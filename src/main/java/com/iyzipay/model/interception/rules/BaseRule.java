package com.iyzipay.model.interception.rules;

import com.iyzipay.ToStringRequestBuilder;
import com.iyzipay.model.interception.RuleContext;
import com.iyzipay.model.interception.RuleHitResult;
import com.iyzipay.model.interception.config.GrayReleaseConfig;
import com.iyzipay.model.interception.config.RuleVersion;
import com.iyzipay.model.interception.enums.MatchStrategy;
import com.iyzipay.model.interception.enums.RuleAction;
import com.iyzipay.model.interception.enums.RuleStatus;
import com.iyzipay.model.interception.enums.RuleType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BaseRule implements Serializable {

    private static final long serialVersionUID = 1L;

    protected String ruleId;
    protected String ruleName;
    protected String description;
    protected RuleType ruleType;
    protected RuleStatus status;
    protected RuleAction action;
    protected MatchStrategy matchStrategy;
    protected int priority;
    protected long createdAt;
    protected long updatedAt;
    protected String createdBy;
    protected String updatedBy;

    protected RuleVersion currentVersion;
    protected final Map<Integer, RuleVersion> versionHistory = new ConcurrentHashMap<>();
    protected GrayReleaseConfig grayReleaseConfig;

    protected final AtomicLong hitCount = new AtomicLong(0);
    protected final AtomicLong evaluationCount = new AtomicLong(0);
    protected final AtomicLong totalEvaluationTime = new AtomicLong(0);

    protected BaseRule() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.status = RuleStatus.DRAFT;
        this.action = RuleAction.BLOCK;
        this.matchStrategy = MatchStrategy.ANY_MATCH;
        this.priority = 0;
    }

    protected BaseRule(String ruleId, String ruleName) {
        this();
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.currentVersion = RuleVersion.createInitial(ruleId, "system");
        this.versionHistory.put(1, this.currentVersion);
    }

    public abstract boolean isApplicable(RuleContext context);

    public abstract boolean matches(RuleContext context);

    public abstract String getExplanation(RuleContext context);

    public abstract String getHitDetail(RuleContext context);

    public RuleHitResult evaluate(RuleContext context) {
        long startTime = System.nanoTime();
        evaluationCount.incrementAndGet();

        RuleHitResult result = new RuleHitResult();

        if (status != RuleStatus.ACTIVE && status != RuleStatus.GRAY_RELEASE) {
            return result;
        }

        if (status == RuleStatus.GRAY_RELEASE && grayReleaseConfig != null) {
            boolean applyGray = grayReleaseConfig.shouldApplyGray(context.getRequestId());
            result.setGrayApplied(applyGray);
            result.setGrayRuleId(ruleId);
            if (!applyGray) {
                return result;
            }
        }

        if (!isApplicable(context)) {
            recordEvaluationTime(startTime);
            return result;
        }

        if (matches(context)) {
            hitCount.incrementAndGet();
            result.addHitRule(ruleId, getHitDetail(context), getExplanation(context));
            applyAction(result);
        }

        recordEvaluationTime(startTime);
        return result;
    }

    protected void applyAction(RuleHitResult result) {
        switch (action) {
            case BLOCK:
                result.setBlocked(true);
                break;
            case REVIEW:
                result.setReviewRequired(true);
                break;
            case CHALLENGE:
                result.setChallengeRequired(true);
                break;
            case ALLOW:
            case MONITOR:
            default:
                break;
        }
        result.setFinalAction(action);
    }

    private void recordEvaluationTime(long startTimeNano) {
        long elapsed = System.nanoTime() - startTimeNano;
        totalEvaluationTime.addAndGet(elapsed);
    }

    public void activate() {
        this.status = RuleStatus.ACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }

    public void deactivate() {
        this.status = RuleStatus.INACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }

    public void startGrayRelease(int trafficPercentage, String operator) {
        this.status = RuleStatus.GRAY_RELEASE;
        this.grayReleaseConfig = new GrayReleaseConfig(trafficPercentage);
        this.updatedBy = operator;
        this.updatedAt = System.currentTimeMillis();
        if (currentVersion != null) {
            currentVersion.setGrayReleaseConfig(this.grayReleaseConfig);
            currentVersion.addChangeLog("Started gray release with " + trafficPercentage + "% traffic by " + operator);
        }
    }

    public void promoteFromGray(String operator) {
        this.status = RuleStatus.ACTIVE;
        this.grayReleaseConfig = null;
        this.updatedBy = operator;
        this.updatedAt = System.currentTimeMillis();
        if (currentVersion != null) {
            currentVersion.addChangeLog("Promoted from gray release to full active by " + operator);
        }
    }

    public int incrementVersion(String operator, String changeDescription) {
        int newVersionNum = currentVersion != null ? currentVersion.getVersion() + 1 : 1;
        RuleVersion newVersion = currentVersion != null
                ? currentVersion.copyWithNewVersion(newVersionNum, operator, changeDescription)
                : RuleVersion.createInitial(ruleId, operator);
        newVersion.setRuleSnapshot(this.toString());
        this.currentVersion = newVersion;
        this.versionHistory.put(newVersionNum, newVersion);
        this.updatedBy = operator;
        this.updatedAt = System.currentTimeMillis();
        return newVersionNum;
    }

    public boolean rollbackToVersion(int targetVersion, String operator) {
        RuleVersion target = versionHistory.get(targetVersion);
        if (target == null) {
            return false;
        }
        target.setActive(true);
        for (Map.Entry<Integer, RuleVersion> entry : versionHistory.entrySet()) {
            if (entry.getKey() > targetVersion) {
                entry.getValue().setActive(false);
            }
        }
        this.currentVersion = target;
        this.updatedBy = operator;
        this.updatedAt = System.currentTimeMillis();
        if (currentVersion != null) {
            currentVersion.addChangeLog("Rolled back to version " + targetVersion + " by " + operator);
        }
        return true;
    }

    public List<RuleVersion> getVersionHistory() {
        List<RuleVersion> versions = new ArrayList<>(versionHistory.values());
        versions.sort((v1, v2) -> Integer.compare(v2.getVersion(), v1.getVersion()));
        return Collections.unmodifiableList(versions);
    }

    public RuleVersion getVersion(int versionNum) {
        return versionHistory.get(versionNum);
    }

    public double getAverageEvaluationTimeMs() {
        long evalCount = evaluationCount.get();
        if (evalCount == 0) {
            return 0;
        }
        return (totalEvaluationTime.get() / 1_000_000.0) / evalCount;
    }

    public double getHitRate() {
        long evalCount = evaluationCount.get();
        if (evalCount == 0) {
            return 0;
        }
        return (hitCount.get() * 100.0) / evalCount;
    }

    public void resetStats() {
        hitCount.set(0);
        evaluationCount.set(0);
        totalEvaluationTime.set(0);
        if (grayReleaseConfig != null) {
            grayReleaseConfig.resetStats();
        }
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public RuleStatus getStatus() {
        return status;
    }

    public void setStatus(RuleStatus status) {
        this.status = status;
    }

    public RuleAction getAction() {
        return action;
    }

    public void setAction(RuleAction action) {
        this.action = action;
    }

    public MatchStrategy getMatchStrategy() {
        return matchStrategy;
    }

    public void setMatchStrategy(MatchStrategy matchStrategy) {
        this.matchStrategy = matchStrategy;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = System.currentTimeMillis();
    }

    public RuleVersion getCurrentVersion() {
        return currentVersion;
    }

    public GrayReleaseConfig getGrayReleaseConfig() {
        return grayReleaseConfig;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getEvaluationCount() {
        return evaluationCount.get();
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("ruleId", ruleId)
                .append("ruleName", ruleName)
                .append("ruleType", ruleType)
                .append("status", status)
                .append("action", action)
                .append("priority", priority)
                .append("version", currentVersion != null ? currentVersion.getVersion() : 0)
                .append("hitCount", hitCount.get())
                .toString();
    }
}
