# 🎯 AI-Core Module: 10 Real-Life Use Case Applications

**Document Purpose:** Showcase 10 focused, real-life applications that fully demonstrate AI-core module capabilities  
**Tech Stack:** ONNX (embeddings) + OpenAI (LLM) + Lucene (search) + H2 (database)  
**Project Scope:** Small, focused apps (2-3 weeks each), production-ready with tests  
**Last Updated:** November 2025

---

## 📋 Table of Contents

1. [Quick Overview](#quick-overview)
2. [Use Case #1: Smart Resume Analyzer](#use-case-1-smart-resume-analyzer)
3. [Use Case #2: E-Commerce Product Recommender](#use-case-2-e-commerce-product-recommender)
4. [Use Case #3: Legal Document Search & Q&A](#use-case-3-legal-document-search--qa)
5. [Use Case #4: Customer Support Ticket Classifier](#use-case-4-customer-support-ticket-classifier)
6. [Use Case #5: Real Estate Property Finder](#use-case-5-real-estate-property-finder)
7. [Use Case #6: Medical Records Analyzer](#use-case-6-medical-records-analyzer)
8. [Use Case #7: Content Moderation Engine](#use-case-7-content-moderation-engine)
9. [Use Case #8: Job Posting Matcher](#use-case-8-job-posting-matcher)
10. [Use Case #9: Document Summarization Engine](#use-case-9-document-summarization-engine)
11. [Use Case #10: Behavior Analytics Dashboard](#use-case-10-behavior-analytics-dashboard)
12. [Implementation Roadmap](#implementation-roadmap)
13. [Deployment & Testing Strategy](#deployment--testing-strategy)

---

## Quick Overview

| Use Case | Core Capability | Tech Stack | Effort | Users | Revenue Potential |
|----------|-----------------|-----------|--------|-------|-------------------|
| Resume Analyzer | Semantic matching + Classification | ONNX + Lucene | 2w | HR Teams | $500-2K/mo |
| Product Recommender | Vector search + Personalization | ONNX + Lucene | 2w | E-commerce | $1K-5K/mo |
| Legal Search | RAG + Full-text search | OpenAI + Lucene | 2w | Law Firms | $2K-10K/mo |
| Support Classifier | PII Detection + Classification | OpenAI + Behavior | 2w | SaaS | $500-3K/mo |
| Property Finder | Geo-semantic search | ONNX + Lucene | 1.5w | Real Estate | $1K-4K/mo |
| Medical Analyzer | HIPAA + Entity extraction | OpenAI + Behavior | 3w | Healthcare | $3K-15K/mo |
| Content Moderation | Multi-policy enforcement | OpenAI + Behavior | 2w | Platforms | $1K-5K/mo |
| Job Matcher | Skills matching + Ranking | ONNX + Lucene | 2w | Recruiters | $1K-4K/mo |
| Summarization | Document processing | OpenAI + RAG | 1.5w | Publishers | $500-2K/mo |
| Analytics Dashboard | Event ingestion + KPIs | Behavior Module | 2w | All | $1K-3K/mo |

---

## Use Case #1: Smart Resume Analyzer

### 🎯 Problem Statement
HR departments spend hours manually reviewing resumes, missing qualified candidates and creating hiring bias. Need automated, fair resume screening with semantic understanding.

### 📊 Solution Architecture

```
┌─────────────────┐
│  Resume Upload  │
│  (PDF/DOCX)     │
└────────┬────────┘
         ↓
┌─────────────────┐
│ Text Extraction │  (Apache Tika)
└────────┬────────┘
         ↓
┌──────────────────────────┐
│ Entity Extraction        │  (OpenAI)
│ - Skills                 │
│ - Experience Years       │
│ - Education Level        │
│ - Certifications         │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embedding           │  (Local, fast)
│ Generate vector for      │
│ full resume content      │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Index             │
│ Store resume + metadata  │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ H2 Database              │
│ Resume records + JD refs │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Semantic Matching        │  (ONNX vectors)
│ vs Job Description       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Score & Rank             │  (0-100 match %)
│ Generate Report          │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Embeddings:** ONNX sentence-transformers (fast, no API cost)
- **Extraction:** OpenAI for skills/certifications
- **Search:** Lucene for full-text + semantic search
- **Storage:** H2 with resume text + structured fields
- **Features:**
  - Upload 1000+ resumes, auto-index in <5 min
  - Match against 50+ job descriptions simultaneously
  - Generate fairness report (diverse candidate pool)
  - Export top 50 matches with reasoning

### 💾 Database Schema
```sql
CREATE TABLE resumes (
    id UUID PRIMARY KEY,
    user_id UUID,
    file_name VARCHAR,
    raw_text CLOB,
    extracted_skills JSON,
    years_experience INT,
    education_level VARCHAR,
    certifications ARRAY,
    embedding BLOB,  -- ONNX vector
    created_at TIMESTAMP,
    INDEX lucene_full_text (raw_text)
);

CREATE TABLE job_postings (
    id UUID PRIMARY KEY,
    title VARCHAR,
    description CLOB,
    required_skills ARRAY,
    min_years INT,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    resume_id UUID,
    job_id UUID,
    match_score DECIMAL,
    reasoning TEXT,
    created_at TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Throughput:** 1000 resumes/hour
- **Accuracy:** >90% skill detection
- **Latency:** <500ms per match
- **Cost:** ~$0.01 per resume (mostly ONNX local)

### 📱 UI/UX
- Drag-drop resume upload with progress
- Real-time match scoring display
- Exportable reports (CSV/PDF)
- Fairness metrics dashboard

---

## Use Case #2: E-Commerce Product Recommender

### 🎯 Problem Statement
Online retailers struggle with low conversion rates because generic product recommendations don't match customer intent. Need semantic understanding of products + customer browsing behavior for personalized suggestions.

### 📊 Solution Architecture

```
┌──────────────────┐
│ Product Catalog  │  (100K+ SKUs)
│ with metadata    │
└────────┬─────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embeddings          │  (Generated once, cached)
│ For each product:        │
│ - Title                  │
│ - Description            │
│ - Category tags          │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Index             │
│ Product catalog vectors  │
│ + full-text (title/desc) │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Behavior Module          │  (Track user actions)
│ - Product views          │
│ - Add to cart            │
│ - Purchases              │
│ - Time spent             │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Real-time Recommendation │
│ 1. User embedding        │
│ 2. Semantic similarity   │
│ 3. Collaborative filter  │
│ 4. Business rules        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Rank & Filter            │
│ - Inventory              │
│ - Margin                 │
│ - Diversity              │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Return Top 10            │
│ Personalized             │
│ recommendations          │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Embeddings:** ONNX (same as content search)
- **Indexing:** Lucene for 100K+ products (partitioned by category)
- **Behavior Tracking:** Behavior Module ingests view/cart/purchase events
- **Real-time:** Sub-100ms latency per request
- **Features:**
  - "Similar products" within 50ms
  - "Customers who viewed also bought" (collaborative)
  - A/B test different ranking strategies
  - Handles cold-start via content-based fallback

### 💾 Database Schema
```sql
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR UNIQUE,
    title VARCHAR,
    description CLOB,
    category VARCHAR,
    price DECIMAL,
    embedding BLOB,  -- ONNX vector
    stock_qty INT,
    margin_percent INT,
    created_at TIMESTAMP,
    INDEX lucene_catalog (title, description, category)
);

CREATE TABLE user_behavior_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR,  -- 'view', 'cart', 'purchase'
    product_id UUID,
    timestamp TIMESTAMP,
    session_id VARCHAR
);

CREATE TABLE recommendations (
    id UUID PRIMARY KEY,
    user_id UUID,
    product_id UUID,
    rank INT,
    score DECIMAL,
    reason VARCHAR,  -- 'similar', 'collaborative', 'trending'
    created_at TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **CTR Improvement:** +35-45% from baseline
- **Latency:** <100ms for 10 recommendations
- **Throughput:** 10K req/sec per server
- **Cost:** ~$0.0001 per recommendation

### 📱 UI/UX
- "Similar products" carousel on product page
- "Recommended for you" homepage widget
- Explanation tooltips ("because you viewed X")
- Horizontal scroll with images

---

## Use Case #3: Legal Document Search & Q&A

### 🎯 Problem Statement
Law firms have millions of pages of case law, contracts, and precedents. Lawyers spend days searching for relevant cases. Need semantic search + RAG for instant Q&A.

### 📊 Solution Architecture

```
┌──────────────────────────┐
│ Upload Case Law          │
│ Contracts, Precedents    │
│ (100K+ docs, 1B+ pages)  │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Document Processing      │
│ - Extract text           │
│ - Identify sections      │
│ - Preserve metadata      │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Chunking Strategy        │
│ - Respect document       │
│   structure              │
│ - 512-token chunks       │
│ - Overlap windows        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embeddings          │  (Fast, local)
│ Each chunk vector        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Indexing          │
│ Semantic + full-text     │
│ search (BM25 hybrid)     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ H2 Storage               │
│ Chunks + metadata +      │
│ cross-references         │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ User Query (Natural Lang)│
│ "Find cases where...'     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Hybrid Retrieval         │
│ 1. Semantic search       │
│    (ONNX similarity)     │
│ 2. Full-text search      │
│    (Lucene BM25)         │
│ 3. RRF ranking           │
│ 4. Return top 20 chunks  │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ RAG with OpenAI          │
│ Context: top 20 chunks   │
│ Generate answer with     │
│ case citations           │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Return:                  │
│ - Answer text            │
│ - Source docs + pages    │
│ - Confidence score       │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Embeddings:** ONNX (legal domain fine-tuned optional)
- **Search:** Lucene + hybrid BM25/semantic ranking
- **RAG:** OpenAI GPT-4 with context window optimization
- **Storage:** H2 with full document graph relationships
- **Features:**
  - Search 1M+ pages in <500ms
  - Multi-citation RAG (cite 5+ supporting cases)
  - Legal citation format auto-generation
  - Precedent change tracking

### 💾 Database Schema
```sql
CREATE TABLE legal_documents (
    id UUID PRIMARY KEY,
    case_id VARCHAR UNIQUE,
    title VARCHAR,
    document_type VARCHAR,  -- 'case', 'statute', 'contract'
    full_text CLOB,
    metadata JSON,
    upload_date TIMESTAMP
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID,
    chunk_index INT,
    text VARCHAR(2000),
    embedding BLOB,  -- ONNX vector
    page_number INT,
    section_title VARCHAR,
    citations ARRAY,
    INDEX lucene_chunks (text)
);

CREATE TABLE chunk_relationships (
    id UUID PRIMARY KEY,
    source_chunk_id UUID,
    target_chunk_id UUID,
    relation_type VARCHAR,  -- 'cites', 'contradicts', 'extends'
    confidence DECIMAL
);

CREATE TABLE search_queries (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    results_count INT,
    execution_time_ms INT,
    created_at TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Relevance:** >85% top-5 relevance score
- **Latency:** <1000ms for complex queries
- **Coverage:** Index 50M+ legal documents
- **Cost:** ~$0.05 per query (mostly OpenAI)

### 📱 UI/UX
- Natural language query box
- Real-time result preview
- Color-coded citations
- Export to Word/PDF with formatting

---

## Use Case #4: Customer Support Ticket Classifier

### 🎯 Problem Statement
Support teams receive 1000s of tickets daily. Manual categorization is slow and inconsistent. Need auto-classification + priority routing with PII detection (GDPR compliance).

### 📊 Solution Architecture

```
┌─────────────────┐
│ Incoming Ticket │
│ Customer email  │
└────────┬────────┘
         ↓
┌──────────────────────────┐
│ PII Detection            │  (Privacy-first)
│ - Credit cards           │
│ - SSN/Phone/Email        │
│ - Medical records        │
│ - Redact if found        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Behavior Module Ingestion│  (Track events)
│ Event type: 'ticket'     │
│ Attributes: category,    │
│ priority, sentiment       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Classification Models    │
│ 1. Category (OpenAI)     │
│    - Billing             │
│    - Technical Support   │
│    - Feature Request     │
│    - Bug Report          │
│ 2. Priority (Rules)      │
│    - Urgent              │
│    - High                │
│    - Normal              │
│    - Low                 │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Sentiment Analysis       │  (OpenAI)
│ - Positive/Neutral       │
│ - Negative/Angry         │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Suggested Actions        │
│ - Auto-resolve tickets   │
│ - Route to queue         │
│ - Escalate if needed     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ H2 Storage               │
│ Classified tickets +     │
│ confidence + audit       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Dashboard Display        │
│ - Categorized queue      │
│ - Auto-actions applied   │
│ - Manual review fallback │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **PII Detection:** OpenAI or regex-based (configurable)
- **Classification:** OpenAI with few-shot examples
- **Event Tracking:** Behavior Module (ingestion + analytics)
- **Audit Trail:** All PII redaction logged
- **Features:**
  - Classify 10K tickets/day
  - 95% accuracy on categories
  - Auto-resolve 30% of common issues
  - Fairness metrics (routing distribution)

### 💾 Database Schema
```sql
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    ticket_number VARCHAR UNIQUE,
    customer_id UUID,
    original_text CLOB,
    redacted_text CLOB,  -- PII removed
    category VARCHAR,
    priority VARCHAR,
    sentiment VARCHAR,
    confidence DECIMAL,
    auto_resolved BOOLEAN,
    assigned_queue VARCHAR,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE pii_redaction_log (
    id UUID PRIMARY KEY,
    ticket_id UUID,
    pii_type VARCHAR,  -- 'credit_card', 'ssn', etc.
    redacted_value VARCHAR,  -- hash of actual value
    timestamp TIMESTAMP,
    CONSTRAINT check_audit (redacted_value IS NOT NULL)
);

CREATE TABLE classification_feedback (
    id UUID PRIMARY KEY,
    ticket_id UUID,
    classifier VARCHAR,  -- 'category', 'priority'
    system_prediction VARCHAR,
    human_correction VARCHAR,
    is_correct BOOLEAN,
    created_at TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Accuracy:** >92% category accuracy
- **Throughput:** 10K tickets/day
- **PII Detection:** 99%+ for credit cards, 95%+ for SSN
- **Cost:** ~$0.005 per ticket

### 📱 UI/UX
- Real-time ticket dashboard (auto-refreshing)
- Bulk actions (reassign, auto-resolve)
- PII detection warnings (yellow highlight)
- Classification feedback loop (thumbs up/down)

---

## Use Case #5: Real Estate Property Finder

### 🎯 Problem Statement
Real estate portals have 1M+ listings. Buyers need to filter by vague criteria ("cozy European cottage near mountains"). Geo-spatial + semantic search together.

### 📊 Solution Architecture

```
┌──────────────────┐
│ Property Listing │
│ (photos, desc,   │
│  price, location)│
└────────┬─────────┘
         ↓
┌──────────────────────────┐
│ Extract Amenities        │
│ (OpenAI from photos +    │
│  text description)       │
│ - Pool, garden, terrace  │
│ - Parking, security      │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Geo-indexing             │
│ - Latitude/Longitude     │
│ - Nearby attractions     │
│ - Transit score          │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embeddings          │
│ Property description     │
│ "Modern 3BR with pool"   │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Indexing          │
│ Full-text + geo          │
│ + semantic vectors       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ User Search Query        │
│ "Quiet cottage,          │
│  near nature, <200K"     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Hybrid Search            │
│ 1. Geo proximity         │
│ 2. Price range filter    │
│ 3. Semantic similarity   │
│ 4. Amenity matching      │
│ 5. Rank by relevance     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Return Ranked Results    │
│ 50 properties with       │
│ scores & explanations    │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Embeddings:** ONNX for property descriptions
- **Geo-search:** Lucene geo-spatial indexing
- **Search:** Combined price/location/semantic ranking
- **Storage:** H2 with geospatial extensions
- **Features:**
  - Search 1M+ listings in <200ms
  - Natural language queries
  - Photo-based amenity detection
  - Saved searches + alerts

### 💾 Database Schema
```sql
CREATE TABLE properties (
    id UUID PRIMARY KEY,
    listing_id VARCHAR UNIQUE,
    title VARCHAR,
    description CLOB,
    price DECIMAL,
    bedrooms INT,
    bathrooms DECIMAL,
    sqft INT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(10, 8),
    amenities JSON,
    embedding BLOB,  -- ONNX vector
    created_at TIMESTAMP,
    INDEX lucene_description (description),
    INDEX geo_index (latitude, longitude)
);

CREATE TABLE saved_searches (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    max_price DECIMAL,
    min_price DECIMAL,
    geo_radius_km INT,
    filters JSON,
    alert_enabled BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE search_views (
    id UUID PRIMARY KEY,
    user_id UUID,
    property_id UUID,
    search_id UUID,
    rank INT,
    view_time TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Search Latency:** <200ms for 1M listings
- **Relevance:** >80% relevant results in top 10
- **Conversion:** +25% save-to-contact rate
- **Cost:** Minimal (~ONNX only, local)

### 📱 UI/UX
- Google Maps integration with listings
- Advanced filters (price, beds, walk score)
- Photo gallery with amenity tags
- Natural language query builder

---

## Use Case #6: Medical Records Analyzer

### 🎯 Problem Statement
Hospitals have patient records in multiple formats (notes, lab results, imaging reports). Clinicians need to find relevant history instantly while maintaining HIPAA compliance. Complex entity extraction + security.

### 📊 Solution Architecture

```
┌──────────────────┐
│ Incoming Medical │
│ Document (note,  │
│ lab, imaging)    │
└────────┬─────────┘
         ↓
┌──────────────────────────┐
│ PHI/PII Detection        │  (HIPAA 18 identifiers)
│ - Patient name, DOB      │
│ - Medical record #       │
│ - Account/Social number  │
│ - Facility name          │
│ - Redact metadata        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Entity Extraction        │  (OpenAI + domain)
│ - Diagnoses (ICD-10)     │
│ - Medications (RxNorm)   │
│ - Procedures             │
│ - Labs (abnormal values) │
│ - Allergies              │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Clinical NLP             │
│ - Sentiment/severity     │
│ - Temporal markers       │
│   (onset, duration)      │
│ - Relationships          │
│   (cause-effect)         │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embeddings          │
│ Clinical concept vectors │
│ (diagnosis + meds)       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Indexing          │
│ - Full-text with stemming│
│ - Code lookups (ICD-10)  │
│ - Semantic vectors       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Clinical Search          │
│ "Find all diabetes       │
│  management notes"       │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Hybrid Retrieval         │
│ 1. Diagnosis codes       │
│ 2. Semantic similarity   │
│ 3. Temporal ordering     │
│ 4. Return 20 documents   │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Audit Logging            │  (HIPAA required)
│ - Who accessed           │
│ - When, what records     │
│ - Purpose code           │
│ - Immutable trail        │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **PHI Detection:** OpenAI + regex for 18 HIPAA identifiers
- **Entity Extraction:** OpenAI with clinical domain knowledge
- **NLP:** Custom rules for clinical text patterns
- **Embeddings:** Clinical ONNX models (SciBERT or ClinicalBERT)
- **Security:** Row-level encryption, audit logging
- **Features:**
  - Search 10M+ records in <500ms
  - Drug interaction checks
  - Clinical decision support suggestions
  - Longitudinal patient timeline

### 💾 Database Schema
```sql
CREATE TABLE patient_records (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,  -- Encrypted
    document_type VARCHAR,  -- 'note', 'lab', 'imaging'
    original_text CLOB,
    redacted_text CLOB,  -- PHI removed
    extracted_entities JSON,  -- diagnoses, meds, etc.
    embedding BLOB,  -- ONNX vector
    document_date DATE,
    created_at TIMESTAMP,
    accessed_at TIMESTAMP
);

CREATE TABLE phi_redaction_log (
    id UUID PRIMARY KEY,
    record_id UUID,
    phi_type VARCHAR,  -- HIPAA identifier type (18 types)
    redacted_hash VARCHAR,
    user_id UUID,
    timestamp TIMESTAMP,
    CONSTRAINT audit_trail (user_id IS NOT NULL)
);

CREATE TABLE clinical_entities (
    id UUID PRIMARY KEY,
    record_id UUID,
    entity_type VARCHAR,  -- 'diagnosis', 'medication', 'procedure'
    code VARCHAR,  -- ICD-10, RxNorm, CPT
    canonical_name VARCHAR,
    confidence DECIMAL,
    extracted_at TIMESTAMP
);

CREATE TABLE access_audit (
    id UUID PRIMARY KEY,
    user_id UUID,
    patient_id UUID,
    action VARCHAR,  -- 'view', 'search'
    record_ids ARRAY,
    purpose_code VARCHAR,
    timestamp TIMESTAMP,
    ip_address VARCHAR,
    CONSTRAINT immutable (timestamp IS NOT NULL)
);
```

### 🎯 KPIs & Metrics
- **Accuracy:** >94% entity extraction (diagnoses)
- **Latency:** <500ms for patient record search
- **Coverage:** 10M+ records indexed
- **Compliance:** 100% HIPAA audit trail
- **Cost:** ~$0.01 per record + inference

### 📱 UI/UX
- Clinician search interface (specialty-focused)
- Patient timeline (chronological events)
- Drug interaction warnings
- Audit trail transparency

---

## Use Case #7: Content Moderation Engine

### 🎯 Problem Statement
Social platforms receive 100K+ user-generated content pieces daily (posts, comments, videos). Manual moderation is impossible. Need AI-powered classification with policy enforcement and audit trails.

### 📊 Solution Architecture

```
┌───────────────────┐
│ User-Generated    │
│ Content (text,    │
│ images, links)    │
└────────┬──────────┘
         ↓
┌──────────────────────────┐
│ Content Extraction       │
│ - OCR for images         │
│ - URL preview text       │
│ - Video transcription    │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Multi-Policy Check       │  (Customer hooks)
│ 1. Spam detection        │
│ 2. Hate speech           │
│ 3. Violence/gore         │
│ 4. Sexual content        │
│ 5. Self-harm             │
│ 6. Harassment/bullying   │
│ 7. Copyright             │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Classification (OpenAI)  │
│ Category: POLICY_NAME    │
│ Confidence: 0.0-1.0      │
│ Reasoning: brief text    │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Action Decision          │
│ If confidence > 0.9:     │
│   Auto-remove            │
│ If 0.5-0.9:              │
│   Queue for review       │
│ If < 0.5:                │
│   Approve (allow)        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Behavior Module          │  (Track moderation events)
│ Event: 'content_action'  │
│ Action: approved/removed │
│ Policy: which violation  │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ H2 Storage               │
│ Content + classification │
│ + moderator notes        │
│ + appeals                │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Appeal & Feedback        │
│ User can contest removal │
│ System learns false pos  │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Dashboard Metrics        │
│ - False positive rate    │
│ - Appeal rate            │
│ - Policy distribution    │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Content Analysis:** OpenAI with custom policy prompts
- **Policy Enforcement:** Hook-based (EntityAccessPolicy pattern)
- **Behavior Tracking:** Ingestion module for moderation events
- **Appeal System:** H2 storage with workflow status
- **Features:**
  - Auto-moderate 100K+ items/day
  - <100ms per decision
  - Appeal workflow with manual review
  - Fairness metrics (policy bias detection)

### 💾 Database Schema
```sql
CREATE TABLE user_content (
    id UUID PRIMARY KEY,
    user_id UUID,
    content_type VARCHAR,  -- 'text', 'image', 'video', 'link'
    raw_content CLOB,
    extracted_text VARCHAR,  -- from OCR/transcription
    moderation_status VARCHAR,  -- 'approved', 'removed', 'pending'
    classifier_decision VARCHAR,
    confidence DECIMAL,
    reasoning TEXT,
    moderator_notes TEXT,
    moderator_id UUID,
    created_at TIMESTAMP,
    moderated_at TIMESTAMP
);

CREATE TABLE moderation_policies (
    id UUID PRIMARY KEY,
    policy_name VARCHAR,  -- 'spam', 'hate_speech', etc.
    description TEXT,
    openai_prompt TEXT,
    enabled BOOLEAN,
    confidence_threshold DECIMAL,
    auto_action VARCHAR,  -- 'approve', 'remove', 'pending'
    created_at TIMESTAMP
);

CREATE TABLE content_appeals (
    id UUID PRIMARY KEY,
    content_id UUID,
    user_id UUID,
    appeal_reason TEXT,
    appeal_status VARCHAR,  -- 'pending', 'approved', 'rejected'
    reviewer_id UUID,
    review_notes TEXT,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE moderation_audit (
    id UUID PRIMARY KEY,
    content_id UUID,
    action VARCHAR,  -- 'approved', 'removed'
    policy_violated VARCHAR,
    actor_id UUID,  -- moderator or 'system'
    confidence DECIMAL,
    timestamp TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Throughput:** 100K+ items/day
- **Accuracy:** >91% policy match (verified by manual review)
- **Latency:** <100ms per decision
- **Appeal Rate:** <5% of removed content
- **Cost:** ~$0.001 per moderation call

### 📱 UI/UX
- Moderator queue (flagged items)
- Bulk actions (approve/remove multiple)
- Appeal request form for users
- Appeal review dashboard

---

## Use Case #8: Job Posting Matcher

### 🎯 Problem Statement
Job platforms have 1M+ active postings and 10M+ resumes. Traditional keyword matching misses qualified candidates (resume says "full-stack" but job posts want "backend" + "frontend"). Need semantic skills matching.

### 📊 Solution Architecture

```
┌─────────────────────────┐
│ Job Posting             │
│ (title, skills, salary) │
└────────┬────────────────┘
         ↓
┌──────────────────────────┐
│ Parse Job Requirements   │  (OpenAI)
│ - Core skills            │
│ - Desired skills         │
│ - Years required         │
│ - Nice-to-haves          │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ ONNX Embedding           │
│ Job posting vector       │
│ (skills + description)   │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Resume Database          │
│ (10M+ indexed resumes    │
│  with pre-computed       │
│  embeddings)             │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Semantic Search          │
│ 1. Find similar resumes  │
│    (ONNX vectors)        │
│ 2. Filter by salary exp  │
│ 3. Rank by skill match   │
│ 4. Return top 100        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Detailed Matching        │
│ For each resume:         │
│ - Exact skill matches    │
│ - Similar skill matches  │
│ - Experience gap         │
│ - Salary fit             │
│ - Calculate score        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Candidate Ranking        │
│ Score = semantic match   │
│         + exact matches  │
│         - experience gap │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Return Top 50            │
│ Candidates with          │
│ reasoning for recruiter  │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Embeddings:** ONNX (skills taxonomy embedding)
- **Search:** Lucene for 10M+ resumes (sharded)
- **Ranking:** Multi-factor scoring
- **Storage:** H2 with resume pool
- **Features:**
  - Match 1000 job postings/day
  - <500ms per posting match
  - Portable skills taxonomy (JavaScript → TypeScript)
  - Fairness filtering (diverse candidates)

### 💾 Database Schema
```sql
CREATE TABLE job_postings (
    id UUID PRIMARY KEY,
    posting_id VARCHAR UNIQUE,
    title VARCHAR,
    description CLOB,
    salary_min DECIMAL,
    salary_max DECIMAL,
    location VARCHAR,
    required_skills ARRAY,
    nice_to_have_skills ARRAY,
    years_required INT,
    embedding BLOB,  -- ONNX vector
    created_at TIMESTAMP
);

CREATE TABLE resume_pool (
    id UUID PRIMARY KEY,
    resume_id UUID,
    candidate_id UUID,
    skills ARRAY,
    years_experience INT,
    salary_expectation DECIMAL,
    embedding BLOB,  -- ONNX vector (pre-computed)
    indexed_at TIMESTAMP
);

CREATE TABLE job_matches (
    id UUID PRIMARY KEY,
    job_id UUID,
    candidate_id UUID,
    match_score DECIMAL,
    exact_skill_matches INT,
    similar_skill_matches INT,
    experience_gap INT,
    salary_fit VARCHAR,  -- 'perfect', 'high', 'acceptable', 'low'
    created_at TIMESTAMP
);
```

### 🎯 KPIs & Metrics
- **Match Quality:** >85% relevant candidates in top 50
- **Throughput:** 1000 postings/day
- **Latency:** <500ms per job matching
- **Conversion:** +40% apply rate vs keyword matching
- **Cost:** Minimal (~ONNX only)

### 📱 UI/UX
- Recruiter: "Find candidates for this job" button
- Candidate: "Find matching jobs" search
- Skill match display with explanations
- Exportable match reports

---

## Use Case #9: Document Summarization Engine

### 🎯 Problem Statement
Publishers, researchers, and analysts need to digest 1000s of articles/reports daily. Manual reading impossible. Need automatic summarization with key points extraction.

### 📊 Solution Architecture

```
┌──────────────────┐
│ Source Document  │
│ (article, report,│
│  paper, manual)  │
└────────┬─────────┘
         ↓
┌──────────────────────────┐
│ Text Extraction          │
│ - Strip formatting       │
│ - Remove boilerplate     │
│ - Preserve structure     │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Intelligent Chunking     │
│ - Respect paragraphs     │
│ - Section-aware          │
│ - <512 token chunks      │
│ - With overlap           │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Key Points Extraction    │  (OpenAI)
│ - Main findings          │
│ - Critical data          │
│ - Recommendations        │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Multi-Level Summary      │  (OpenAI)
│ 1. 1-sentence summary    │
│ 2. 3-point executive     │
│ 3. 200-word summary      │
│ 4. Full section-by-sect. │
│ (User chooses detail)    │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Entity Extraction        │  (Optional)
│ - Named entities         │
│ - Data points/numbers    │
│ - Key citations          │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Lucene Indexing          │
│ - Original full text     │
│ - Summary text           │
│ - Entities for search    │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ H2 Storage               │
│ - Original doc           │
│ - Summary levels         │
│ - Key points             │
│ - Metadata               │
└────────┬─────────────────┘
         ↓
┌──────────────────────────┐
│ Return:                  │
│ - Multi-level summaries  │
│ - Key points             │
│ - Entity highlights      │
│ - Readability estimate   │
└──────────────────────────┘
```

### 🛠️ Tech Implementation
- **Extraction:** Apache Tika or custom parsers
- **Chunking:** Structure-aware (sections, paragraphs)
- **Summarization:** OpenAI GPT-4 with prompting
- **Indexing:** Lucene for full-text + entities
- **Storage:** H2 with multiple summary levels
- **Features:**
  - Summarize 1000+ documents/day
  - Multi-level summaries (1 sentence to full)
  - Key entities highlighted
  - Readability scoring

### 💾 Database Schema
```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    source_url VARCHAR,
    title VARCHAR,
    original_text CLOB,
    document_type VARCHAR,  -- 'article', 'report', 'paper'
    created_at TIMESTAMP
);

CREATE TABLE document_summaries (
    id UUID PRIMARY KEY,
    document_id UUID,
    summary_level VARCHAR,  -- 'one_liner', 'executive', 'medium', 'full'
    summary_text VARCHAR,
    generated_at TIMESTAMP,
    confidence DECIMAL
);

CREATE TABLE key_points (
    id UUID PRIMARY KEY,
    document_id UUID,
    point_text VARCHAR,
    importance INT,  -- 1-10
    entity_references ARRAY,
    extracted_at TIMESTAMP
);

CREATE TABLE document_entities (
    id UUID PRIMARY KEY,
    document_id UUID,
    entity_text VARCHAR,
    entity_type VARCHAR,  -- 'person', 'organization', 'number', 'date'
    context VARCHAR,
    position INT
);
```

### 🎯 KPIs & Metrics
- **Throughput:** 1000+ docs/day
- **Latency:** 5-30 seconds per document (depends on length)
- **Relevance:** >85% for key points
- **Compression:** 10-20x shorter summaries
- **Cost:** ~$0.01 per document (OpenAI)

### 📱 UI/UX
- Reader: choose summary detail level (slider)
- Publisher dashboard: batch upload summarization
- Key points displayed prominently
- Entity highlighting with click-to-learn

---

## Use Case #10: Behavior Analytics Dashboard

### 🎯 Problem Statement
Every app needs visibility into user behavior (what features are used, where do users drop off, what drives conversions). Need real-time event ingestion + analytics engine with low latency.

### 📊 Solution Architecture

```
┌────────────────────────┐
│ User Events            │
│ (10K events/sec)       │
│ - Views, clicks        │
│ - Form submissions     │
│ - Purchases            │
│ - Errors               │
└────────┬───────────────┘
         ↓
┌────────────────────────────────┐
│ Behavior Module Ingestion      │
│ - Event batching               │
│ - Schema validation            │
│ - Real-time stream             │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│ Streaming Processors           │
│ - Window aggregations (1min)   │
│ - User sessions                │
│ - Funnel tracking              │
│ - Anomaly detection            │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│ H2 Time-Series Storage         │
│ - Raw events (searchable)      │
│ - Aggregated metrics           │
│ - User segments                │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│ Query Engine                   │
│ 1. Real-time KPI queries       │
│ 2. Cohort analysis             │
│ 3. Funnel visualization        │
│ 4. Retention curves            │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│ AI Insights (Optional OpenAI)  │
│ - Anomaly explanation          │
│ - Trend recommendation         │
│ - Action suggestions           │
└────────┬───────────────────────┘
         ↓
┌────────────────────────────────┐
│ Real-Time Dashboard            │
│ - Live metrics (1min refresh)  │
│ - Funnels                      │
│ - Heatmaps                     │
│ - Alerts                       │
└──────────────────────────────────┘
```

### 🛠️ Tech Implementation
- **Event Ingestion:** Behavior Module HTTP API
- **Stream Processing:** Local batch aggregation
- **Storage:** H2 time-series tables with indexes
- **Query:** Direct SQL + view materializations
- **Features:**
  - Ingest 100K+ events/second per server
  - <500ms query latency for dashboards
  - Real-time alerts on anomalies
  - User cohort segmentation
  - Funnel analysis with conversion rates

### 💾 Database Schema
```sql
CREATE TABLE behavior_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR,
    event_data JSON,
    session_id VARCHAR,
    timestamp TIMESTAMP,
    INDEX events_by_time (timestamp),
    INDEX events_by_user (user_id)
);

CREATE TABLE event_aggregates (
    id UUID PRIMARY KEY,
    event_type VARCHAR,
    time_window TIMESTAMP,  -- 1-min aggregate
    count INT,
    user_count INT,
    attributes JSON,
    created_at TIMESTAMP,
    INDEX agg_by_time (time_window)
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID,
    session_id VARCHAR UNIQUE,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    events_count INT,
    duration_seconds INT,
    conversion BOOLEAN
);

CREATE TABLE funnels (
    id UUID PRIMARY KEY,
    funnel_name VARCHAR,
    step_order INT,
    event_type VARCHAR,
    filter_json JSON,
    created_at TIMESTAMP
);

CREATE TABLE funnel_results (
    id UUID PRIMARY KEY,
    funnel_id UUID,
    step INT,
    users_at_step INT,
    conversion_rate DECIMAL,
    time_window TIMESTAMP
);

CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    alert_name VARCHAR,
    metric_name VARCHAR,
    threshold DECIMAL,
    comparison VARCHAR,  -- 'gt', 'lt', 'change_pct'
    triggered_at TIMESTAMP,
    metric_value DECIMAL,
    severity VARCHAR
);
```

### 🎯 KPIs & Metrics
- **Throughput:** 100K events/second
- **Ingestion Latency:** <100ms (p99)
- **Query Latency:** <500ms for most dashboards
- **Storage:** ~500GB per month (1 event = 1KB avg)
- **Cost:** Minimal (local processing only)

### 📱 UI/UX
- Real-time KPI cards (auto-refresh)
- Funnel visualization with drop-off rates
- Cohort comparison (User A vs User B)
- Event timeline for debugging
- Custom chart builder (SQL or UI wizard)

---

## Implementation Roadmap

### Phase 1: Foundation (Weeks 1-2)
- [ ] Setup shared infrastructure
  - H2 database templates
  - Lucene index configuration
  - ONNX model loading
  - OpenAI API integration
- [ ] Create base entity/repository structure
- [ ] Build shared utility libraries

### Phase 2: Core Use Cases (Weeks 3-10)
- **Week 3-4:** Smart Resume Analyzer
- **Week 5-6:** E-Commerce Recommender
- **Week 7-8:** Legal Document Search
- **Week 9-10:** Support Ticket Classifier

### Phase 3: Specialized Cases (Weeks 11-18)
- **Week 11-12:** Real Estate Finder
- **Week 13-14:** Medical Records Analyzer
- **Week 15-16:** Content Moderation
- **Week 17-18:** Job Posting Matcher

### Phase 4: Advanced Cases (Weeks 19-22)
- **Week 19:** Document Summarization
- **Week 20-22:** Behavior Analytics Dashboard

### Testing Strategy
Each use case includes:
- Unit tests (repository, service layers)
- Integration tests (end-to-end with H2)
- Performance tests (throughput, latency)
- Functional tests (UI automation)

---

## Deployment & Testing Strategy

### Local Development
```bash
# Start all services
docker-compose -f docker-compose.dev.yml up

# Run use case app
cd use-cases/resume-analyzer
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Access
# Frontend: http://localhost:3000/resume-analyzer
# API: http://localhost:8080/api/resume-analyzer
# Docs: http://localhost:8080/swagger-ui.html
```

### Testing Each Use Case

**Performance Benchmarks:**
```
Resume Analyzer:
  - Ingest: 1000 resumes in 5 minutes
  - Match: 50 resumes vs 1 JD in <500ms
  - Accuracy: >90% skill detection

Product Recommender:
  - Index: 100K products in 10 minutes
  - Recommend: <100ms for 10 items
  - CTR: +35% vs baseline

Legal Search:
  - Index: 1M pages in 2 hours
  - Search: <1s for complex queries
  - Relevance: >85% top-5 matching
```

### Production Deployment
Each use case is a standalone Spring Boot app:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Deploy to Kubernetes/Cloud Run with:
- Auto-scaling based on throughput
- Health checks (liveness/readiness)
- Metric collection (Prometheus)
- Log aggregation (ELK stack)

---

## 🎓 Learning Outcomes

Building these 10 apps demonstrates:

**AI Infrastructure Mastery:**
- ✅ Entity-level AI capabilities (@AICapable)
- ✅ Multi-mode embeddings (ONNX + OpenAI)
- ✅ Hybrid search (semantic + full-text)
- ✅ RAG patterns (retrieval + generation)
- ✅ PII detection and privacy controls
- ✅ Behavior module for event analytics

**Real-World Patterns:**
- ✅ Entity extraction and classification
- ✅ Semantic similarity and ranking
- ✅ Multi-factor scoring algorithms
- ✅ Audit logging and compliance
- ✅ Streaming event processing
- ✅ Real-time dashboard design

**Production Best Practices:**
- ✅ Database indexing strategies
- ✅ Caching and performance tuning
- ✅ Error handling and resilience
- ✅ Testing (unit, integration, performance)
- ✅ Security and data privacy
- ✅ Monitoring and alerting

---

## 📊 Metrics Summary

| Metric | Target | Notes |
|--------|--------|-------|
| **Total Effort** | 22 weeks | 1-3 weeks per app |
| **Total Code** | 30K-50K LOC | Across 10 apps + shared libs |
| **Test Coverage** | >80% | Unit + integration |
| **Documentation** | 100% | Each app has README + guides |
| **Performance** | <1s latency | p99 for most queries |
| **Throughput** | 1M+ ops/day | Aggregate across all apps |
| **Cost/Month** | $500-1500 | Mostly OpenAI API |

---

## 🎯 Success Criteria

Each use case app is considered **production-ready** when:

1. ✅ **Functional:** All core features working with <1% error rate
2. ✅ **Performant:** Meets latency targets (see individual specs)
3. ✅ **Tested:** >80% code coverage with automated tests
4. ✅ **Secure:** PII/PHI properly handled, audit logging
5. ✅ **Documented:** README, API docs, deployment guide
6. ✅ **Scalable:** Can handle 2x load without degradation
7. ✅ **Monitored:** Metrics, logs, alerts configured

---

## 🚀 Getting Started

To build any of these apps:

1. **Choose a use case** from the table above
2. **Read the architecture section** for your app
3. **Setup shared infrastructure** (H2, Lucene, ONNX)
4. **Implement entities and repositories**
5. **Build AI services** (embedding, search, generation)
6. **Create REST API controllers**
7. **Build React frontend** with dashboard
8. **Write comprehensive tests**
9. **Deploy and monitor**

Each use case has a dedicated branch with starting template code and detailed implementation guides.

---

**Document Status:** Ready for implementation  
**Last Updated:** November 2025  
**Maintainer:** AI Infrastructure Team





