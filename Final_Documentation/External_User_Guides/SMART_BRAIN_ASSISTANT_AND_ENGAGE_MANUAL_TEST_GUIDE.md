# Create a Smart Brain with AI assistance and test it in Engage

LoomAI Labs | Manual integration exercise | 12 November 2026

Build a fictional office-furniture assistant from a real API document, review
the saved actions, deploy a staging Brain, then use it through both a standalone
Engage app and a website widget. This tests configuration assistance and the
customer experience without using a real customer's systems or personal data.

The temporary Harbor Office API is already running. **Your Brain and Engage
apps have not been created for you.** Follow this guide using your own Console
account. Estimated hands-on time is 45-75 minutes, plus provisioning/indexing.

## 1. Your lab and the result to aim for

- Console: [Open LoomAI Console](https://console.loomai.pro/).
- API document: [OpenAPI JSON](https://assistant-lab-20261112.46.224.145.148.sslip.io/openapi.json).
- Test website: [Harbor Office](https://assistant-lab-20261112.46.224.145.148.sslip.io/demo).
- Evidence counters: [Read-only lab status](https://assistant-lab-20261112.46.224.145.148.sslip.io/lab/status).
- API health: [Health endpoint](https://assistant-lab-20261112.46.224.145.148.sslip.io/health).
- Exact API base URL and embedded website origin:
  `https://assistant-lab-20261112.46.224.145.148.sslip.io`.

The test service source is outside the Platform repository at
`/Users/mahmoudashraf/Downloads/Projects/loomai-assistant-lab`.
Its private credential is in the ignored auth document under **Harbor Office
Manual Assistant/Engage Lab - 12 November 2026**, and separately in
`/Users/mahmoudashraf/.codex/private/loomai-assistant-lab-20261112/LAB_ACCESS_PRIVATE.md`.
Do not paste the credential into the assistant, API documentation or widget code.

**Target outcome:** a customer can find an available desk below GBP 600, understand
delivery, compare products, submit an explicitly confirmed test enquiry and see
the real receipt. The same Brain powers the standalone app and website widget.

**Safety:** use only `alex@example.com` or another `example.com`/`.test` address.
The API rejects real-looking contact addresses. Enquiries do not send email,
reserve stock, create purchases or start delivery. They are held in memory for
up to 24 hours and are lost on a service restart. Counters are shared across all
testers; avoid simultaneous write tests and compare before/after values.

### Reference facts

- Harbor Standing Desk: `prd-desk-01`, GBP 499, 8 in stock, height adjustable.
- Harbor Ergonomic Chair: `prd-chair-01`, GBP 249, 12 in stock, height adjustable.
- Harbor Executive Desk: `prd-desk-02`, GBP 799, out of stock, not height adjustable.
- Delivery: UK mainland only; GBP 25 below GBP 500; free from GBP 500;
  estimated 3-5 working days; installation is not included.
- Therefore, one GBP 499 Standing Desk costs GBP 524 including standard delivery.
  The Brain must not describe that order as eligible for free delivery.

<!-- pagebreak -->

## 2. Create your workspace and Brain

### Customer route

1. Sign in to Console as the customer workspace in which you want this test.
   Use an existing account or complete normal signup and workspace onboarding.
2. Open **My kits** and start **AI customer experience**. If you intentionally
   entered through the developer page, use **Customer experience Brain** instead;
   its staging-only policy is expected. Do not choose the dealership template.
3. Choose an available **Knowledge workspace** starting experience with a
   selectable workspace style. If several offers have the same name, use the
   one exposing the customer workspace style, not an unavailable historical offer.
   If none is available, stop and request template access rather than guessing IDs.
4. Set the Kit name to **Harbor Office Lab** and the customer-facing assistant
   name to **Harbor Office Assistant**. Choose staging and an offered AI-state
   storage option. This is conversation/AI state, not a furniture database.
5. Choose **Both** delivery channels if offered, or start standalone and add the
   embed later. Use an available lowercase address such as `harbor-lab-yourname`.
   The full address preview should end in `.apps.loomai.pro`. Do not enter a full
   URL or spaces in the address slug.
6. For the embed, enter the exact lab origin from section 1, without `/demo`.
   Keep Human support and ticketing off for this first exercise.
7. Review and choose **Create my workspace**. Open the Kit's Smart Brain.
   Record its Brain ID and Kit URL. Creating configuration does not deploy a runtime.

### Optional blank-Brain route for staff

If your staff UI offers **Create Smart Brain -> Start from scratch**, you may
use that instead. Choose a Conversational Brain named Harbor Office Lab in
staging, owned by the intended customer. You must configure its supported model,
credentials, storage and release target yourself, then create Engage manually.
The Kit route above avoids that extra wiring and tests the normal customer story.

### Finish the starting template requirements

8. Open **AI models**. Use an available model and valid owned/provider binding.
   GPT-5.4-mini is the recent qualification model when offered by your account;
   it is not selected automatically merely because the authoring assistant uses it.
   Do not remove usage or funding checks to make an unavailable model work.
9. The current Knowledge workspace starter can require document storage.
   Configure **Knowledge -> storage** using LoomAI-provided storage or an owned
   S3-compatible binding. Keep any required vector/search binding valid. The live
   API actions themselves do not require you to invent vector-space names.
10. If the starter needs a source file, upload the supplied
    `harbor-returns-policy.txt` from the external lab folder using the Knowledge
    upload flow. Then discover/preview, approve and index it explicitly. This file
    covers returns, not the catalogue/delivery facts used by the API tests.
11. Keep all changes in this new lab Brain. Do not reset or repurpose an existing
    production Brain. The default workspace allowance is two provisioned
    environments; drafts and resource-free Engage apps do not use another slot.

**Checkpoint:** the Kit and Brain exist in your account, staging is selected,
and any missing provider/storage requirements are visible rather than bypassed.

<!-- pagebreak -->

## 3. Let the assistant prepare your actions

1. In the Brain, open **AI capabilities -> Actions** and choose **Build with AI**.
   Its page-specific introduction should explain action configuration.
2. Add the OpenAPI JSON URL from section 1. If URL import is unavailable, download
   that same JSON and upload the file. Do not upload a screenshot of the document.
3. Review the imported catalogue. There are five business operations:
   `harbor_search_products`, `harbor_get_product`, `harbor_delivery_policy`,
   `harbor_create_enquiry` and `harbor_get_enquiry`.
4. Select those operations. Open **Turn options**. For this exercise, enable
   **Let AI fix validation errors** and allow at most **2 corrections** if the
   configured service permits it. Keep Preview only and Explain only off when
   you want a draft save. Two corrections means at most three generation attempts.
5. Leave the message empty and choose **Prepare actions**. Page context and the
   selected API document are sufficient to start. If the assistant needs a
   supported credential reference or another essential field, answer its question;
   do not insert the key value into chat.
6. Wait for completion. Open the actual saved changes and validation findings.
   A saved draft, the model saying "done", and a successful API call are three
   different things. The editor should refresh to the saved result.

### Review the five saved operations

- **Search products:** READ; GET `/v1/products`; optional `category`, `maxPrice`
  and `inStock` query parameters with their documented types. No hardcoded IDs.
- **Product details:** READ; GET `/v1/products/{productId}`; required product ID
  mapped into the path from its action parameter.
- **Delivery:** READ; GET `/v1/policies/delivery`; no invented required arguments.
- **Create enquiry:** WRITE; POST `/v1/enquiries`; JSON body with required
  `productId` and `email`, optional `message`; **confirmation required**.
- **Read enquiry:** READ; GET `/v1/enquiries/{reference}`; use the actual receipt
  reference, not a guessed number or the product ID.

All five operations in this lab can be permitted to anonymous app visitors.
The key is a **company-to-API credential**, not end-user sign-in. A private
customer-account lookup would need a separate verified identity/ownership setup;
do not add it to this exercise just to reproduce the earlier subscription issue.

For the four READs, enable the existing read/evidence policy so INFORMATION
questions can select them. They may supply live evidence without a vector
collection. Do not enable anonymous access on unrelated template capabilities.

### Optional follow-ups and Undo

Ask: **"Rename product search to Find office furniture. Keep its endpoint and
parameters unchanged."** Inspect the saved label, then use **Undo** and verify
the original label returns. Do not rely on the assistant's wording alone.

If there are actual validation errors, expand **Validation feedback**. Review
each attempt and why it stopped. A single valid first attempt is fine; do not
deliberately break the Brain to force a correction demo. If the limit is reached,
keep the findings and continue explicitly or edit the existing form manually.

**Checkpoint:** five reviewed actions are saved, their real HTTP routes match
the document, and the synthetic write still requires confirmation.

<!-- pagebreak -->

## 4. Connect the API and shape the responses

### Store the company key

1. Open **Keys & secrets -> Add key**. Choose the integration/security purpose.
2. Enter **HARBOR_LAB_KEY** in Key name, enter the key from the private lab
   credential note, and save. The name uses uppercase letters and underscores,
   with no spaces. Copy the actual returned reference; use that exact reference
   rather than assuming it is identical to the typed name.
3. Return to **AI capabilities -> Actions -> HTTP routes & connector**. Set the
   upstream base URL to the exact lab origin. Select **API_KEY** authentication,
   header **X-Lab-Key**, and the saved secret reference in the supported field.
   A technical environment-placeholder field uses `${RETURNED_REFERENCE}`;
   a field explicitly named secretRef takes the bare reference instead.
4. Set the optional safe connection check path to `/v1/connection-check`.
   Unlike public `/health`, this GET actually requires the company key.
5. Save. If you prefer AI help for the nonsecret wiring, reuse the API document
   in the Connections assistant and say **"Use my saved HARBOR_LAB_KEY
   reference for this API."** Inspect its result in the same routing form.

The API document intentionally omits the connection-check route from the five
business actions. It is a setup probe, not a capability the model needs to call.
The connector may attach this company key to the public reads too; visitors must
never see or supply it. Keep runtime-to-connector security separate and intact.

### Set response guidance

6. Open **Responses -> Build with AI**. Reuse this Brain's API document explicitly
   and paste the following short guide as text. Choose **Prepare responses**.

```text
You help customers choose Harbor Office furniture.
Use live product and delivery evidence for business facts.
Be friendly and concise. Compare useful differences when asked.
Do not ask the customer to choose a knowledge domain or API tool.
Ask for missing enquiry details and confirm before submission.
An enquiry is not a purchase, stock reservation or sent email.
Never invent a price, available stock, receipt or delivery promise.
Offer one relevant next step when it helps the customer.
```

7. Review both the saved instructions and visible profile selections. Use a
   suitable friendly/professional tone, grounded/**Your information only** answer
   breadth and balanced length. Inspect **Helpful next steps** explicitly if you
   want suggestions enabled; do not assume the model changed every preference.
8. If a prompt tells the customer to select a technical source/domain, remove
   that instruction from this draft through the editor or an explicit AI request.
   Do not replace semantic routing with keyword rules or enable search-all merely
   to hide an unrelated action mapping problem.
9. Open **Updates -> Check setup**. Inspect the aggregate result and Details.
   The API probe should verify using its saved key. Not tested is not passed.
   Correct missing credentials, invalid paths or actual configuration errors
   before publishing; no per-action write should run during setup checking.

**Checkpoint:** authenticated API connectivity is checked, response choices are
reviewed, and changes remain draft-only.

<!-- pagebreak -->

## 5. Deploy staging and activate Engage

### Publish and verify the Brain

1. Confirm **Staging** in the Brain header. In **Updates**, inspect Changes and
   the current draft/release comparison. Check that only intended Harbor changes
   are included; avoid publishing an unrelated unfinished edit.
2. Select a compatible runtime image with framework **0.8.50 or later** if this
   exercise is meant to include the new protected-READ behavior. A newer backend
   or a freshly saved draft does not upgrade an already running runtime.
3. Run the explicit publish-and-update/apply action for staging. On a fresh Brain,
   this creates its infrastructure. Wait for **verified/active**, not only a queued
   job or saved version. Record the applied version and runtime image.
4. Open **Try it** if available and confirm it points at that applied staging
   version. An unsaved draft is not what the running chat executes.

### Standalone app

5. Open the Kit's Engage app. If you used the blank-Brain route, open **Engage
   apps -> Create Engage app -> Connect deployment** and select this Brain.
6. Check the connection's environment and verified release. Choose the staging
   release explicitly if no verified one is selected. Do not create a second Brain
   or guess a technical assignment ID to connect the app.
7. Set the name Harbor Office Assistant, a colour you recognize, welcome text
   and useful starters such as **Find a desk**, **Compare furniture** and
   **Delivery options**. Review the visual preview in standalone and docked modes.
   Preview content is synthetic; it is not a successful live action.
8. For this anonymous lab, keep visitor authentication off unless the chosen
   starter requires a supported profile. No sign-in is needed by this lab's
   product/enquiry contract. Do not enter the company API key in Engage settings.
9. Verify the managed app hostname if pending. Refresh readiness. If origins or
   identity settings require a Brain update, use **Apply required deployment
   update**, inspect the proposed draft changes, confirm publish/apply, then
   choose **Use verified update**. Activation remains separate and explicit.
10. Activate the ready app and open its actual `.apps.loomai.pro` URL. Run the
    read scenarios in section 6 before the write tests.

### Website widget

11. Open the standalone installation and choose **Add website embed** if the Kit
    did not already create one. It should reuse the same Brain/connection and
    experience configuration; it is another delivery channel, not another runtime.
12. Set the allowed origin to the exact lab origin, without `/demo`. Resolve any
    required Brain-origin update, verify readiness and activate this embedded
    installation. Copy its **Installation ID** from the installation script.
13. Open the Harbor Office test website. Enter that public installation ID and
    choose **Load my Engage widget**. The page loads the fixed Platform installer.
    The ID is public installation configuration, not your API credential.
14. Test a docked conversation, expand to MAX, then minimize/reopen. Check the
    visible branding and conversation state. Run at least one read and one
    confirmation scenario here as well as in standalone.

The two channels share a Brain, not browser storage or an automatic cross-origin
conversation. Do not expect a standalone conversation to appear on the test site.
Custom DNS and production promotion are not prerequisites for this exercise.

<!-- pagebreak -->

## 6. Read and conversation scenarios

Judge meaning, evidence and actual behavior, not exact response wording. Keep
each first response, including errors; do not quietly replace a failed case with
an easier or more explicit question.

### R1 Find a product

**Say:** "I need an available height-adjustable desk under GBP 600."

**Expected:** Harbor Standing Desk, GBP 499, in stock; do not recommend the
GBP 799/out-of-stock Executive Desk as meeting the request. Catalogue evidence
must exist; an invented answer or an acknowledgement alone is not enough.

### R2 Combine products and policy

**Say:** "Would that desk qualify for free delivery to London, and what would
the total be?"

**Expected:** resolve "that desk" from conversation, use the delivery rules,
explain the GBP 500 threshold, GBP 25 delivery and GBP 524 total. A vague delivery
answer, invented free delivery or a request to choose a knowledge domain is a miss.
The customer should not have to name either tool.

### R3 Compare options

**Say:** "Compare that desk with the executive desk. Which can I get now?"

**Expected:** Standing Desk GBP 499/in stock/adjustable versus Executive Desk
GBP 799/out of stock/not adjustable. The system should retain the conversational
target and avoid claiming it can reserve the unavailable item.

### R4 Explain a restriction

**Say:** "Can you deliver to Paris tomorrow and install it?"

**Expected:** explain UK-mainland-only delivery, the 3-5-working-day estimate
and no installation. Do not invent an exception or say an arrangement was made.

### R5 Admit missing facts

**Say:** "What warranty does the chair have?"

**Expected:** the supplied product/policy data does not define a warranty. Say
what is unknown and offer an appropriate next step; do not invent a duration.
This is a good uncertainty response, not a failed knowledge lookup to cover up.

### R6 Ask about indexed knowledge

Only run this after the returns TXT has been approved and successfully indexed.

**Say:** "Can I return a used chair after ten days?"

**Expected:** the indexed guide permits unused goods in original packaging
within 14 days; used goods are not eligible under that change-of-mind policy.
Do not claim a refund was approved. If indexing is incomplete, record this as
blocked rather than pretend the uploaded file is already searchable.

**Helpful next steps:** suggestions should fit the latest result, such as an
enquiry for the available desk, not repeat the question or offer an unsupported
purchase. Suggestions being absent when configured Off is expected.

<!-- pagebreak -->

## 7. Confirmation and real receipt scenarios

Open the read-only lab counters in another tab. Record **enquiriesCreated**
before each test, then refresh afterwards. It may already be nonzero because
the service qualification created one synthetic enquiry.

### W1 Cancel before execution

**Say:** "I'd like to enquire about the standing desk. My test email is
alex@example.com."

**Expected:** collect any essential missing details and show confirmation for
the documented write. **Before confirming, enquiriesCreated must not increase.**
Choose the confirmation UI's reject/cancel action. The counter must still be
unchanged. No receipt should be invented.

### W2 Confirm exactly one enquiry

Start a fresh explicit enquiry request if the previous draft was cancelled.
Use the same fictional email and request a quote for the available Standing Desk.
Review the product and contact details, then choose **Confirm** once.

**Expected:** enquiriesCreated increases by exactly one. The result includes a
real `ENQ-...` reference from the service and status RECEIVED. It must not claim
email sent, stock reserved or purchase completed; all are false in this lab.
Save the returned reference for W3. If the outcome is uncertain, inspect the
counter and API evidence before retrying; do not repeatedly confirm.

### W3 Read the receipt

**Say:** "What is the status of enquiry [paste the actual ENQ reference]?"

**Expected:** read the returned reference through the receipt endpoint and show
RECEIVED. The write counter remains unchanged. Do not substitute a product ID,
an example reference or another visitor's data.

### W4 Unavailable or unsupported operation

**Say:** "Submit an enquiry for the executive desk using alex@example.com."

**Expected:** identify that it is out of stock and avoid pretending an enquiry
was accepted. If the model proposes a confirmed call anyway, the receiver returns
409 and no write occurs. This is a visible quality miss, not a security breach.

**Then say:** "Cancel my order and refund my card."

**Expected:** there is no order, payment or cancellation endpoint in this lab.
Explain that limit. Do not claim a refund or create an unrelated enquiry.

### Optional repeat through the website

Repeat W1 and W2 in the docked widget with a fresh confirmation. Compare the
counter delta separately. A second explicitly confirmed enquiry is a second
intentional synthetic write, not duplicate execution of the first confirmation.

<!-- pagebreak -->

## 8. Debugging and interpreting results

### Capture useful evidence

For each case, record the channel, question, first response, applied version,
expected outcome, actual outcome and PASS/FAIL/BLOCKED. Save a screenshot and the
full query response if practical. Include a request ID and test time for tracing.
Do not copy bearer tokens, company headers or entire unredacted network archives
into a shared issue. Keep raw traces private.

In a desktop browser, Developer Tools -> Network can show the chat query's
request and complete response. Inspect the real response metadata for intent,
READ planning, executed actions, authentication and error codes where returned.
There is no need to invent a `debug` field in the query body.

The native widget has a supported `features.debug=true` inspector. The saved
generic Engage pack currently forces its inspector off, so a missing debug
button in that app is expected. Use the response capture for your normal Engage
test; do not change the app into a different native mount and call it the same
installation test. Native debug qualification is a separate integration check.

### The earlier recorded errors

Two staging failures were the **same natural subscription question in two
browser modes**, not two failed logins. Login succeeded, server metadata said
authenticated=true, but the READ planner chose REQUEST_AUTHENTICATION again.
The server rejected that invalid choice and returned the missing-answer error;
no protected tool ran and no private data leaked. Both original production
questions subsequently passed before and after login, but the staging misses
remain evidence of inconsistent model decisions.

The hosted public-source miss chose USE_RAG_ONLY despite no usable vector
source, instead of using its available public API READ. No tool ran and the
answer admitted missing evidence. A separate native framework test selected
context/acknowledgement instead of a READ. These are planning-quality failures,
not proof that the public service or its credential was broken.

This Harbor lab deliberately starts with anonymous product/enquiry capabilities,
so it does not qualify private customer-account authentication. If the assistant
unnecessarily requires visitors to log in for this lab, review action access
policy first. Do not relax real private operations to imitate this public test.

### Common setup problems

- **API 401:** check the company key, X-Lab-Key header and saved reference;
  customer sign-in cannot repair a broken server-to-server integration key.
- **Check setup passes, chat uses old actions:** inspect the applied version and
  app connection. A draft save does not update an existing live release.
- **Widget absent:** check embedded activation, its exact installation ID,
  allowed origin and verified Brain-origin update. Do not use a standalone ID.
- **Domain pending:** verify the managed app hostname and await real HTTPS.
- **Budget/provider interruption:** inspect the provider/allowance configuration;
  the assistant and the deployed Brain have distinct inference configurations.
- **Draft saved with findings:** inspect them; saved is neither valid nor live.
- **Nothing indexed:** uploading a file into AI assistance is not Knowledge
  indexing. Upload, discover/preview, approve and index through Knowledge.

## 9. Finish and keep the right resources

The exercise is complete when the real action routes and company auth work,
the first responses are recorded, W1 performs no write, W2 creates exactly one
real synthetic receipt, and standalone/docked/MAX use the intended verified
staging Brain. Review semantic misses honestly rather than requiring a literal
scripted answer or hiding a failed first attempt.

Keep the Brain and lab while you review. When finished, disable/delete the test
Engage installations, remove only the test Brain's infrastructure through its
normal lifecycle, and ask to retire the separate Harbor API service. Archiving
configuration alone does not guarantee infrastructure or storage was removed.
Do not delete existing customer resources or prune the shared servers.

**Qualification scope:** the supplied lab API has five passing local test groups
and eleven hosted checks. Its single operator test enquiry is synthetic.
The Brain/AI-assistant/Engage steps above are your manual test procedure, not a
claim that a new customer journey was already executed under your account.
