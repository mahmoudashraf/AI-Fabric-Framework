# LinkedIn Post: LoomAI Dealership AI Workspace Demo

We have turned a fictional dealership website into a live AI buying workspace, not just another chat window.

The Northfield demo lets a visitor:

- search and compare current stock;
- ask questions that combine vehicle facts with approved dealership policies;
- attach the current vehicle page as working context;
- create a confirmed callback or test-drive request; and
- move the same conversation to dealership staff, then back to AI.

Technically, the website integration is one Platform-hosted script:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_..."
></script>
```

That installation resolves the retailer's current verified LoomAI deployment and loads a reusable dealership experience pack. The pack injects brand, buyer tools, vehicle presentation and page context while the generic AI Workspace stays domain-neutral.

Each dealership receives its own deployment boundary. Inventory synchronization maps dealer-scoped provider stock into indexed `dealer-vehicle` evidence. Live read actions provide current operational facts. Approved dealership documents answer warranty, delivery and test-drive questions. Confirmed write actions stay behind reviewed routes and application policy.

Human handover is part of the same governed conversation. The model can select `request_conversation_handover`, but application intake and AI Fabric's durable state machine control the transition. Staff claim and reply through a protected view; AI is blocked while the person owns the conversation; returning to AI is explicit.

The public demo uses fictional data and an Auto Trader public-contract simulator, not Auto Trader production credentials or an endorsement. A production pilot would replace that profile with approved advertiser-scoped access. The browser never receives provider credentials, and the AI cannot issue arbitrary provider calls.

The product goal is simple: help a buyer build confidence before asking for contact, then give the dealership a better-informed conversation instead of an empty lead form.

Watch the demo: https://youtu.be/rNl_bcMiS4Y

Try the experience: https://loomai.pro/demos/dealership-ai

Platform: https://loomai.pro/products/loomai-platform

#AutomotiveRetail #CustomerExperience #GenerativeAI #HumanInTheLoop #AIWorkspace
