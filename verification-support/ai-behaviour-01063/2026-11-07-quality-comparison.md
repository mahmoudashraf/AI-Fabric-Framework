# AI Behaviour: Hosted Conversation Comparison

Manual assessment of real Northfield anonymous canonical-message requests,
using the existing executor / landing configuration. No mocked responses,
lexical relevance gates, semantic answer rewriting or hidden retries.

The provider/model remained OpenAI `gpt-5.4-mini-2026-03-17`. The new canary
combines framework 0.8.46 with explicit Consultative / Advisory / Balanced /
Contextual instructions. This is a practical before/after configuration sample,
not a controlled attribution experiment or a guarantee of model correctness.

## Same Three Turns

1. **Stock:** Find electric cars under GBP 35,000; give a short comparison.
   Both versions returned the Morrow C2 at GBP 22,750 / 1,980 miles and the Aster
   E1 at GBP 31,950 / 4,850 miles. New staging and production retained those facts.
2. **Mixed follow-up:** Of those, which has lower mileage? Also explain general
   weekly motorway-driving considerations, separating facts from advice.
   The old baseline returned a compound clarification asking for an internal
   knowledge domain, with an unhelpful completed-search sibling. The new staging
   and production answers identified the Morrow's lower mileage, provided general
   EV-driving considerations, and disclosed missing battery/charging/equipment
   evidence. This is a partial rather than an unqualified pass: hedged suitability
   reasoning still leans on marketing names or body style without specifications.
3. **Policy follow-up:** Explain delivery policy for the lower-mileage car and
   identify unconfirmed details. New staging and production correctly described
   the indexed 25-mile delivery area, three working days after payment/preparation,
   and photo-ID requirement. They left actual address eligibility, dates, fees,
   payment and preparation unconfirmed rather than claiming a booking or promise.

Baseline: **two pass / one fail**. New staging and production: **two pass / one
partial** under this manual assessment. All new requests returned successful
information responses; no business write was confirmed or executed.

## Recommendations And Limits

The actual public widget displayed a canonical suggestion as a native button.
Clicking its benign stock query used the same conversation's ordinary message
route and completed another read-only search. Transport/click behaviour passed;
relevance is not solved by that transport change. Some recommendations repeat
the question because current recommendations can originate before final READ
evidence. No lexical deduplication, phrase trimming or automatic search-all was
added. A future result-aware recommendation improvement remains separate work.

The profile defaults remain Professional / Grounded / Balanced / Off. This
Advisory canary does not silently update all existing templates or Support
deployments. Wider advice does not supply internet access or change business
evidence, permissions, confirmation or provider sampling settings.
