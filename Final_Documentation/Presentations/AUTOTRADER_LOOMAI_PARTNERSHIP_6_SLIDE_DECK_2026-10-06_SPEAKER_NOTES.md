# Autotrader And LoomAI Partnership Deck - Speaker Notes

Date: 6 October 2026

Recommended presentation time: 10-12 minutes, followed by discussion

## Meeting outcome

Ask Autotrader to approve an architecture and data-rights workshop, identify the
correct Connect sponsor and Integration Manager, and define a bounded one-dealer
pilot.

## Slide 1 - The proposition

Open by correcting the category assumption: LoomAI is not a chatbot product. It
is an AI enablement and deployment layer that can productise four behaviours
from the same governed primitives.

- **Conversational AI** supports user-led journeys, grounded answers and
  governed actions.
- **Smart Brain** runs fixed analysis or operational plans from system events
  and task queues without waiting for a chat prompt.
- **Multi-agent teams** use a conversation manager to coordinate genuinely
  distinct specialist workers.
- **Sequential and parallel plans** execute declarative workers in order or
  concurrently and bring their results into a governed outcome.

For Autotrader, authorised automotive data can power any of these behaviours
while every dealership remains isolated. LoomAI does not replace Autotrader,
the dealership website, a CRM/DMS, or Deal Builder.

## Slide 2 - The buyer experience

The intended outcome is not more chat. A buyer describes an ordinary need,
receives a dealer-only shortlist, compares vehicles using consistent evidence,
revalidates important live facts, and approves the context passed into the next
journey. The same generic chat application provides a small docked composer and
a full Max workspace for search, comparison, sources, attachments, and governed
actions.

The live demonstration is available at
[loomai.pro/demos/dealership-ai](https://loomai.pro/demos/dealership-ai).

## Slide 3 - The AI-enabled workspace

The experience is more than a chatbot placed over a website. The generic LoomAI
chat application becomes a persistent workspace around the dealership journey:
the conversation, selected vehicles, and attached pages remain available while
the buyer navigates; tools change with the active context without hiding the
broader stock-search tools; and evidence and actions can be rendered as useful
interactive surfaces rather than raw AI text.

For a vehicle page, that means the buyer can inspect live details, ask about
everyday suitability or trade-offs, locate the car, and request a test drive or
callback from the same workspace. The host injects dealership-specific labels,
tools, and renderers into generic Chat UI extension points. Automotive logic does
not become hard-coded into the reusable chat application.

## Slide 4 - Freshness and trust

Use the phrase: **index for discovery; re-read for decisions**.

A baseline creates the dealer-scoped projection and semantic index. A verified
stock event triggers targeted reconciliation of the affected `stockId`, followed
by upsert or deletion and indexing completion. When price, availability, or
another operational fact matters, a typed live action confirms it before the
answer or handoff. The vector index is derived and rebuildable; it is not the
source of truth.

The proposed production boundary is one advertiser per deployment, permitted
fields only, no cross-dealer retrieval, deterministic deletion, and no model
authority over identifiers or business effects.

## Slide 5 - Why partner

Autotrader brings the trusted data, advertiser relationship, Connect contracts,
marketplace reach, and conversion products. LoomAI brings dealer-isolated AI
deployments, grounded reasoning, governed actions, reusable packaging, release
gates, and an embeddable customer experience.

The joint value is a better-qualified buyer with fewer unsupported answers and
consented context that can continue into the workflow Autotrader and the dealer
choose. Position LoomAI as complementary to existing Autotrader products and
LivePerson, not as a forced replacement.

## Slide 6 - Pilot and ask

Propose one dealership, one advertiser, one isolated deployment, read-first
discovery and comparison, dealer-owned policy knowledge, live validation where
granted, and one confirmed handoff. Exclude provider writes unless the exact
capability is explicitly approved.

Request:

1. A named product sponsor and Connect Integration Manager.
2. Sandbox credentials, exact capability grants, a test advertiser, and
   representative stock.
3. Written rules for persistence, embeddings, model inference, retention,
   display, attribution, and deletion.
4. Notification registration, verification, retry, and test-event guidance.
5. The preferred Deal Builder, enquiry, CRM, or appointment handoff.
6. The production go-live checklist and one candidate design-partner dealer.

Measure useful results, shortlist/comparison completion, fact support,
freshness, qualified handoffs, advertiser isolation, and deterministic deletion.
Chat volume is diagnostic, not the primary success measure.

## Official discussion sources

- [Autotrader Connect](https://www.autotrader.co.uk/partners/retailer/platform/autotrader-connect)
- [Add a retailer to an integration](https://www.autotrader.co.uk/partners/retailer/platform/autotrader-connect/add-to-an-integration)
- [Autotrader Connect advertiser business rules](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
- [Autotrader Dealer Website chat guidance](https://help.autotrader.co.uk/hc/en-gb/articles/13233677505949-Can-I-put-chat-on-a-Dealer-Website)
- [Autotrader Connect integration guidance](https://help.autotrader.co.uk/hc/en-gb/sections/21767572815005-Autotrader-Connect)
