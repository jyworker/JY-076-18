package com.iyzipay.model;

import com.iyzipay.ToStringRequestBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class InterceptionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;
    private String requestId;
    private String ruleSetVersion;
    private long evaluationTimeMs;
    private boolean blocked;
    private boolean reviewRequired;
    private boolean challengeRequired;
    private String action;
    private List<String> hitRuleIds = new ArrayList<>();
    private List<String> hitDetails = new ArrayList<>();
    private String explanation;
    private String errorCode;
    private String errorMessage;
    private String errorGroup;

    public static InterceptionResponse allow(String requestId, String reason) {
        InterceptionResponse response = new InterceptionResponse();
        response.setStatus("success");
        response.setRequestId(requestId);
        response.setBlocked(false);
        response.setAction("ALLOW");
        response.setExplanation(reason);
        return response;
    }

    public static InterceptionResponse block(String requestId, String reason, String ruleId) {
        InterceptionResponse response = new InterceptionResponse();
        response.setStatus("failure");
        response.setRequestId(requestId);
        response.setBlocked(true);
        response.setAction("BLOCK");
        response.setExplanation(reason);
        response.setErrorCode("INTERCEPT_BLOCKED");
        response.setErrorMessage("Transaction blocked: " + reason);
        response.setErrorGroup("INTERCEPTION");
        if (ruleId != null) {
            response.getHitRuleIds().add(ruleId);
        }
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public boolean isChallengeRequired() {
        return challengeRequired;
    }

    public void setChallengeRequired(boolean challengeRequired) {
        this.challengeRequired = challengeRequired;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getHitRuleIds() {
        return hitRuleIds;
    }

    public void setHitRuleIds(List<String> hitRuleIds) {
        this.hitRuleIds = hitRuleIds;
    }

    public List<String> getHitDetails() {
        return hitDetails;
    }

    public void setHitDetails(List<String> hitDetails) {
        this.hitDetails = hitDetails;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorGroup() {
        return errorGroup;
    }

    public void setErrorGroup(String errorGroup) {
        this.errorGroup = errorGroup;
    }

    @Override
    public String toString() {
        return new ToStringRequestBuilder(this)
                .append("status", status)
                .append("requestId", requestId)
                .append("blocked", blocked)
                .append("action", action)
                .append("explanation", explanation)
                .append("evaluationTimeMs", evaluationTimeMs)
                .toString();
    }
}
