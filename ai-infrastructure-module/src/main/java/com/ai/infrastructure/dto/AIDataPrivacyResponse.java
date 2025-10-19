package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Data Privacy Responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIDataPrivacyResponse {
    
    /**
     * Request ID
     */
    private String requestId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Data classification
     */
    private String dataClassification;
    
    /**
     * Compliance status
     */
    private Boolean isCompliant;
    
    /**
     * Processed content
     */
    private String processedContent;
    
    /**
     * Consent required
     */
    private Boolean consentRequired;
    
    /**
     * Privacy recommendations
     */
    private List<String> recommendations;
    
    /**
     * Privacy controls applied
     */
    private Map<String, Object> privacyControls;
    
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
     * Privacy metrics
     */
    private Map<String, Object> privacyMetrics;
    
    /**
     * Data protection recommendations
     */
    private List<String> dataProtectionRecommendations;
    
    /**
     * Compliance recommendations
     */
    private List<String> complianceRecommendations;
    
    /**
     * Risk assessment
     */
    private String riskAssessment;
    
    /**
     * Impact assessment
     */
    private String impactAssessment;
    
    /**
     * Mitigation measures
     */
    private List<String> mitigationMeasures;
    
    /**
     * Monitoring measures
     */
    private List<String> monitoringMeasures;
    
    /**
     * Review recommendations
     */
    private List<String> reviewRecommendations;
    
    /**
     * Next review date
     */
    private LocalDateTime nextReviewDate;
    
    /**
     * Privacy level
     */
    private String privacyLevel;
    
    /**
     * Privacy maturity
     */
    private String privacyMaturity;
    
    /**
     * Privacy gaps
     */
    private List<String> privacyGaps;
    
    /**
     * Privacy improvements
     */
    private List<String> privacyImprovements;
    
    /**
     * Privacy training needs
     */
    private List<String> privacyTrainingNeeds;
    
    /**
     * Privacy documentation
     */
    private List<String> privacyDocumentation;
    
    /**
     * Privacy policies
     */
    private List<String> privacyPolicies;
    
    /**
     * Privacy procedures
     */
    private List<String> privacyProcedures;
    
    /**
     * Privacy guidelines
     */
    private List<String> privacyGuidelines;
    
    /**
     * Privacy standards
     */
    private List<String> privacyStandards;
    
    /**
     * Privacy frameworks
     */
    private List<String> privacyFrameworks;
    
    /**
     * Privacy methodologies
     */
    private List<String> privacyMethodologies;
    
    /**
     * Privacy tools
     */
    private List<String> privacyTools;
    
    /**
     * Privacy technologies
     */
    private List<String> privacyTechnologies;
    
    /**
     * Privacy platforms
     */
    private List<String> privacyPlatforms;
    
    /**
     * Privacy services
     */
    private List<String> privacyServices;
    
    /**
     * Privacy products
     */
    private List<String> privacyProducts;
    
    /**
     * Privacy solutions
     */
    private List<String> privacySolutions;
    
    /**
     * Privacy systems
     */
    private List<String> privacySystems;
    
    /**
     * Privacy applications
     */
    private List<String> privacyApplications;
    
    /**
     * Privacy databases
     */
    private List<String> privacyDatabases;
    
    /**
     * Privacy networks
     */
    private List<String> privacyNetworks;
    
    /**
     * Privacy infrastructure
     */
    private List<String> privacyInfrastructure;
    
    /**
     * Privacy cloud
     */
    private List<String> privacyCloud;
    
    /**
     * Privacy on-premises
     */
    private List<String> privacyOnPremises;
    
    /**
     * Privacy hybrid
     */
    private List<String> privacyHybrid;
    
    /**
     * Privacy multi-cloud
     */
    private List<String> privacyMultiCloud;
    
    /**
     * Privacy edge
     */
    private List<String> privacyEdge;
    
    /**
     * Privacy mobile
     */
    private List<String> privacyMobile;
    
    /**
     * Privacy IoT
     */
    private List<String> privacyIoT;
    
    /**
     * Privacy AI
     */
    private List<String> privacyAI;
    
    /**
     * Privacy ML
     */
    private List<String> privacyML;
    
    /**
     * Privacy DL
     */
    private List<String> privacyDL;
    
    /**
     * Privacy NLP
     */
    private List<String> privacyNLP;
    
    /**
     * Privacy CV
     */
    private List<String> privacyCV;
    
    /**
     * Privacy RL
     */
    private List<String> privacyRL;
    
    /**
     * Privacy GAN
     */
    private List<String> privacyGAN;
    
    /**
     * Privacy Transformer
     */
    private List<String> privacyTransformer;
    
    /**
     * Privacy BERT
     */
    private List<String> privacyBERT;
    
    /**
     * Privacy GPT
     */
    private List<String> privacyGPT;
    
    /**
     * Privacy T5
     */
    private List<String> privacyT5;
    
    /**
     * Privacy RoBERTa
     */
    private List<String> privacyRoBERTa;
    
    /**
     * Privacy DistilBERT
     */
    private List<String> privacyDistilBERT;
    
    /**
     * Privacy ALBERT
     */
    private List<String> privacyALBERT;
    
    /**
     * Privacy ELECTRA
     */
    private List<String> privacyELECTRA;
    
    /**
     * Privacy DeBERTa
     */
    private List<String> privacyDeBERTa;
    
    /**
     * Privacy Longformer
     */
    private List<String> privacyLongformer;
    
    /**
     * Privacy BigBird
     */
    private List<String> privacyBigBird;
    
    /**
     * Privacy Reformer
     */
    private List<String> privacyReformer;
    
    /**
     * Privacy Linformer
     */
    private List<String> privacyLinformer;
    
    /**
     * Privacy Performer
     */
    private List<String> privacyPerformer;
    
    /**
     * Privacy Nyströmformer
     */
    private List<String> privacyNystromformer;
    
    /**
     * Privacy Sparse Transformer
     */
    private List<String> privacySparseTransformer;
    
    /**
     * Privacy Switch Transformer
     */
    private List<String> privacySwitchTransformer;
    
    /**
     * Privacy GLaM
     */
    private List<String> privacyGLaM;
    
    /**
     * Privacy PaLM
     */
    private List<String> privacyPaLM;
    
    /**
     * Privacy LaMDA
     */
    private List<String> privacyLaMDA;
    
    /**
     * Privacy Chinchilla
     */
    private List<String> privacyChinchilla;
    
    /**
     * Privacy Gopher
     */
    private List<String> privacyGopher;
    
    /**
     * Privacy Megatron-Turing NLG
     */
    private List<String> privacyMegatronTuringNLG;
    
    /**
     * Privacy Jurassic-1
     */
    private List<String> privacyJurassic1;
    
    /**
     * Privacy Jurassic-2
     */
    private List<String> privacyJurassic2;
    
    /**
     * Privacy Codex
     */
    private List<String> privacyCodex;
    
    /**
     * Privacy CodeT5
     */
    private List<String> privacyCodeT5;
    
    /**
     * Privacy CodeBERT
     */
    private List<String> privacyCodeBERT;
    
    /**
     * Privacy GraphCodeBERT
     */
    private List<String> privacyGraphCodeBERT;
    
    /**
     * Privacy PLBART
     */
    private List<String> privacyPLBART;
    
    /**
     * Privacy CodeT5+
     */
    private List<String> privacyCodeT5Plus;
    
    /**
     * Privacy Codex-Davinci
     */
    private List<String> privacyCodexDavinci;
    
    /**
     * Privacy Codex-Cushman
     */
    private List<String> privacyCodexCushman;
    
    /**
     * Privacy Codex-Babbage
     */
    private List<String> privacyCodexBabbage;
    
    /**
     * Privacy Codex-Ada
     */
    private List<String> privacyCodexAda;
    
    /**
     * Privacy Codex-Curie
     */
    private List<String> privacyCodexCurie;
    
    /**
     * Privacy Codex-GPT-3.5-turbo
     */
    private List<String> privacyCodexGpt35Turbo;
    
    /**
     * Privacy Codex-GPT-4
     */
    private List<String> privacyCodexGpt4;
    
    /**
     * Privacy Codex-GPT-4-turbo
     */
    private List<String> privacyCodexGpt4Turbo;
    
    /**
     * Privacy Codex-GPT-4o
     */
    private List<String> privacyCodexGpt4o;
    
    /**
     * Privacy Codex-GPT-4o-mini
     */
    private List<String> privacyCodexGpt4oMini;
    
    /**
     * Privacy Codex-Claude-3-opus
     */
    private List<String> privacyCodexClaude3Opus;
    
    /**
     * Privacy Codex-Claude-3-sonnet
     */
    private List<String> privacyCodexClaude3Sonnet;
    
    /**
     * Privacy Codex-Claude-3-haiku
     */
    private List<String> privacyCodexClaude3Haiku;
    
    /**
     * Privacy Codex-Claude-3.5-sonnet
     */
    private List<String> privacyCodexClaude35Sonnet;
    
    /**
     * Privacy Codex-Gemini-pro
     */
    private List<String> privacyCodexGeminiPro;
    
    /**
     * Privacy Codex-Gemini-pro-vision
     */
    private List<String> privacyCodexGeminiProVision;
    
    /**
     * Privacy Codex-Gemini-1.5-pro
     */
    private List<String> privacyCodexGemini15Pro;
    
    /**
     * Privacy Codex-Gemini-1.5-flash
     */
    private List<String> privacyCodexGemini15Flash;
    
    /**
     * Privacy Codex-Gemini-2.0-flash
     */
    private List<String> privacyCodexGemini20Flash;
    
    /**
     * Privacy Codex-Llama-2
     */
    private List<String> privacyCodexLlama2;
    
    /**
     * Privacy Codex-Llama-3
     */
    private List<String> privacyCodexLlama3;
    
    /**
     * Privacy Codex-Llama-3.1
     */
    private List<String> privacyCodexLlama31;
    
    /**
     * Privacy Codex-Mistral-7B
     */
    private List<String> privacyCodexMistral7b;
    
    /**
     * Privacy Codex-Mistral-8x7B
     */
    private List<String> privacyCodexMistral8x7b;
    
    /**
     * Privacy Codex-Mixtral-8x7B
     */
    private List<String> privacyCodexMixtral8x7b;
    
    /**
     * Privacy Codex-Mixtral-8x22B
     */
    private List<String> privacyCodexMixtral8x22b;
    
    /**
     * Privacy Codex-Codestral-22B
     */
    private List<String> privacyCodexCodestral22b;
    
    /**
     * Privacy Codex-DeepSeek-Coder
     */
    private List<String> privacyCodexDeepseekCoder;
    
    /**
     * Privacy Codex-DeepSeek-Coder-2
     */
    private List<String> privacyCodexDeepseekCoder2;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B
     */
    private List<String> privacyCodexDeepseekCoder67b;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B
     */
    private List<String> privacyCodexDeepseekCoder33b;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B
     */
    private List<String> privacyCodexDeepseekCoder13b;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-instruct
     */
    private List<String> privacyCodexDeepseekCoder67bInstruct;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-instruct
     */
    private List<String> privacyCodexDeepseekCoder33bInstruct;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-instruct
     */
    private List<String> privacyCodexDeepseekCoder13bInstruct;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-chat
     */
    private List<String> privacyCodexDeepseekCoder67bChat;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-chat
     */
    private List<String> privacyCodexDeepseekCoder33bChat;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-chat
     */
    private List<String> privacyCodexDeepseekCoder13bChat;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-python
     */
    private List<String> privacyCodexDeepseekCoder67bPython;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-python
     */
    private List<String> privacyCodexDeepseekCoder33bPython;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-python
     */
    private List<String> privacyCodexDeepseekCoder13bPython;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-java
     */
    private List<String> privacyCodexDeepseekCoder67bJava;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-java
     */
    private List<String> privacyCodexDeepseekCoder33bJava;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-java
     */
    private List<String> privacyCodexDeepseekCoder13bJava;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-cpp
     */
    private List<String> privacyCodexDeepseekCoder67bCpp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-cpp
     */
    private List<String> privacyCodexDeepseekCoder33bCpp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-cpp
     */
    private List<String> privacyCodexDeepseekCoder13bCpp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-javascript
     */
    private List<String> privacyCodexDeepseekCoder67bJavascript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-javascript
     */
    private List<String> privacyCodexDeepseekCoder33bJavascript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-javascript
     */
    private List<String> privacyCodexDeepseekCoder13bJavascript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-typescript
     */
    private List<String> privacyCodexDeepseekCoder67bTypescript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-typescript
     */
    private List<String> privacyCodexDeepseekCoder33bTypescript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-typescript
     */
    private List<String> privacyCodexDeepseekCoder13bTypescript;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-go
     */
    private List<String> privacyCodexDeepseekCoder67bGo;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-go
     */
    private List<String> privacyCodexDeepseekCoder33bGo;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-go
     */
    private List<String> privacyCodexDeepseekCoder13bGo;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-rust
     */
    private List<String> privacyCodexDeepseekCoder67bRust;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-rust
     */
    private List<String> privacyCodexDeepseekCoder33bRust;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-rust
     */
    private List<String> privacyCodexDeepseekCoder13bRust;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-php
     */
    private List<String> privacyCodexDeepseekCoder67bPhp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-php
     */
    private List<String> privacyCodexDeepseekCoder33bPhp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-php
     */
    private List<String> privacyCodexDeepseekCoder13bPhp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-ruby
     */
    private List<String> privacyCodexDeepseekCoder67bRuby;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-ruby
     */
    private List<String> privacyCodexDeepseekCoder33bRuby;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-ruby
     */
    private List<String> privacyCodexDeepseekCoder13bRuby;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-scala
     */
    private List<String> privacyCodexDeepseekCoder67bScala;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-scala
     */
    private List<String> privacyCodexDeepseekCoder33bScala;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-scala
     */
    private List<String> privacyCodexDeepseekCoder13bScala;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-kotlin
     */
    private List<String> privacyCodexDeepseekCoder67bKotlin;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-kotlin
     */
    private List<String> privacyCodexDeepseekCoder33bKotlin;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-kotlin
     */
    private List<String> privacyCodexDeepseekCoder13bKotlin;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-swift
     */
    private List<String> privacyCodexDeepseekCoder67bSwift;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-swift
     */
    private List<String> privacyCodexDeepseekCoder33bSwift;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-swift
     */
    private List<String> privacyCodexDeepseekCoder13bSwift;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-dart
     */
    private List<String> privacyCodexDeepseekCoder67bDart;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-dart
     */
    private List<String> privacyCodexDeepseekCoder33bDart;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-dart
     */
    private List<String> privacyCodexDeepseekCoder13bDart;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-csharp
     */
    private List<String> privacyCodexDeepseekCoder67bCsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-csharp
     */
    private List<String> privacyCodexDeepseekCoder33bCsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-csharp
     */
    private List<String> privacyCodexDeepseekCoder13bCsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-vbnet
     */
    private List<String> privacyCodexDeepseekCoder67bVbnet;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-vbnet
     */
    private List<String> privacyCodexDeepseekCoder33bVbnet;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-vbnet
     */
    private List<String> privacyCodexDeepseekCoder13bVbnet;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-fsharp
     */
    private List<String> privacyCodexDeepseekCoder67bFsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-fsharp
     */
    private List<String> privacyCodexDeepseekCoder33bFsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-fsharp
     */
    private List<String> privacyCodexDeepseekCoder13bFsharp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-haskell
     */
    private List<String> privacyCodexDeepseekCoder67bHaskell;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-haskell
     */
    private List<String> privacyCodexDeepseekCoder33bHaskell;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-haskell
     */
    private List<String> privacyCodexDeepseekCoder13bHaskell;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-clojure
     */
    private List<String> privacyCodexDeepseekCoder67bClojure;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-clojure
     */
    private List<String> privacyCodexDeepseekCoder33bClojure;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-clojure
     */
    private List<String> privacyCodexDeepseekCoder13bClojure;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-erlang
     */
    private List<String> privacyCodexDeepseekCoder67bErlang;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-erlang
     */
    private List<String> privacyCodexDeepseekCoder33bErlang;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-erlang
     */
    private List<String> privacyCodexDeepseekCoder13bErlang;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-elixir
     */
    private List<String> privacyCodexDeepseekCoder67bElixir;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-elixir
     */
    private List<String> privacyCodexDeepseekCoder33bElixir;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-elixir
     */
    private List<String> privacyCodexDeepseekCoder13bElixir;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-ocaml
     */
    private List<String> privacyCodexDeepseekCoder67bOcaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-ocaml
     */
    private List<String> privacyCodexDeepseekCoder33bOcaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-ocaml
     */
    private List<String> privacyCodexDeepseekCoder13bOcaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-racket
     */
    private List<String> privacyCodexDeepseekCoder67bRacket;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-racket
     */
    private List<String> privacyCodexDeepseekCoder33bRacket;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-racket
     */
    private List<String> privacyCodexDeepseekCoder13bRacket;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-scheme
     */
    private List<String> privacyCodexDeepseekCoder67bScheme;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-scheme
     */
    private List<String> privacyCodexDeepseekCoder33bScheme;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-scheme
     */
    private List<String> privacyCodexDeepseekCoder13bScheme;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-lisp
     */
    private List<String> privacyCodexDeepseekCoder67bLisp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-lisp
     */
    private List<String> privacyCodexDeepseekCoder33bLisp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-lisp
     */
    private List<String> privacyCodexDeepseekCoder13bLisp;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-prolog
     */
    private List<String> privacyCodexDeepseekCoder67bProlog;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-prolog
     */
    private List<String> privacyCodexDeepseekCoder33bProlog;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-prolog
     */
    private List<String> privacyCodexDeepseekCoder13bProlog;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-sql
     */
    private List<String> privacyCodexDeepseekCoder67bSql;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-sql
     */
    private List<String> privacyCodexDeepseekCoder33bSql;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-sql
     */
    private List<String> privacyCodexDeepseekCoder13bSql;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-html
     */
    private List<String> privacyCodexDeepseekCoder67bHtml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-html
     */
    private List<String> privacyCodexDeepseekCoder33bHtml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-html
     */
    private List<String> privacyCodexDeepseekCoder13bHtml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-css
     */
    private List<String> privacyCodexDeepseekCoder67bCss;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-css
     */
    private List<String> privacyCodexDeepseekCoder33bCss;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-css
     */
    private List<String> privacyCodexDeepseekCoder13bCss;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-xml
     */
    private List<String> privacyCodexDeepseekCoder67bXml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-xml
     */
    private List<String> privacyCodexDeepseekCoder33bXml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-xml
     */
    private List<String> privacyCodexDeepseekCoder13bXml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-json
     */
    private List<String> privacyCodexDeepseekCoder67bJson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-json
     */
    private List<String> privacyCodexDeepseekCoder33bJson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-json
     */
    private List<String> privacyCodexDeepseekCoder13bJson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-yaml
     */
    private List<String> privacyCodexDeepseekCoder67bYaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-yaml
     */
    private List<String> privacyCodexDeepseekCoder33bYaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-yaml
     */
    private List<String> privacyCodexDeepseekCoder13bYaml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-toml
     */
    private List<String> privacyCodexDeepseekCoder67bToml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-toml
     */
    private List<String> privacyCodexDeepseekCoder33bToml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-toml
     */
    private List<String> privacyCodexDeepseekCoder13bToml;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-ini
     */
    private List<String> privacyCodexDeepseekCoder67bIni;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-ini
     */
    private List<String> privacyCodexDeepseekCoder33bIni;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-ini
     */
    private List<String> privacyCodexDeepseekCoder13bIni;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-csv
     */
    private List<String> privacyCodexDeepseekCoder67bCsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-csv
     */
    private List<String> privacyCodexDeepseekCoder33bCsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-csv
     */
    private List<String> privacyCodexDeepseekCoder13bCsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-tsv
     */
    private List<String> privacyCodexDeepseekCoder67bTsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-tsv
     */
    private List<String> privacyCodexDeepseekCoder33bTsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-tsv
     */
    private List<String> privacyCodexDeepseekCoder13bTsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-psv
     */
    private List<String> privacyCodexDeepseekCoder67bPsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-psv
     */
    private List<String> privacyCodexDeepseekCoder33bPsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-psv
     */
    private List<String> privacyCodexDeepseekCoder13bPsv;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-fixed
     */
    private List<String> privacyCodexDeepseekCoder67bFixed;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-fixed
     */
    private List<String> privacyCodexDeepseekCoder33bFixed;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-fixed
     */
    private List<String> privacyCodexDeepseekCoder13bFixed;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-jsonl
     */
    private List<String> privacyCodexDeepseekCoder67bJsonl;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-jsonl
     */
    private List<String> privacyCodexDeepseekCoder33bJsonl;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-jsonl
     */
    private List<String> privacyCodexDeepseekCoder13bJsonl;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-ndjson
     */
    private List<String> privacyCodexDeepseekCoder67bNdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-ndjson
     */
    private List<String> privacyCodexDeepseekCoder33bNdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-ndjson
     */
    private List<String> privacyCodexDeepseekCoder13bNdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-ldjson
     */
    private List<String> privacyCodexDeepseekCoder67bLdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-ldjson
     */
    private List<String> privacyCodexDeepseekCoder33bLdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-ldjson
     */
    private List<String> privacyCodexDeepseekCoder13bLdjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-jsonc
     */
    private List<String> privacyCodexDeepseekCoder67bJsonc;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-jsonc
     */
    private List<String> privacyCodexDeepseekCoder33bJsonc;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-jsonc
     */
    private List<String> privacyCodexDeepseekCoder13bJsonc;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-json5
     */
    private List<String> privacyCodexDeepseekCoder67bJson5;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-json5
     */
    private List<String> privacyCodexDeepseekCoder33bJson5;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-json5
     */
    private List<String> privacyCodexDeepseekCoder13bJson5;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-hjson
     */
    private List<String> privacyCodexDeepseekCoder67bHjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-hjson
     */
    private List<String> privacyCodexDeepseekCoder33bHjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-hjson
     */
    private List<String> privacyCodexDeepseekCoder13bHjson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-6.7B-cson
     */
    private List<String> privacyCodexDeepseekCoder67bCson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-33B-cson
     */
    private List<String> privacyCodexDeepseekCoder33bCson;
    
    /**
     * Privacy Codex-DeepSeek-Coder-1.3B-cson
     */
    private List<String> privacyCodexDeepseekCoder13bCson;
    
    /**
     * Additional privacy metrics
     */
    private Map<String, Object> additionalPrivacyMetrics;
}