# 📋 Business Requirements Document (BRD)
# Subscription Management Hub - AI-Powered SaaS Platform

**Version:** 1.0  
**Date:** January 2026  
**Framework:** AI Fabric Framework  
**Status:** Ready for Implementation

---

## 📑 Table of Contents

1. [Executive Summary](#executive-summary)
2. [Business Objectives](#business-objectives)
3. [AI Fabric Framework Integration](#ai-fabric-framework-integration)
4. [Functional Requirements](#functional-requirements)
5. [User Stories & Natural Language Interactions](#user-stories--natural-language-interactions)
6. [User Interface Requirements](#user-interface-requirements)
7. [Technical Architecture](#technical-architecture)
8. [Data Models](#data-models)
9. [API Endpoints](#api-endpoints)
10. [Intent Action Handling](#intent-action-handling)
11. [Implementation Roadmap](#implementation-roadmap)

---

## 1. Executive Summary

### 🎯 **Recommendation: This is the BEST use case for demonstrating AI Fabric Framework capabilities**

The **Subscription Management Hub** is a comprehensive SaaS subscription management platform that leverages AI Fabric Framework to provide:

- **Natural Language Interface** - Users interact via plain English queries
- **Intent Action Handling** - Framework routes actions (subscribe, cancel, upgrade) to business logic
- **Semantic Search** - Find subscription plans using natural language ("plans under $50/month")
- **Behavior Analytics** - Track user actions and predict churn risk
- **Smart Recommendations** - AI-powered upgrade suggestions based on usage patterns
- **PII Detection** - Secure handling of billing addresses and payment information

### Business Value

- **20-35% reduction in churn** through predictive analytics
- **15-25% increase in upgrades** via intelligent recommendations
- **40-50% reduction in support tickets** through natural language interface
- **Improved user experience** with conversational interactions

---

## 2. Business Objectives

### Primary Goals

1. **Reduce Churn** - Identify at-risk subscribers and take proactive measures
2. **Increase Revenue** - Drive upgrades through intelligent recommendations
3. **Improve UX** - Enable natural language interactions instead of complex forms
4. **Automate Operations** - Reduce manual intervention in subscription management
5. **Data-Driven Insights** - Provide actionable analytics on subscription health

### Success Metrics

- Churn rate reduction: 20-35%
- Upgrade conversion rate: 15-25% increase
- Support ticket reduction: 40-50%
- User satisfaction score: >4.5/5.0
- Average time to complete subscription actions: <30 seconds

---

## 3. AI Fabric Framework Integration

### 🎯 **Framework Capabilities Used**

#### ✅ **1. Intent Extraction & Action Handling** (Primary Feature)
- **What it does:** Converts natural language to actionable intents
- **Framework Service:** `IntentQueryExtractor`, `ActionHandlerRegistry`
- **User Implementation:** Custom `ActionHandler` implementations
- **Example:** "Cancel my subscription" → ACTION intent → `CancelSubscriptionActionHandler`

#### ✅ **2. Semantic Search** (Core Feature)
- **What it does:** Understands meaning, not just keywords
- **Framework Service:** `AISearchService`
- **Use Cases:**
  - Search plans: "plans under $50 per month"
  - Find subscriptions: "my active subscriptions"
  - Discover features: "plans with unlimited storage"

#### ✅ **3. RAG (Retrieval-Augmented Generation)** (Core Feature)
- **What it does:** Provides context-aware answers to questions
- **Framework Service:** `RAGProvider`
- **Use Cases:**
  - "What's included in the Pro plan?"
  - "When does my subscription renew?"
  - "How do I upgrade?"

#### ✅ **4. Behavior Analytics** (Advanced Feature)
- **What it does:** Tracks user behavior and predicts churn
- **Framework Service:** `BehaviorAnalysisService`
- **Use Cases:**
  - Track subscription events (subscribe, cancel, upgrade)
  - Calculate churn risk scores
  - Identify engagement trends
  - Generate personalized recommendations

#### ✅ **5. PII Detection** (Security Feature)
- **What it does:** Detects and protects sensitive information
- **Framework Service:** `PIIDetectionService`
- **Use Cases:**
  - Validate billing addresses
  - Protect payment information
  - Audit sensitive data access

#### ✅ **6. Vector Indexing** (Automatic)
- **What it does:** Automatically indexes subscription plans for search
- **Framework Annotation:** `@AICapable`, `@AIProcess`
- **Automatic Features:**
  - Plan descriptions indexed for semantic search
  - Subscription metadata indexed for queries
  - Real-time synchronization with database

#### ✅ **7. Natural Language Queries** (Advanced Feature)
- **What it does:** Converts questions to database queries
- **Framework Service:** `ReliableRelationshipQueryService`
- **Use Cases:**
  - "Show me all subscriptions expiring this month"
  - "Find users who upgraded in the last 30 days"
  - "List plans with more than 100 active subscribers"

---

## 4. Functional Requirements

### 4.1 Subscription Plan Management

#### FR-1.1: Plan Discovery via Natural Language
- **Requirement:** Users can search for plans using natural language
- **Framework Integration:** `AISearchService` with semantic search
- **Examples:**
  - "Show me plans under $50"
  - "Find enterprise plans with unlimited users"
  - "What plans include priority support?"

#### FR-1.2: Plan Recommendations
- **Requirement:** System suggests optimal plans based on usage
- **Framework Integration:** `AICoreService.generateRecommendations()`
- **Logic:** Analyzes user behavior patterns via `BehaviorAnalysisService`

### 4.2 Subscription Actions (Intent Action Handling)

#### FR-2.1: Subscribe to Plan
- **Action:** `subscribe`
- **Natural Language:** "I want to subscribe to the Pro plan"
- **Framework:** Intent extraction → `SubscribeActionHandler`
- **Confirmation Required:** Yes (for paid plans)

#### FR-2.2: Cancel Subscription
- **Action:** `cancel_subscription`
- **Natural Language:** "Cancel my subscription" or "I want to unsubscribe"
- **Framework:** Intent extraction → `CancelSubscriptionActionHandler`
- **Confirmation Required:** Yes (critical action)

#### FR-2.3: Upgrade Plan
- **Action:** `upgrade_subscription`
- **Natural Language:** "Upgrade me to Enterprise" or "Move me to the Pro plan"
- **Framework:** Intent extraction → `UpgradeSubscriptionActionHandler`
- **Confirmation Required:** Yes (billing change)

#### FR-2.4: Downgrade Plan
- **Action:** `downgrade_subscription`
- **Natural Language:** "Downgrade to Basic plan" or "Switch to the cheaper plan"
- **Framework:** Intent extraction → `DowngradeSubscriptionActionHandler`
- **Confirmation Required:** Yes (feature loss warning)

#### FR-2.5: Update Address
- **Action:** `update_address`
- **Natural Language:** "Change my billing address to 123 Main St, New York"
- **Framework:** Intent extraction → `UpdateAddressActionHandler`
- **PII Detection:** `PIIDetectionService` validates address format
- **Confirmation Required:** No (low-risk action)

### 4.3 Churn Prediction & Prevention

#### FR-3.1: Churn Risk Scoring
- **Requirement:** Calculate churn risk for each subscriber
- **Framework Integration:** `BehaviorAnalysisService.analyzeUser()`
- **Output:** Risk score (0.0-1.0) with reasoning

#### FR-3.2: At-Risk Subscriber Alerts
- **Requirement:** Identify subscribers with high churn risk
- **Framework Integration:** `BehaviorAnalysisService` trend detection
- **Action:** Proactive outreach or retention offers

### 4.4 Natural Language Q&A

#### FR-4.1: Subscription Information Queries
- **Requirement:** Answer questions about subscriptions
- **Framework Integration:** `RAGProvider` with subscription context
- **Examples:**
  - "When does my subscription renew?"
  - "What features are included in my current plan?"
  - "How much will I be charged next month?"

---

## 5. User Stories & Natural Language Interactions

### 🎯 **User Story 1: Natural Language Subscription Cancellation**

**As a** subscriber  
**I want to** cancel my subscription using natural language  
**So that** I don't have to navigate complex forms

**Natural Language Examples:**
- "I want to cancel my subscription"
- "Cancel my Pro plan subscription"
- "Unsubscribe me from the service"
- "I need to cancel, it's too expensive"

**Framework Flow:**
```
User Query: "Cancel my subscription"
    ↓
Intent Extraction (Framework)
    ↓
Intent Type: ACTION
Action: cancel_subscription
    ↓
ActionHandlerRegistry.findHandler("cancel_subscription")
    ↓
CancelSubscriptionActionHandler
    ├─ validateActionAllowed(userId) ✅
    ├─ getConfirmationMessage() → "Are you sure you want to cancel?"
    ├─ User confirms
    └─ executeAction() → Subscription cancelled
    ↓
BehaviorAnalysisService.trackEvent("UNSUBSCRIBE")
    ↓
Response: "Your subscription has been cancelled successfully"
```

### 🎯 **User Story 2: Semantic Plan Search**

**As a** potential customer  
**I want to** find subscription plans using natural language  
**So that** I can discover the right plan for my needs

**Natural Language Examples:**
- "Show me plans under $50 per month"
- "Find plans with unlimited storage"
- "What's the cheapest plan?"
- "Enterprise plans with priority support"

**Framework Flow:**
```
User Query: "plans under $50 per month"
    ↓
Intent Extraction (Framework)
    ↓
Intent Type: INFORMATION
    ↓
AISearchService.performSearch()
    ├─ Generate embedding for query
    ├─ Semantic search across subscription-plan entities
    └─ Filter by price metadata (@AIContext)
    ↓
Results: List of plans matching semantic intent + price filter
```

### 🎯 **User Story 3: Intelligent Upgrade Recommendation**

**As a** subscriber  
**I want to** receive personalized upgrade recommendations  
**So that** I can get the most value from the service

**Natural Language Examples:**
- "Should I upgrade my plan?"
- "What plan would be best for me?"
- "Recommend a plan based on my usage"

**Framework Flow:**
```
User Query: "Should I upgrade?"
    ↓
Intent Extraction (Framework)
    ↓
Intent Type: INFORMATION
    ↓
BehaviorAnalysisService.analyzeUser(userId)
    ├─ Analyze usage patterns
    ├─ Calculate feature utilization
    └─ Generate insights
    ↓
AICoreService.generateRecommendations()
    ├─ Use behavior insights as context
    ├─ Semantic search for matching plans
    └─ Generate personalized recommendations
    ↓
Response: "Based on your usage, we recommend upgrading to Pro plan..."
```

### 🎯 **User Story 4: Address Update with Validation**

**As a** subscriber  
**I want to** update my billing address using natural language  
**So that** I can keep my information current

**Natural Language Examples:**
- "Update my billing address to 123 Main Street, New York, NY 10001"
- "Change my address to the new office location"
- "My new billing address is 456 Oak Ave, Los Angeles, CA 90001"

**Framework Flow:**
```
User Query: "Update billing address to 123 Main St, New York"
    ↓
Intent Extraction (Framework)
    ↓
Intent Type: ACTION
Action: update_address
Parameters: {address: "123 Main St, New York", type: "billing"}
    ↓
UpdateAddressActionHandler
    ├─ validateActionAllowed(userId) ✅
    ├─ PIIDetectionService.detectAndProcess(address)
    │   └─ Validate address format
    ├─ AI-powered address validation
    └─ executeAction() → Address updated
    ↓
Response: "Your billing address has been updated successfully"
```

---

## 6. User Interface Requirements

### 6.1 Main Dashboard

#### What Users See:
- **Current Subscription Card**
  - Active subscription plan name and tier
  - Monthly/annual billing amount
  - Next billing date
  - Subscription status (Active, Expiring Soon, Cancelled)
  - Days until renewal or expiration
  - Visual indicator for subscription health (green/yellow/red)

- **Churn Risk Indicator**
  - Risk level badge (Low, Medium, High)
  - Risk score percentage
  - Brief explanation of risk factors
  - Actionable recommendations to reduce risk

- **Quick Actions Panel**
  - "Ask AI Assistant" button (opens chat interface)
  - "View Plans" button
  - "Manage Subscription" button
  - "Update Address" button

#### What Users Can Do:
- Click "Ask AI Assistant" to open natural language chat
- View subscription details by clicking on subscription card
- Navigate to plan management
- Access account settings

#### Testing Requirements:
- Verify subscription information displays correctly
- Confirm churn risk indicator updates based on behavior
- Test all quick action buttons navigate correctly

---

### 6.2 Natural Language Chat Interface

#### What Users See:
- **Chat Window**
  - Message history with user queries and AI responses
  - Text input field at bottom
  - "Send" button
  - Suggested queries or prompts
  - Typing indicator when AI is processing

- **Message Types**
  - User messages (right-aligned, user avatar)
  - AI responses (left-aligned, AI avatar)
  - Confirmation dialogs (centered, highlighted)
  - Action results (success/error messages)

- **Confirmation Dialogs**
  - Action description
  - Confirmation message
  - "Confirm" and "Cancel" buttons
  - Warning icons for high-risk actions

#### What Users Can Do:
- Type natural language queries in plain English
- Ask questions about subscription
- Give commands to perform actions
- Confirm or cancel actions
- View conversation history
- Click suggested queries for quick actions

#### Testing Scenarios:

**Test 1: Information Query**
- User types: "When does my subscription renew?"
- User sees: AI response with renewal date and plan details
- User can: Ask follow-up questions

**Test 2: Action Request**
- User types: "Cancel my subscription"
- User sees: Confirmation dialog with subscription details
- User can: Click "Confirm" or "Cancel"
- After confirmation: Success message with cancellation details

**Test 3: Plan Search**
- User types: "Show me plans under $50"
- User sees: List of matching plans with prices and features
- User can: Click on a plan to view details or subscribe

**Test 4: Address Update**
- User types: "Update my billing address to 123 Main Street, New York"
- User sees: Confirmation showing old and new address
- User can: Confirm or modify the address
- After confirmation: Success message with updated address

**Test 5: Upgrade Request**
- User types: "Upgrade me to the Enterprise plan"
- User sees: Comparison of current vs. new plan, price difference
- User can: Confirm upgrade or cancel
- After confirmation: Success message with new plan details

---

### 6.3 Subscription Plans Discovery Page

#### What Users See:
- **Search Bar**
  - Natural language search input
  - Placeholder text: "Search plans... (e.g., 'plans under $50', 'unlimited storage')"
  - Search button
  - Filter options (Price range, Features, Tier)

- **Plan Cards**
  - Plan name and tier badge
  - Monthly and annual pricing
  - Key features list
  - "View Details" button
  - "Subscribe" button
  - Popular/Recommended badge (if applicable)

- **Recommendations Section**
  - "Recommended for You" heading
  - Personalized plan suggestions
  - Reason for recommendation
  - "Why this plan?" expandable section

#### What Users Can Do:
- Search plans using natural language
- Filter plans by price, features, or tier
- View detailed plan information
- Subscribe to a plan directly
- See personalized recommendations
- Compare plans side-by-side

#### Testing Scenarios:

**Test 1: Natural Language Search**
- User types: "plans with unlimited storage"
- User sees: All plans matching the semantic meaning
- User can: Refine search or view plan details

**Test 2: Price-Based Search**
- User types: "cheapest plan" or "plans under $30"
- User sees: Plans sorted by price, filtered by criteria
- User can: Compare features across plans

**Test 3: Feature Search**
- User types: "plans with priority support"
- User sees: Plans that include priority support feature
- User can: View full feature list for each plan

**Test 4: View Recommendations**
- User clicks: "Recommended for You" section
- User sees: Personalized plan suggestions with explanations
- User can: Click "Why this plan?" to see reasoning

---

### 6.4 Subscription Management Page

#### What Users See:
- **Current Subscription Details**
  - Plan name, tier, and status
  - Billing cycle (Monthly/Annual)
  - Next billing date and amount
  - Subscription start date
  - Auto-renewal status toggle

- **Action Buttons**
  - "Upgrade Plan" button
  - "Downgrade Plan" button
  - "Cancel Subscription" button (highlighted in red)
  - "Update Billing Address" button
  - "Update Payment Method" button

- **Usage Statistics**
  - Features used vs. available
  - Storage usage percentage
  - User count (if applicable)
  - Usage trends graph

- **Churn Risk Panel**
  - Risk level indicator
  - Risk score
  - Factors contributing to risk
  - Recommendations to reduce risk

#### What Users Can Do:
- View complete subscription details
- Upgrade or downgrade subscription
- Cancel subscription
- Update billing information
- View usage statistics
- Review churn risk and recommendations
- Toggle auto-renewal on/off

#### Testing Scenarios:

**Test 1: View Subscription Details**
- User navigates: To subscription management page
- User sees: All subscription information displayed correctly
- User can: Scroll to see all details

**Test 2: Upgrade Subscription**
- User clicks: "Upgrade Plan" button
- User sees: Available upgrade options with comparison
- User can: Select new plan and confirm
- After confirmation: Success message and updated subscription

**Test 3: Cancel Subscription**
- User clicks: "Cancel Subscription" button
- User sees: Confirmation dialog with cancellation details
- User can: Provide cancellation reason (optional)
- User can: Confirm or cancel the action
- After confirmation: Subscription status changes to "Cancelled"

**Test 4: View Churn Risk**
- User views: Churn Risk Panel
- User sees: Risk level, score, and recommendations
- User can: Click on recommendations to take action

---

### 6.5 Address Management Page

#### What Users See:
- **Current Addresses**
  - Billing address card
  - Shipping address card (if applicable)
  - Address validation status indicator
  - "Edit" button for each address

- **Address Form** (when editing)
  - Street address field
  - City field
  - State/Province dropdown
  - Postal/ZIP code field
  - Country dropdown
  - Address type selector (Billing/Shipping)
  - Validation status indicator
  - "Save" and "Cancel" buttons

- **Natural Language Input Option**
  - "Or describe your address" text link
  - Opens chat interface for natural language address entry

#### What Users Can Do:
- View current addresses
- Edit existing addresses
- Add new addresses
- Enter address using natural language
- See address validation status
- Save or cancel address changes

#### Testing Scenarios:

**Test 1: Edit Address via Form**
- User clicks: "Edit" on billing address
- User sees: Address form pre-filled with current address
- User can: Modify fields and save
- After save: Success message and updated address displayed

**Test 2: Natural Language Address Entry**
- User clicks: "Or describe your address"
- User types: "Change my billing address to 123 Main Street, New York, NY 10001"
- User sees: Parsed address in form with validation status
- User can: Confirm or modify parsed address
- After confirmation: Address updated successfully

**Test 3: Address Validation**
- User enters: Address in form
- User sees: Validation status indicator (Valid/Invalid/Needs Review)
- User can: See validation score and any warnings
- System shows: Suggestions for address correction if invalid

---

### 6.6 Recommendations & Insights Page

#### What Users See:
- **Upgrade Recommendations**
  - Recommended plan card
  - Reason for recommendation
  - Feature comparison (current vs. recommended)
  - Estimated value/benefit
  - "Upgrade Now" button

- **Usage Insights**
  - Feature utilization chart
  - Storage usage visualization
  - Usage trends over time
  - Unused features highlighted

- **Behavioral Insights**
  - Engagement score
  - Activity timeline
  - Sentiment indicator
  - Trend direction (Improving/Declining/Stable)

- **Churn Risk Analysis**
  - Risk level visualization
  - Risk factors list
  - Timeline showing risk changes
  - Actionable recommendations

#### What Users Can Do:
- View personalized upgrade recommendations
- See usage statistics and insights
- Review behavioral analytics
- Check churn risk analysis
- Act on recommendations
- Export insights report

#### Testing Scenarios:

**Test 1: View Recommendations**
- User navigates: To recommendations page
- User sees: Personalized upgrade suggestions
- User can: Click "Why this plan?" to see reasoning
- User can: Click "Upgrade Now" to proceed

**Test 2: Review Usage Insights**
- User views: Usage statistics section
- User sees: Charts showing feature utilization
- User can: Identify unused features
- User can: See usage trends over time

**Test 3: Check Churn Risk**
- User views: Churn Risk Analysis section
- User sees: Risk level, factors, and recommendations
- User can: Review risk timeline
- User can: Click recommendations to take action

---

### 6.7 Testing Checklist for All Functionality

#### Natural Language Interface Testing:
- [ ] User can ask "When does my subscription renew?" and receive accurate answer
- [ ] User can say "Cancel my subscription" and see confirmation dialog
- [ ] User can search "plans under $50" and see relevant results
- [ ] User can request "Upgrade to Pro plan" and complete upgrade
- [ ] User can ask "What's included in my plan?" and see feature list
- [ ] User can use "Update address to..." and successfully update address
- [ ] User can ask follow-up questions in conversation
- [ ] User can cancel actions before confirmation

#### Semantic Search Testing:
- [ ] User can search "cheapest plan" and see lowest price plan
- [ ] User can search "plans with unlimited storage" and see matching plans
- [ ] User can search "enterprise plans" and see enterprise tier plans
- [ ] User can search "plans with priority support" and see relevant results
- [ ] Search results show relevance scores
- [ ] Search understands synonyms and variations

#### Action Handling Testing:
- [ ] User can subscribe to a plan via natural language
- [ ] User can cancel subscription with confirmation
- [ ] User can upgrade subscription and see price difference
- [ ] User can downgrade subscription with feature loss warning
- [ ] User can update address via natural language
- [ ] All actions show appropriate confirmation dialogs
- [ ] All actions show success/error messages
- [ ] Actions are tracked for behavior analytics

#### Behavior Analytics Testing:
- [ ] Churn risk indicator updates based on user behavior
- [ ] User can view churn risk details and recommendations
- [ ] Usage statistics display correctly
- [ ] Behavioral insights show engagement trends
- [ ] Recommendations are personalized based on usage
- [ ] Sentiment indicator reflects user activity

#### Recommendations Testing:
- [ ] Upgrade recommendations appear based on usage
- [ ] Recommendations include clear reasoning
- [ ] User can view feature comparison
- [ ] User can act on recommendations directly
- [ ] Recommendations update as usage changes

#### Address Management Testing:
- [ ] User can view current addresses
- [ ] User can edit addresses via form
- [ ] User can enter address via natural language
- [ ] Address validation status displays correctly
- [ ] Invalid addresses show correction suggestions
- [ ] Address updates save successfully

#### Visual Indicators Testing:
- [ ] Subscription status colors are clear (green/yellow/red)
- [ ] Churn risk levels are visually distinct
- [ ] Confirmation dialogs are prominent for high-risk actions
- [ ] Success/error messages are clearly visible
- [ ] Loading indicators appear during processing
- [ ] All interactive elements have hover states

---

### 6.8 User Experience Flow Examples

#### Flow 1: Natural Language Subscription Cancellation
1. User opens chat interface
2. User types: "I want to cancel my subscription"
3. System shows: Confirmation dialog with subscription details
4. User reviews: Cancellation date, refund policy, access end date
5. User confirms: Clicks "Confirm Cancellation"
6. System processes: Cancellation action
7. User sees: Success message with cancellation confirmation
8. System updates: Dashboard shows cancelled subscription status

#### Flow 2: Semantic Plan Discovery
1. User navigates: To plans discovery page
2. User types: "Show me plans under $50 with unlimited storage"
3. System searches: Semantic search across all plans
4. User sees: Matching plans with relevance scores
5. User clicks: On a plan card to view details
6. User reviews: Full plan features and pricing
7. User subscribes: Clicks "Subscribe" button
8. System processes: Subscription creation
9. User sees: Success message and new subscription details

#### Flow 3: Address Update via Natural Language
1. User navigates: To address management page
2. User clicks: "Or describe your address" link
3. Chat interface opens: For address entry
4. User types: "Change my billing address to 123 Main Street, New York, NY 10001"
5. System parses: Address from natural language
6. User sees: Parsed address in form with validation status
7. User confirms: Address is correct
8. System validates: Address format and completeness
9. User saves: Address update
10. System confirms: Address updated successfully

#### Flow 4: Upgrade Recommendation Follow-Through
1. User views: Recommendations page
2. User sees: "Recommended: Enterprise Plan" card
3. User clicks: "Why this plan?" to see reasoning
4. User reviews: Feature comparison and usage analysis
5. User decides: To upgrade
6. User clicks: "Upgrade Now" button
7. System shows: Upgrade confirmation with price difference
8. User confirms: Upgrade action
9. System processes: Plan upgrade
10. User sees: Success message and updated subscription

---

## 7. Technical Architecture

### 6.1 System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    User Interface Layer                      │
│  (Web UI, Mobile App, Chat Interface, Voice Assistant)        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ↓
┌─────────────────────────────────────────────────────────────┐
│              AI Fabric Framework - Intent Layer              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ IntentQueryExtractor                                │   │
│  │  - Extracts intent from natural language            │   │
│  │  - Determines ACTION vs INFORMATION                 │   │
│  └────────────────────────────────────────────────────┘   │
│                       │                                      │
│                       ↓                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ActionHandlerRegistry                                │   │
│  │  - Routes ACTION intents to handlers                 │   │
│  │  - Manages action metadata                           │   │
│  └────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ↓                             ↓
┌──────────────────┐        ┌──────────────────┐
│ ACTION Intent    │        │ INFORMATION      │
│                  │        │ Intent           │
│ ActionHandlers:  │        │                  │
│ - Subscribe      │        │ RAGProvider      │
│ - Cancel         │        │ AISearchService  │
│ - Upgrade        │        │                  │
│ - Downgrade      │        │                  │
│ - UpdateAddress  │        │                  │
└──────────────────┘        └──────────────────┘
        │                             │
        ↓                             ↓
┌─────────────────────────────────────────────────────────────┐
│              Business Logic Layer                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ SubscriptionService                                   │   │
│  │  - @AIProcess annotations for vector sync            │   │
│  │  - Business logic execution                          │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ BehaviorAnalysisService (Framework)                   │   │
│  │  - Track events (SUBSCRIBE, CANCEL, UPGRADE)         │   │
│  │  - Calculate churn risk                              │   │
│  │  - Generate insights                                 │   │
│  └────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ↓
┌─────────────────────────────────────────────────────────────┐
│              Data Layer                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ JPA Entities with @AICapable                          │   │
│  │  - SubscriptionPlan (indexed for search)               │   │
│  │  - Subscription (tracked for behavior)                │   │
│  │  - Address (PII protected)                           │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Vector Database (Lucene/Milvus/Qdrant)               │   │
│  │  - Automatic indexing via @AIProcess                 │   │
│  │  - Semantic search capabilities                      │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Framework Integration Points

#### Intent Extraction
- **Service:** `IntentQueryExtractor`
- **Input:** Natural language query
- **Output:** `MultiIntentResponse` with ACTION or INFORMATION intents
- **Configuration:** Available actions registered via `AIActionProvider`

#### Action Handling
- **Service:** `ActionHandlerRegistry`
- **Handlers:** User-implemented `ActionHandler` interfaces
- **Flow:** Intent → Handler → Business Logic → Result

#### Semantic Search
- **Service:** `AISearchService`
- **Use:** Plan discovery, subscription queries
- **Indexing:** Automatic via `@AICapable` annotation

#### Behavior Analytics
- **Service:** `BehaviorAnalysisService`
- **Events:** SUBSCRIBE, UNSUBSCRIBE, UPGRADE, DOWNGRADE, UPDATE_ADDRESS
- **Output:** Churn risk scores, sentiment, trends, recommendations

#### RAG for Q&A
- **Service:** `RAGProvider`
- **Use:** Answer questions about subscriptions, plans, billing
- **Context:** Subscription data indexed in vector database

---

## 8. Data Models

### 7.1 SubscriptionPlan Entity

```java
@Entity
@AICapable(
    entityType = "subscription-plan",
    autoEmbedding = true,              // ✅ Framework: Auto-generate embeddings
    indexable = true,                  // ✅ Framework: Enable search indexing
    enableRecommendations = true,      // ✅ Framework: Enable recommendations
    indexingStrategy = IndexingStrategy.ASYNC
)
public class SubscriptionPlan {
    @Id
    private UUID id;
    
    @AISearchable(weight = 2.0)       // ✅ Framework: High weight for search
    private String name;               // "Pro Plan", "Enterprise Plan"
    
    @AISearchable(weight = 1.5)       // ✅ Framework: Searchable description
    @Column(columnDefinition = "TEXT")
    private String description;        // Full plan description
    
    @AIContext(contextKey = "price", dataType = "decimal")
    private BigDecimal monthlyPrice;
    
    @AIContext(contextKey = "annualPrice", dataType = "decimal")
    private BigDecimal annualPrice;
    
    @AIContext(contextKey = "tier")
    private String tier;               // BASIC, PRO, ENTERPRISE
    
    @AIContext(contextKey = "features")
    private List<String> features;     // ["Unlimited storage", "Priority support"]
    
    @AIContext(contextKey = "maxUsers")
    private Integer maxUsers;
    
    @AIContext(contextKey = "storageGB")
    private Integer storageGB;
    
    private Boolean isActive;
}
```

**Framework Benefits:**
- ✅ Automatic vector indexing for semantic search
- ✅ Searchable by natural language ("plans under $50")
- ✅ Recommendations based on usage patterns
- ✅ Real-time synchronization with database

### 7.2 Subscription Entity

```java
@Entity
@AICapable(
    entityType = "subscription",
    autoEmbedding = false,             // Subscription metadata, not content
    indexable = true                   // ✅ Framework: Index for queries
)
public class Subscription {
    @Id
    private UUID id;
    
    private UUID userId;
    private UUID planId;
    
    @AIContext(contextKey = "status")
    private SubscriptionStatus status; // ACTIVE, CANCELLED, PAST_DUE, EXPIRED
    
    @AIContext(contextKey = "startDate", dataType = "datetime")
    private LocalDateTime startDate;
    
    @AIContext(contextKey = "endDate", dataType = "datetime")
    private LocalDateTime endDate;
    
    @AIContext(contextKey = "billingCycle")
    private BillingCycle billingCycle; // MONTHLY, ANNUAL
    
    @AIContext(contextKey = "churnRisk", dataType = "decimal")
    private Double churnRiskScore;     // ✅ Framework: From BehaviorAnalysisService
    
    @AIContext(contextKey = "lastActivityDate", dataType = "datetime")
    private LocalDateTime lastActivityDate;
    
    @OneToOne(cascade = CascadeType.ALL)
    private Address billingAddress;
    
    @OneToOne(cascade = CascadeType.ALL)
    private Address shippingAddress;
}
```

**Framework Benefits:**
- ✅ Indexed for relationship queries ("subscriptions expiring this month")
- ✅ Churn risk calculated by BehaviorAnalysisService
- ✅ Metadata available for RAG context

### 7.3 Address Entity (PII Protected)

```java
@Entity
public class Address {
    @Id
    private UUID id;
    
    @AISearchable(weight = 1.0)       // ✅ Framework: Searchable
    private String streetAddress;
    
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    @AIContext(contextKey = "addressType")
    private AddressType type;          // BILLING, SHIPPING
    
    @AIContext(contextKey = "isValidated")
    private Boolean isValidated;        // ✅ Framework: AI validation result
    
    @AIContext(contextKey = "validationScore", dataType = "decimal")
    private Double validationScore;     // ✅ Framework: Confidence score
}
```

**Framework Benefits:**
- ✅ PII detection via `PIIDetectionService`
- ✅ AI-powered address validation
- ✅ Secure handling of sensitive data

---

## 8. API Endpoints

### 8.1 Natural Language Interface

#### POST `/api/subscriptions/query`
**Purpose:** Natural language query interface  
**Framework Integration:** Intent extraction + RAG/actions

**Request:**
```json
{
  "query": "Cancel my subscription",
  "userId": "user-123"
}
```

**Response (ACTION Intent):**
```json
{
  "intentType": "ACTION",
  "action": "cancel_subscription",
  "requiresConfirmation": true,
  "confirmationMessage": "Are you sure you want to cancel subscription sub-456?",
  "actionParams": {
    "subscriptionId": "sub-456"
  }
}
```

**Response (INFORMATION Intent):**
```json
{
  "intentType": "INFORMATION",
  "response": "Your subscription renews on March 15, 2026. You're currently on the Pro plan at $49/month.",
  "sources": ["subscription:sub-456"]
}
```

### 8.2 Action Execution

#### POST `/api/subscriptions/actions/execute`
**Purpose:** Execute confirmed actions  
**Framework Integration:** `ActionHandler.executeAction()`

**Request:**
```json
{
  "action": "cancel_subscription",
  "params": {
    "subscriptionId": "sub-456",
    "reason": "too expensive"
  },
  "userId": "user-123",
  "confirmed": true
}
```

**Response:**
```json
{
  "success": true,
  "message": "Your subscription has been cancelled successfully",
  "data": {
    "subscriptionId": "sub-456",
    "status": "CANCELLED",
    "endDate": "2026-01-15T10:30:00Z"
  }
}
```

### 8.3 Semantic Search

#### POST `/api/subscriptions/plans/search`
**Purpose:** Semantic search for subscription plans  
**Framework Integration:** `AISearchService.performSearch()`

**Request:**
```json
{
  "query": "plans under $50 per month with unlimited storage",
  "limit": 10
}
```

**Response:**
```json
{
  "results": [
    {
      "id": "plan-123",
      "name": "Pro Plan",
      "monthlyPrice": 49.99,
      "description": "Professional plan with unlimited storage...",
      "relevanceScore": 0.95
    }
  ],
  "totalResults": 1
}
```

### 8.4 Behavior Analytics

#### GET `/api/subscriptions/{id}/churn-risk`
**Purpose:** Get churn risk analysis  
**Framework Integration:** `BehaviorAnalysisService.analyzeUser()`

**Response:**
```json
{
  "subscriptionId": "sub-456",
  "churnRisk": 0.75,
  "churnReason": "User has not logged in for 30 days and subscription expires in 5 days",
  "sentiment": "FRUSTRATED",
  "trend": "RAPIDLY_DECLINING",
  "recommendations": [
    "Send retention offer: 20% discount for next 3 months",
    "Highlight unused features in Pro plan",
    "Schedule proactive outreach call"
  ]
}
```

### 8.5 Recommendations

#### GET `/api/subscriptions/{id}/recommendations`
**Purpose:** Get personalized upgrade recommendations  
**Framework Integration:** `AICoreService.generateRecommendations()` + `BehaviorAnalysisService`

**Response:**
```json
{
  "recommendations": [
    {
      "planId": "plan-789",
      "planName": "Enterprise Plan",
      "reason": "You're using 95% of Pro plan features. Enterprise offers team collaboration you might need.",
      "confidence": 0.88,
      "estimatedValue": "Save 2 hours/week with team features"
    }
  ]
}
```

---

## 10. Intent Action Handling

### 9.1 Action Handler Implementation

#### CancelSubscriptionActionHandler

```java
@Component
public class CancelSubscriptionActionHandler implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    private final BehaviorAnalysisService behaviorService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel an active subscription")
            .parameters(List.of("subscriptionId", "reason"))
            .requiresConfirmation(true)
            .riskLevel("HIGH")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // ✅ Framework: Check if user has active subscription
        return subscriptionService.hasActiveSubscription(userId);
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String subscriptionId = (String) params.get("subscriptionId");
        Subscription subscription = subscriptionService.findById(subscriptionId);
        
        return String.format(
            "Are you sure you want to cancel your %s subscription? " +
            "You'll lose access on %s. This action cannot be undone.",
            subscription.getPlan().getName(),
            subscription.getEndDate()
        );
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String subscriptionId = (String) params.get("subscriptionId");
        String reason = (String) params.getOrDefault("reason", "User requested");
        
        // ✅ Framework: @AIProcess ensures vector sync
        Subscription subscription = subscriptionService.unsubscribe(
            UUID.fromString(subscriptionId), 
            reason
        );
        
        // ✅ Framework: Track event for behavior analysis
        behaviorService.trackEvent(userId, "UNSUBSCRIBE", Map.of(
            "subscriptionId", subscriptionId,
            "reason", reason
        ));
        
        return ActionResult.builder()
            .success(true)
            .message("Your subscription has been cancelled successfully")
            .data(Map.of(
                "subscriptionId", subscriptionId,
                "status", subscription.getStatus().toString(),
                "endDate", subscription.getEndDate().toString()
            ))
            .build();
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error cancelling subscription for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel subscription. Please contact support.")
            .errorCode("CANCEL_FAILED")
            .build();
    }
}
```

### 9.2 Action Registration

```java
@Component
public class SubscriptionActionProvider implements AIActionProvider {
    
    private final List<ActionHandler> actionHandlers;
    
    public SubscriptionActionProvider(
        CancelSubscriptionActionHandler cancelHandler,
        SubscribeActionHandler subscribeHandler,
        UpgradeSubscriptionActionHandler upgradeHandler,
        DowngradeSubscriptionActionHandler downgradeHandler,
        UpdateAddressActionHandler updateAddressHandler
    ) {
        this.actionHandlers = List.of(
            cancelHandler,
            subscribeHandler,
            upgradeHandler,
            downgradeHandler,
            updateAddressHandler
        );
    }
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return actionHandlers.stream()
            .map(handler -> {
                AIActionMetaData metadata = handler.getActionMetadata();
                return ActionInfo.builder()
                    .name(metadata.getName())
                    .description(metadata.getDescription())
                    .parameters(metadata.getParameters())
                    .requiresConfirmation(metadata.isRequiresConfirmation())
                    .build();
            })
            .collect(Collectors.toList());
    }
}
```

### 9.3 Natural Language to Action Flow

```
User: "I want to cancel my subscription"
    ↓
POST /api/subscriptions/query
    ↓
IntentQueryExtractor (Framework)
    ├─ Analyzes query
    ├─ Checks available actions (from AIActionProvider)
    └─ Extracts intent
    ↓
Intent Response:
{
  "type": "ACTION",
  "action": "cancel_subscription",
  "actionParams": {
    "subscriptionId": "sub-456"  // Extracted from user context
  }
}
    ↓
ActionHandlerRegistry.findHandler("cancel_subscription")
    ↓
CancelSubscriptionActionHandler
    ├─ validateActionAllowed(userId) ✅
    ├─ getConfirmationMessage(params)
    └─ Returns confirmation
    ↓
Response to User:
{
  "requiresConfirmation": true,
  "confirmationMessage": "Are you sure you want to cancel your Pro subscription?"
}
    ↓
User: "Yes, confirm"
    ↓
POST /api/subscriptions/actions/execute
    ↓
CancelSubscriptionActionHandler.executeAction()
    ├─ subscriptionService.unsubscribe()  // @AIProcess ensures sync
    ├─ behaviorService.trackEvent()       // Track for analytics
    └─ Returns success result
    ↓
Response:
{
  "success": true,
  "message": "Your subscription has been cancelled successfully"
}
```

---

## 11. Implementation Roadmap

### Phase 1: Core Framework Integration (Week 1-2)

#### ✅ **Week 1: Setup & Basic Search**
- [ ] Configure AI Fabric Framework dependencies
- [ ] Create `SubscriptionPlan` entity with `@AICapable`
- [ ] Implement semantic search for plans
- [ ] Test: "plans under $50" query

#### ✅ **Week 2: Intent Extraction**
- [ ] Configure `IntentQueryExtractor`
- [ ] Register available actions via `AIActionProvider`
- [ ] Test intent extraction for sample queries
- [ ] Implement basic ACTION vs INFORMATION routing

### Phase 2: Action Handlers (Week 3-4)

#### ✅ **Week 3: Critical Actions**
- [ ] Implement `CancelSubscriptionActionHandler`
- [ ] Implement `SubscribeActionHandler`
- [ ] Add confirmation flow
- [ ] Test: "cancel my subscription" → confirmation → execution

#### ✅ **Week 4: Additional Actions**
- [ ] Implement `UpgradeSubscriptionActionHandler`
- [ ] Implement `DowngradeSubscriptionActionHandler`
- [ ] Implement `UpdateAddressActionHandler`
- [ ] Test all action flows

### Phase 3: Behavior Analytics (Week 5-6)

#### ✅ **Week 5: Event Tracking**
- [ ] Integrate `BehaviorAnalysisService`
- [ ] Track subscription events (SUBSCRIBE, CANCEL, UPGRADE)
- [ ] Test event tracking

#### ✅ **Week 6: Churn Prediction**
- [ ] Implement churn risk calculation
- [ ] Create at-risk subscriber alerts
- [ ] Test churn prediction accuracy

### Phase 4: Advanced Features (Week 7-8)

#### ✅ **Week 7: RAG Integration**
- [ ] Configure `RAGProvider` for subscription Q&A
- [ ] Index subscription data for RAG context
- [ ] Test: "When does my subscription renew?"

#### ✅ **Week 8: Recommendations**
- [ ] Implement upgrade recommendations
- [ ] Use behavior insights for personalization
- [ ] Test recommendation quality

### Phase 5: Polish & Testing (Week 9-10)

#### ✅ **Week 9: PII Protection**
- [ ] Integrate `PIIDetectionService` for addresses
- [ ] Add address validation
- [ ] Test PII detection

#### ✅ **Week 10: Testing & Documentation**
- [ ] End-to-end testing
- [ ] Performance testing
- [ ] User acceptance testing
- [ ] Documentation completion

---

## 12. Framework Capabilities Summary

### ✅ **What Users Can Do with AI Fabric Framework**

1. **Natural Language Interactions**
   - Ask questions: "When does my subscription renew?"
   - Give commands: "Cancel my subscription"
   - Search semantically: "plans with unlimited storage"

2. **Intent-Based Actions**
   - Framework extracts intent from natural language
   - Routes to appropriate business logic handlers
   - Handles confirmations automatically
   - Tracks actions for analytics

3. **Semantic Search**
   - Find plans by meaning, not keywords
   - Understand user intent ("cheap plans" = low price)
   - Filter by metadata automatically

4. **Behavior Analytics**
   - Track all user actions automatically
   - Predict churn risk
   - Generate personalized recommendations
   - Identify engagement trends

5. **RAG-Powered Q&A**
   - Answer questions using subscription context
   - Provide accurate information from indexed data
   - Understand follow-up questions

6. **Automatic Indexing**
   - Plans indexed automatically via `@AICapable`
   - Real-time sync via `@AIProcess`
   - No manual vector management needed

7. **PII Protection**
   - Detect sensitive information automatically
   - Validate addresses with AI
   - Secure handling of billing data

---

## 13. Success Criteria

### Technical Success
- ✅ All 5 action handlers implemented and tested
- ✅ Intent extraction accuracy >90%
- ✅ Semantic search relevance >85%
- ✅ Churn prediction accuracy >75%
- ✅ Response time <2 seconds

### Business Success
- ✅ 20-35% reduction in churn rate
- ✅ 15-25% increase in upgrade conversions
- ✅ 40-50% reduction in support tickets
- ✅ User satisfaction >4.5/5.0

---

## 14. Conclusion

### 🎯 **Recommendation: This is the IDEAL use case for AI Fabric Framework**

The **Subscription Management Hub** demonstrates:

1. **Intent Action Handling** - Complete workflow from natural language to business logic
2. **Semantic Search** - Natural language plan discovery
3. **Behavior Analytics** - Churn prediction and recommendations
4. **RAG Integration** - Context-aware Q&A
5. **Automatic Indexing** - Zero-config vector management
6. **PII Protection** - Secure data handling

This use case showcases the full power of AI Fabric Framework in a real-world, production-ready application.

---

**Document Status:** ✅ Ready for Implementation  
**Framework Version:** 1.0.0  
**Last Updated:** January 2026
