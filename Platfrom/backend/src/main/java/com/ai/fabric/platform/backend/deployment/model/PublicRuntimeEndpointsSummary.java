package com.ai.fabric.platform.backend.deployment.model;

public record PublicRuntimeEndpointsSummary(
    String chatBaseUrl,
    String crudBaseUrl,
    String chatQueryUrl,
    String queryOnceUrl,
    String suggestionsUrl,
    String conversationsUrl,
    String conversationItemUrlTemplate,
    String operationalBaseUrl,
    String healthUrl,
    String authContextUrl,
    String authOverviewUrl,
    String agenticExecuteUrl,
    String agenticExecutionsUrl,
    String agenticExecutionItemUrlTemplate,
    String smartBrainTriggerUrlTemplate,
    String smartBrainOperationUrlTemplate,
    String reviewActionProposalUrlTemplate,
    String reviewInboxUrl,
    String reviewTaskUrlTemplate,
    String reviewDecisionUrlTemplate,
    String canonicalConversationMessageUrlTemplate,
    String handoverRequestUrlTemplate,
    String handoverCancelUrlTemplate,
    String handoverRetryUrlTemplate,
    String conversationControlUrlTemplate,
    String conversationEventsUrlTemplate,
    String aiResumeRequestUrlTemplate
) {
    public PublicRuntimeEndpointsSummary(
        String chatBaseUrl,
        String crudBaseUrl,
        String chatQueryUrl,
        String queryOnceUrl,
        String suggestionsUrl,
        String conversationsUrl,
        String conversationItemUrlTemplate,
        String operationalBaseUrl,
        String healthUrl,
        String authContextUrl,
        String authOverviewUrl,
        String agenticExecuteUrl,
        String agenticExecutionsUrl,
        String agenticExecutionItemUrlTemplate,
        String smartBrainTriggerUrlTemplate,
        String smartBrainOperationUrlTemplate,
        String reviewActionProposalUrlTemplate,
        String reviewInboxUrl,
        String reviewTaskUrlTemplate,
        String reviewDecisionUrlTemplate
    ) {
        this(
            chatBaseUrl, crudBaseUrl, chatQueryUrl, queryOnceUrl, suggestionsUrl, conversationsUrl,
            conversationItemUrlTemplate, operationalBaseUrl, healthUrl, authContextUrl, authOverviewUrl,
            agenticExecuteUrl, agenticExecutionsUrl, agenticExecutionItemUrlTemplate,
            smartBrainTriggerUrlTemplate, smartBrainOperationUrlTemplate, reviewActionProposalUrlTemplate,
            reviewInboxUrl, reviewTaskUrlTemplate, reviewDecisionUrlTemplate,
            null, null, null, null, null, null, null
        );
    }
}
