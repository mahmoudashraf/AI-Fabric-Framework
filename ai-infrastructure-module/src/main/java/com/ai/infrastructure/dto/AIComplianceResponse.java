package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Compliance Responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIComplianceResponse {
    
    /**
     * Request ID
     */
    private String requestId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Data privacy compliance status
     */
    private Boolean dataPrivacyCompliant;
    
    /**
     * Regulatory compliance status
     */
    private Boolean regulatoryCompliant;
    
    /**
     * Audit compliance status
     */
    private Boolean auditCompliant;
    
    /**
     * Data retention compliance status
     */
    private Boolean retentionCompliant;
    
    /**
     * Overall compliance status
     */
    private Boolean overallCompliant;
    
    /**
     * Compliance score (0-100)
     */
    private Double complianceScore;
    
    /**
     * Compliance violations
     */
    private List<String> violations;
    
    /**
     * Compliance recommendations
     */
    private List<String> recommendations;
    
    /**
     * Compliance report
     */
    private AIComplianceReport report;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * Response timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Whether the response was successful
     */
    private Boolean success;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
    
    /**
     * Compliance status details
     */
    private Map<String, Object> complianceDetails;
    
    /**
     * Risk assessment
     */
    private String riskAssessment;
    
    /**
     * Mitigation recommendations
     */
    private List<String> mitigationRecommendations;
    
    /**
     * Next review date
     */
    private LocalDateTime nextReviewDate;
    
    /**
     * Compliance certificate
     */
    private String complianceCertificate;
    
    /**
     * Compliance badge
     */
    private String complianceBadge;
    
    /**
     * Compliance level
     */
    private String complianceLevel;
    
    /**
     * Compliance maturity
     */
    private String complianceMaturity;
    
    /**
     * Compliance gaps
     */
    private List<String> complianceGaps;
    
    /**
     * Compliance strengths
     */
    private List<String> complianceStrengths;
    
    /**
     * Compliance trends
     */
    private Map<String, Object> complianceTrends;
    
    /**
     * Compliance benchmarks
     */
    private Map<String, Object> complianceBenchmarks;
    
    /**
     * Compliance metrics
     */
    private Map<String, Object> complianceMetrics;
    
    /**
     * Compliance alerts
     */
    private List<String> complianceAlerts;
    
    /**
     * Compliance warnings
     */
    private List<String> complianceWarnings;
    
    /**
     * Compliance errors
     */
    private List<String> complianceErrors;
    
    /**
     * Compliance info
     */
    private List<String> complianceInfo;
    
    /**
     * Compliance debug
     */
    private List<String> complianceDebug;
    
    /**
     * Compliance trace
     */
    private List<String> complianceTrace;
    
    /**
     * Compliance verbose
     */
    private List<String> complianceVerbose;
    
    /**
     * Compliance detailed
     */
    private List<String> complianceDetailed;
    
    /**
     * Compliance summary
     */
    private String complianceSummary;
    
    /**
     * Compliance overview
     */
    private String complianceOverview;
    
    /**
     * Compliance executive summary
     */
    private String complianceExecutiveSummary;
    
    /**
     * Compliance technical summary
     */
    private String complianceTechnicalSummary;
    
    /**
     * Compliance business summary
     */
    private String complianceBusinessSummary;
    
    /**
     * Compliance legal summary
     */
    private String complianceLegalSummary;
    
    /**
     * Compliance regulatory summary
     */
    private String complianceRegulatorySummary;
    
    /**
     * Compliance audit summary
     */
    private String complianceAuditSummary;
    
    /**
     * Compliance privacy summary
     */
    private String compliancePrivacySummary;
    
    /**
     * Compliance security summary
     */
    private String complianceSecuritySummary;
    
    /**
     * Compliance data summary
     */
    private String complianceDataSummary;
    
    /**
     * Compliance process summary
     */
    private String complianceProcessSummary;
    
    /**
     * Compliance procedure summary
     */
    private String complianceProcedureSummary;
    
    /**
     * Compliance policy summary
     */
    private String compliancePolicySummary;
    
    /**
     * Compliance guideline summary
     */
    private String complianceGuidelineSummary;
    
    /**
     * Compliance standard summary
     */
    private String complianceStandardSummary;
    
    /**
     * Compliance framework summary
     */
    private String complianceFrameworkSummary;
    
    /**
     * Compliance methodology summary
     */
    private String complianceMethodologySummary;
    
    /**
     * Compliance tool summary
     */
    private String complianceToolSummary;
    
    /**
     * Compliance technology summary
     */
    private String complianceTechnologySummary;
    
    /**
     * Compliance platform summary
     */
    private String compliancePlatformSummary;
    
    /**
     * Compliance service summary
     */
    private String complianceServiceSummary;
    
    /**
     * Compliance product summary
     */
    private String complianceProductSummary;
    
    /**
     * Compliance solution summary
     */
    private String complianceSolutionSummary;
    
    /**
     * Compliance system summary
     */
    private String complianceSystemSummary;
    
    /**
     * Compliance application summary
     */
    private String complianceApplicationSummary;
    
    /**
     * Compliance database summary
     */
    private String complianceDatabaseSummary;
    
    /**
     * Compliance network summary
     */
    private String complianceNetworkSummary;
    
    /**
     * Compliance infrastructure summary
     */
    private String complianceInfrastructureSummary;
    
    /**
     * Compliance cloud summary
     */
    private String complianceCloudSummary;
    
    /**
     * Compliance on-premises summary
     */
    private String complianceOnPremisesSummary;
    
    /**
     * Compliance hybrid summary
     */
    private String complianceHybridSummary;
    
    /**
     * Compliance multi-cloud summary
     */
    private String complianceMultiCloudSummary;
    
    /**
     * Compliance edge summary
     */
    private String complianceEdgeSummary;
    
    /**
     * Compliance mobile summary
     */
    private String complianceMobileSummary;
    
    /**
     * Compliance IoT summary
     */
    private String complianceIoTSummary;
    
    /**
     * Compliance AI summary
     */
    private String complianceAISummary;
    
    /**
     * Compliance ML summary
     */
    private String complianceMLSummary;
    
    /**
     * Compliance DL summary
     */
    private String complianceDLSummary;
    
    /**
     * Compliance NLP summary
     */
    private String complianceNLPSummary;
    
    /**
     * Compliance CV summary
     */
    private String complianceCVSummary;
    
    /**
     * Compliance RL summary
     */
    private String complianceRLSummary;
    
    /**
     * Compliance GAN summary
     */
    private String complianceGANSummary;
    
    /**
     * Compliance Transformer summary
     */
    private String complianceTransformerSummary;
    
    /**
     * Compliance BERT summary
     */
    private String complianceBERTSummary;
    
    /**
     * Compliance GPT summary
     */
    private String complianceGPTSummary;
    
    /**
     * Compliance T5 summary
     */
    private String complianceT5Summary;
    
    /**
     * Compliance RoBERTa summary
     */
    private String complianceRoBERTaSummary;
    
    /**
     * Compliance DistilBERT summary
     */
    private String complianceDistilBERTSummary;
    
    /**
     * Compliance ALBERT summary
     */
    private String complianceALBERTSummary;
    
    /**
     * Compliance ELECTRA summary
     */
    private String complianceELECTRASummary;
    
    /**
     * Compliance DeBERTa summary
     */
    private String complianceDeBERTaSummary;
    
    /**
     * Compliance Longformer summary
     */
    private String complianceLongformerSummary;
    
    /**
     * Compliance BigBird summary
     */
    private String complianceBigBirdSummary;
    
    /**
     * Compliance Reformer summary
     */
    private String complianceReformerSummary;
    
    /**
     * Compliance Linformer summary
     */
    private String complianceLinformerSummary;
    
    /**
     * Compliance Performer summary
     */
    private String compliancePerformerSummary;
    
    /**
     * Compliance Nyströmformer summary
     */
    private String complianceNystromformerSummary;
    
    /**
     * Compliance Sparse Transformer summary
     */
    private String complianceSparseTransformerSummary;
    
    /**
     * Compliance Switch Transformer summary
     */
    private String complianceSwitchTransformerSummary;
    
    /**
     * Compliance GLaM summary
     */
    private String complianceGLaMSummary;
    
    /**
     * Compliance PaLM summary
     */
    private String compliancePaLMSummary;
    
    /**
     * Compliance LaMDA summary
     */
    private String complianceLaMDASummary;
    
    /**
     * Compliance Chinchilla summary
     */
    private String complianceChinchillaSummary;
    
    /**
     * Compliance Gopher summary
     */
    private String complianceGopherSummary;
    
    /**
     * Compliance Megatron-Turing NLG summary
     */
    private String complianceMegatronTuringNLGSummary;
    
    /**
     * Compliance Jurassic-1 summary
     */
    private String complianceJurassic1Summary;
    
    /**
     * Compliance Jurassic-2 summary
     */
    private String complianceJurassic2Summary;
    
    /**
     * Compliance Codex summary
     */
    private String complianceCodexSummary;
    
    /**
     * Compliance CodeT5 summary
     */
    private String complianceCodeT5Summary;
    
    /**
     * Compliance CodeBERT summary
     */
    private String complianceCodeBERTSummary;
    
    /**
     * Compliance GraphCodeBERT summary
     */
    private String complianceGraphCodeBERTSummary;
    
    /**
     * Compliance PLBART summary
     */
    private String compliancePLBARTSummary;
    
    /**
     * Compliance CodeT5+ summary
     */
    private String complianceCodeT5PlusSummary;
    
    /**
     * Compliance Codex-Davinci summary
     */
    private String complianceCodexDavinciSummary;
    
    /**
     * Compliance Codex-Cushman summary
     */
    private String complianceCodexCushmanSummary;
    
    /**
     * Compliance Codex-Babbage summary
     */
    private String complianceCodexBabbageSummary;
    
    /**
     * Compliance Codex-Ada summary
     */
    private String complianceCodexAdaSummary;
    
    /**
     * Compliance Codex-Curie summary
     */
    private String complianceCodexCurieSummary;
    
    /**
     * Compliance Codex-GPT-3.5-turbo summary
     */
    private String complianceCodexGPT35TurboSummary;
    
    /**
     * Compliance Codex-GPT-4 summary
     */
    private String complianceCodexGPT4Summary;
    
    /**
     * Compliance Codex-GPT-4-turbo summary
     */
    private String complianceCodexGPT4TurboSummary;
    
    /**
     * Compliance Codex-GPT-4o summary
     */
    private String complianceCodexGPT4oSummary;
    
    /**
     * Compliance Codex-GPT-4o-mini summary
     */
    private String complianceCodexGPT4oMiniSummary;
    
    /**
     * Compliance Codex-Claude-3-opus summary
     */
    private String complianceCodexClaude3OpusSummary;
    
    /**
     * Compliance Codex-Claude-3-sonnet summary
     */
    private String complianceCodexClaude3SonnetSummary;
    
    /**
     * Compliance Codex-Claude-3-haiku summary
     */
    private String complianceCodexClaude3HaikuSummary;
    
    /**
     * Compliance Codex-Claude-3.5-sonnet summary
     */
    private String complianceCodexClaude35SonnetSummary;
    
    /**
     * Compliance Codex-Gemini-pro summary
     */
    private String complianceCodexGeminiProSummary;
    
    /**
     * Compliance Codex-Gemini-pro-vision summary
     */
    private String complianceCodexGeminiProVisionSummary;
    
    /**
     * Compliance Codex-Gemini-1.5-pro summary
     */
    private String complianceCodexGemini15ProSummary;
    
    /**
     * Compliance Codex-Gemini-1.5-flash summary
     */
    private String complianceCodexGemini15FlashSummary;
    
    /**
     * Compliance Codex-Gemini-2.0-flash summary
     */
    private String complianceCodexGemini20FlashSummary;
    
    /**
     * Compliance Codex-Llama-2 summary
     */
    private String complianceCodexLlama2Summary;
    
    /**
     * Compliance Codex-Llama-3 summary
     */
    private String complianceCodexLlama3Summary;
    
    /**
     * Compliance Codex-Llama-3.1 summary
     */
    private String complianceCodexLlama31Summary;
    
    /**
     * Compliance Codex-Mistral-7B summary
     */
    private String complianceCodexMistral7bSummary;
    
    /**
     * Compliance Codex-Mistral-8x7B summary
     */
    private String complianceCodexMistral8x7bSummary;
    
    /**
     * Compliance Codex-Mixtral-8x7B summary
     */
    private String complianceCodexMixtral8x7bSummary;
    
    /**
     * Compliance Codex-Mixtral-8x22B summary
     */
    private String complianceCodexMixtral8x22bSummary;
    
    /**
     * Compliance Codex-Codestral-22B summary
     */
    private String complianceCodexCodestral22bSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder summary
     */
    private String complianceCodexDeepseekCoderSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-2 summary
     */
    private String complianceCodexDeepseekCoder2Summary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B summary
     */
    private String complianceCodexDeepseekCoder67bSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B summary
     */
    private String complianceCodexDeepseekCoder33bSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B summary
     */
    private String complianceCodexDeepseekCoder13bSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-instruct summary
     */
    private String complianceCodexDeepseekCoder67bInstructSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-instruct summary
     */
    private String complianceCodexDeepseekCoder33bInstructSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-instruct summary
     */
    private String complianceCodexDeepseekCoder13bInstructSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-chat summary
     */
    private String complianceCodexDeepseekCoder67bChatSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-chat summary
     */
    private String complianceCodexDeepseekCoder33bChatSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-chat summary
     */
    private String complianceCodexDeepseekCoder13bChatSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-python summary
     */
    private String complianceCodexDeepseekCoder67bPythonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-python summary
     */
    private String complianceCodexDeepseekCoder33bPythonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-python summary
     */
    private String complianceCodexDeepseekCoder13bPythonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-java summary
     */
    private String complianceCodexDeepseekCoder67bJavaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-java summary
     */
    private String complianceCodexDeepseekCoder33bJavaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-java summary
     */
    private String complianceCodexDeepseekCoder13bJavaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-cpp summary
     */
    private String complianceCodexDeepseekCoder67bCppSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-cpp summary
     */
    private String complianceCodexDeepseekCoder33bCppSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-cpp summary
     */
    private String complianceCodexDeepseekCoder13bCppSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-javascript summary
     */
    private String complianceCodexDeepseekCoder67bJavascriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-javascript summary
     */
    private String complianceCodexDeepseekCoder33bJavascriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-javascript summary
     */
    private String complianceCodexDeepseekCoder13bJavascriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-typescript summary
     */
    private String complianceCodexDeepseekCoder67bTypescriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-typescript summary
     */
    private String complianceCodexDeepseekCoder33bTypescriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-typescript summary
     */
    private String complianceCodexDeepseekCoder13bTypescriptSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-go summary
     */
    private String complianceCodexDeepseekCoder67bGoSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-go summary
     */
    private String complianceCodexDeepseekCoder33bGoSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-go summary
     */
    private String complianceCodexDeepseekCoder13bGoSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-rust summary
     */
    private String complianceCodexDeepseekCoder67bRustSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-rust summary
     */
    private String complianceCodexDeepseekCoder33bRustSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-rust summary
     */
    private String complianceCodexDeepseekCoder13bRustSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-php summary
     */
    private String complianceCodexDeepseekCoder67bPhpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-php summary
     */
    private String complianceCodexDeepseekCoder33bPhpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-php summary
     */
    private String complianceCodexDeepseekCoder13bPhpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ruby summary
     */
    private String complianceCodexDeepseekCoder67bRubySummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ruby summary
     */
    private String complianceCodexDeepseekCoder33bRubySummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ruby summary
     */
    private String complianceCodexDeepseekCoder13bRubySummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-scala summary
     */
    private String complianceCodexDeepseekCoder67bScalaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-scala summary
     */
    private String complianceCodexDeepseekCoder33bScalaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-scala summary
     */
    private String complianceCodexDeepseekCoder13bScalaSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-kotlin summary
     */
    private String complianceCodexDeepseekCoder67bKotlinSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-kotlin summary
     */
    private String complianceCodexDeepseekCoder33bKotlinSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-kotlin summary
     */
    private String complianceCodexDeepseekCoder13bKotlinSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-swift summary
     */
    private String complianceCodexDeepseekCoder67bSwiftSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-swift summary
     */
    private String complianceCodexDeepseekCoder33bSwiftSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-swift summary
     */
    private String complianceCodexDeepseekCoder13bSwiftSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-dart summary
     */
    private String complianceCodexDeepseekCoder67bDartSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-dart summary
     */
    private String complianceCodexDeepseekCoder33bDartSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-dart summary
     */
    private String complianceCodexDeepseekCoder13bDartSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-csharp summary
     */
    private String complianceCodexDeepseekCoder67bCsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-csharp summary
     */
    private String complianceCodexDeepseekCoder33bCsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-csharp summary
     */
    private String complianceCodexDeepseekCoder13bCsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-vbnet summary
     */
    private String complianceCodexDeepseekCoder67bVbnetSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-vbnet summary
     */
    private String complianceCodexDeepseekCoder33bVbnetSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-vbnet summary
     */
    private String complianceCodexDeepseekCoder13bVbnetSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-fsharp summary
     */
    private String complianceCodexDeepseekCoder67bFsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-fsharp summary
     */
    private String complianceCodexDeepseekCoder33bFsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-fsharp summary
     */
    private String complianceCodexDeepseekCoder13bFsharpSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-haskell summary
     */
    private String complianceCodexDeepseekCoder67bHaskellSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-haskell summary
     */
    private String complianceCodexDeepseekCoder33bHaskellSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-haskell summary
     */
    private String complianceCodexDeepseekCoder13bHaskellSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-clojure summary
     */
    private String complianceCodexDeepseekCoder67bClojureSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-clojure summary
     */
    private String complianceCodexDeepseekCoder33bClojureSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-clojure summary
     */
    private String complianceCodexDeepseekCoder13bClojureSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-erlang summary
     */
    private String complianceCodexDeepseekCoder67bErlangSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-erlang summary
     */
    private String complianceCodexDeepseekCoder33bErlangSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-erlang summary
     */
    private String complianceCodexDeepseekCoder13bErlangSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-elixir summary
     */
    private String complianceCodexDeepseekCoder67bElixirSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-elixir summary
     */
    private String complianceCodexDeepseekCoder33bElixirSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-elixir summary
     */
    private String complianceCodexDeepseekCoder13bElixirSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ocaml summary
     */
    private String complianceCodexDeepseekCoder67bOcamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ocaml summary
     */
    private String complianceCodexDeepseekCoder33bOcamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ocaml summary
     */
    private String complianceCodexDeepseekCoder13bOcamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-racket summary
     */
    private String complianceCodexDeepseekCoder67bRacketSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-racket summary
     */
    private String complianceCodexDeepseekCoder33bRacketSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-racket summary
     */
    private String complianceCodexDeepseekCoder13bRacketSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-scheme summary
     */
    private String complianceCodexDeepseekCoder67bSchemeSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-scheme summary
     */
    private String complianceCodexDeepseekCoder33bSchemeSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-scheme summary
     */
    private String complianceCodexDeepseekCoder13bSchemeSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-lisp summary
     */
    private String complianceCodexDeepseekCoder67bLispSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-lisp summary
     */
    private String complianceCodexDeepseekCoder33bLispSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-lisp summary
     */
    private String complianceCodexDeepseekCoder13bLispSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-prolog summary
     */
    private String complianceCodexDeepseekCoder67bPrologSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-prolog summary
     */
    private String complianceCodexDeepseekCoder33bPrologSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-prolog summary
     */
    private String complianceCodexDeepseekCoder13bPrologSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-sql summary
     */
    private String complianceCodexDeepseekCoder67bSqlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-sql summary
     */
    private String complianceCodexDeepseekCoder33bSqlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-sql summary
     */
    private String complianceCodexDeepseekCoder13bSqlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-html summary
     */
    private String complianceCodexDeepseekCoder67bHtmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-html summary
     */
    private String complianceCodexDeepseekCoder33bHtmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-html summary
     */
    private String complianceCodexDeepseekCoder13bHtmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-css summary
     */
    private String complianceCodexDeepseekCoder67bCssSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-css summary
     */
    private String complianceCodexDeepseekCoder33bCssSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-css summary
     */
    private String complianceCodexDeepseekCoder13bCssSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-xml summary
     */
    private String complianceCodexDeepseekCoder67bXmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-xml summary
     */
    private String complianceCodexDeepseekCoder33bXmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-xml summary
     */
    private String complianceCodexDeepseekCoder13bXmlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-json summary
     */
    private String complianceCodexDeepseekCoder67bJsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-json summary
     */
    private String complianceCodexDeepseekCoder33bJsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-json summary
     */
    private String complianceCodexDeepseekCoder13bJsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-yaml summary
     */
    private String complianceCodexDeepseekCoder67bYamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-yaml summary
     */
    private String complianceCodexDeepseekCoder33bYamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-yaml summary
     */
    private String complianceCodexDeepseekCoder13bYamlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-toml summary
     */
    private String complianceCodexDeepseekCoder67bTomlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-toml summary
     */
    private String complianceCodexDeepseekCoder33bTomlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-toml summary
     */
    private String complianceCodexDeepseekCoder13bTomlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ini summary
     */
    private String complianceCodexDeepseekCoder67bIniSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ini summary
     */
    private String complianceCodexDeepseekCoder33bIniSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ini summary
     */
    private String complianceCodexDeepseekCoder13bIniSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-csv summary
     */
    private String complianceCodexDeepseekCoder67bCsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-csv summary
     */
    private String complianceCodexDeepseekCoder33bCsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-csv summary
     */
    private String complianceCodexDeepseekCoder13bCsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-tsv summary
     */
    private String complianceCodexDeepseekCoder67bTsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-tsv summary
     */
    private String complianceCodexDeepseekCoder33bTsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-tsv summary
     */
    private String complianceCodexDeepseekCoder13bTsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-psv summary
     */
    private String complianceCodexDeepseekCoder67bPsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-psv summary
     */
    private String complianceCodexDeepseekCoder33bPsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-psv summary
     */
    private String complianceCodexDeepseekCoder13bPsvSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-fixed summary
     */
    private String complianceCodexDeepseekCoder67bFixedSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-fixed summary
     */
    private String complianceCodexDeepseekCoder33bFixedSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-fixed summary
     */
    private String complianceCodexDeepseekCoder13bFixedSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-jsonl summary
     */
    private String complianceCodexDeepseekCoder67bJsonlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-jsonl summary
     */
    private String complianceCodexDeepseekCoder33bJsonlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-jsonl summary
     */
    private String complianceCodexDeepseekCoder13bJsonlSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ndjson summary
     */
    private String complianceCodexDeepseekCoder67bNdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ndjson summary
     */
    private String complianceCodexDeepseekCoder33bNdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ndjson summary
     */
    private String complianceCodexDeepseekCoder13bNdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ldjson summary
     */
    private String complianceCodexDeepseekCoder67bLdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ldjson summary
     */
    private String complianceCodexDeepseekCoder33bLdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ldjson summary
     */
    private String complianceCodexDeepseekCoder13bLdjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-jsonc summary
     */
    private String complianceCodexDeepseekCoder67bJsoncSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-jsonc summary
     */
    private String complianceCodexDeepseekCoder33bJsoncSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-jsonc summary
     */
    private String complianceCodexDeepseekCoder13bJsoncSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-json5 summary
     */
    private String complianceCodexDeepseekCoder67bJson5Summary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-json5 summary
     */
    private String complianceCodexDeepseekCoder33bJson5Summary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-json5 summary
     */
    private String complianceCodexDeepseekCoder13bJson5Summary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-hjson summary
     */
    private String complianceCodexDeepseekCoder67bHjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-hjson summary
     */
    private String complianceCodexDeepseekCoder33bHjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-hjson summary
     */
    private String complianceCodexDeepseekCoder13bHjsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-cson summary
     */
    private String complianceCodexDeepseekCoder67bCsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-cson summary
     */
    private String complianceCodexDeepseekCoder33bCsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-cson summary
     */
    private String complianceCodexDeepseekCoder13bCsonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-6.7B-ron summary
     */
    private String complianceCodexDeepseekCoder67bRonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-33B-ron summary
     */
    private String complianceCodexDeepseekCoder33bRonSummary;
    
    /**
     * Compliance Codex-DeepSeek-Coder-1.3B-ron summary
     */
    private String complianceCodexDeepseekCoder13bRonSummary;
    
    /**
     * Additional compliance metrics
     */
    private Map<String, Object> additionalMetrics;
}