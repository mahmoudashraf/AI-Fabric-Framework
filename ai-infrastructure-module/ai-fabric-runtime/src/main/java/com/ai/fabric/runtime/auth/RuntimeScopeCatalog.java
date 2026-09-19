package com.ai.fabric.runtime.auth;

public final class RuntimeScopeCatalog {

    public static final String DEPLOYMENT_KNOWLEDGE_SPECIALIST =
        "specialist:deployment-knowledge-specialist@1";
    public static final String DOCUMENT_VECTOR = "vector:document";
    public static final String AGENTIC_TEAM_EXECUTE =
        "agentic:deployment-intelligence:execute";
    public static final String AGENTIC_TEAM_READ =
        "agentic:deployment-intelligence:read";
    public static final String AGENTIC_TEAM_CANCEL =
        "agentic:deployment-intelligence:cancel";
    public static final String SMART_BRAIN_TRIGGER = "smart-brain:trigger";
    public static final String SMART_BRAIN_READ = "smart-brain:read";
    public static final String SMART_BRAIN_CANCEL = "smart-brain:cancel";
    public static final String SMART_BRAIN_REPLAY = "smart-brain:replay";
    public static final String REVIEW_ACTION_PROPOSAL_CREATE =
        "review:action-proposals:create";
    public static final String REVIEW_TASK_VIEW = "review:tasks:view";
    public static final String REVIEW_TASK_DECIDE = "review:tasks:decide";

    private RuntimeScopeCatalog() {
    }
}
