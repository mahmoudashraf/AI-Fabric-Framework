# Use Case Examples

The module ships with integration scenarios that mirror real customer workflows. Each example below corresponds to an executable test under `src/test/java/com/ai/infrastructure/relationship/usecases`.

## 1. Law Firm Document Search (`LawFirmDocumentSearchTest`)
- **User query:** “Find all contracts related to John Smith in Q4 2023.”
- **Entities:** `DocumentEntity`, `UserEntity`
- **Filters:** `status=ACTIVE`, `title ILIKE %Q4 2023%`
- **Relationship path:** `document.author -> user.fullName ILIKE %John Smith%`
- **Notes:** Demonstrates single-hop traversal plus direct filters. Logs user query, plan, JPQL, and result metadata.

## 2. E-commerce Product Discovery (`ECommerceProductDiscoveryTest`)
- **User query:** “Show me blue shoes under $100 from Nike.”
- **Entities:** `ProductEntity`, `BrandEntity`
- **Filters:** `color ILIKE blue`, `price <= 100`
- **Relationship path:** `product.brand -> brand.name ILIKE %Nike%`
- **Notes:** Highlights multi-filter combination and how pricing conditions are translated into JPQL parameters.

## 3. Medical Case Finder (`MedicalCaseFinderTest`)
- **User query:** “Find active oncology cases for Alice Carter that require immunotherapy.”
- **Entities:** `MedicalCaseEntity`, `PatientEntity`
- **Filters:** `specialty ILIKE oncology`, `therapyPlan ILIKE %immunotherapy%`, `status=ACTIVE`
- **Relationship path:** `medical-case.patient -> patient.fullName ILIKE %Alice Carter%`
- **Notes:** Demonstrates textual filters across both the primary entity and the related patient record.

## 4. HR Candidate Search (`HRCandidateSearchTest`)
- **User query:** “Show senior machine learning engineer candidates in New York managed by Dana Liu.”
- **Entities:** `CandidateEntity`, `RecruiterEntity`
- **Filters:** Location, seniority, primary skill
- **Relationship path:** `candidate.recruiter -> recruiter.fullName ILIKE %Dana Liu%`
- **Notes:** Asserts results using metadata (recruiter name) to show the difference between content and metadata assertions.

## 5. Financial Fraud Detection (`FinancialFraudDetectionTest`)
- **User query:** “List suspicious transactions over $25k from high-risk regions routed through the same counterparty.”
- **Entities:** `TransactionEntity`, `AccountEntity`
- **Filters:** Amount, status, channel
- **Relationship paths:** 
  - `transaction.destinationAccount -> account.region ILIKE %high-risk%`
  - `transaction.sourceAccount -> account.riskScore >= 0.7`
- **Notes:** Two joins to the same entity type with different semantics; uses temporary logical names to avoid alias collisions and indexes metadata for fallback.

## How to Run
```bash
mvn -pl ai-infrastructure-relationship-query -Dtest=LawFirmDocumentSearchTest,ECommerceProductDiscoveryTest,MedicalCaseFinderTest,HRCandidateSearchTest,FinancialFraudDetectionTest test
```

Each test seeds an in-memory H2 database, populates Lucene/ONNX, and logs the user query, planner plan, JPQL, and resulting `RAGResponse` documents. Use these as templates for domain-specific onboarding or demos.
