-- Greenfield cutover authorized for disposable Console test tickets.
-- Desk TicketProvider is the sole ticket store. Document connections and AI state are untouched.
DROP TABLE platform_support_desk_operations;
DROP TABLE platform_support_case_events;
DROP TABLE platform_support_case_episodes;
DROP TABLE platform_support_cases;
