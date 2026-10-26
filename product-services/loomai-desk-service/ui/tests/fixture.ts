import type { Page, Route } from "@playwright/test";
import type {
  DecisionResult,
  ReviewDecisionFailure,
  ReviewDetail,
  ReviewOutcome,
  ReviewTask,
} from "../src/types";

const stamp = new Date().toISOString();
const ago = (minutes: number) =>
  new Date(Date.now() - minutes * 60_000).toISOString();
export function fixtureState() {
  const reviews: ReviewTask[] = [
    {
      taskId: "task-1",
      type: "OPERATIONAL_REVIEW",
      title: "Confirm a visitor appointment",
      summary:
        "A visitor has requested a confirmed appointment. Review the proposed time and contact information before this action continues.",
      status: "WAITING_FOR_REVIEW",
      version: 3,
      allowedDecisions: ["APPROVE", "REJECT"],
      policyId: { name: "Appointment review", version: "1" },
      createdAt: ago(5),
      expiresAt: new Date(Date.now() + 3600000).toISOString(),
    },
    {
      taskId: "task-2",
      type: "OPERATIONAL_REVIEW",
      title: "Update a service request",
      summary: "Review a requested update to an existing service request.",
      status: "WAITING_FOR_REVIEW",
      version: 1,
      allowedDecisions: ["APPROVE", "REJECT"],
      policyId: { name: "Service review", version: "1" },
      createdAt: ago(15),
      expiresAt: new Date(Date.now() + 3600000).toISOString(),
    },
  ];
  return {
    session: {
      authenticated: true,
      registered: true,
      emailVerified: true,
      csrfToken: "fixture-csrf",
      csrfHeaderName: "X-CSRF-Test",
      operator: {
        id: "agent-1",
        displayName: "Morgan Reed",
        email: "morgan@example.test",
      },
      bindings: [
        {
          bindingId: "studio-prod",
          customerId: "customer-a",
          deploymentId: "studio",
          targetProfileId: "production",
          deploymentName: "Juniper Studio",
          environment: "production",
          roles: ["OWNER", "SUPPORT", "REVIEWER"],
        },
        {
          bindingId: "support-stage",
          customerId: "customer-b",
          deploymentId: "support",
          targetProfileId: "staging",
          deploymentName: "Cedar Support",
          environment: "staging",
          roles: ["SUPPORT"],
        },
      ],
    },
    conversations: [
      {
        handoverId: "hand-1",
        externalConversationRef: "conversation-82f1",
        status: "WAITING",
        safeCustomerLabel: "Eve Lin",
        claimedBy: null as string | null,
        claimedDisplayName: null as string | null,
        unreadCount: 2,
        createdAt: ago(17),
        updatedAt: ago(1),
      },
      {
        handoverId: "hand-2",
        externalConversationRef: "conversation-34bd",
        status: "ACTIVE",
        safeCustomerLabel: "Alex Rivera",
        claimedBy: "agent-2",
        claimedDisplayName: "Sam Taylor",
        unreadCount: 0,
        createdAt: ago(45),
        updatedAt: ago(8),
      },
      {
        handoverId: "hand-3",
        externalConversationRef: "conversation-01da",
        status: "RETURNED_TO_AI",
        safeCustomerLabel: "Robin Bell",
        claimedBy: "agent-1",
        claimedDisplayName: "Morgan Reed",
        unreadCount: 0,
        createdAt: ago(80),
        updatedAt: ago(23),
      },
      {
        handoverId: "hand-4",
        externalConversationRef: "conversation-18fc",
        status: "CLOSED",
        safeCustomerLabel: "Jamie Brooks",
        claimedBy: "agent-2",
        claimedDisplayName: "Sam Taylor",
        unreadCount: 0,
        createdAt: ago(120),
        updatedAt: ago(60),
      },
    ],
    messages: [
      {
        messageId: "m-1",
        participantRole: "USER",
        actorRef: "visitor",
        safeActorDisplayName: "Eve Lin",
        content:
          "Hi, I'm planning a visit next week. Could you help me understand the available options?",
        attachmentRefs: [],
        createdAt: ago(17),
      },
      {
        messageId: "m-2",
        participantRole: "ASSISTANT",
        actorRef: "ai",
        safeActorDisplayName: "AI assistant",
        content:
          "Of course. I can help you explore the available options and explain the current booking policy. What would make the visit most useful for you?",
        attachmentRefs: [],
        createdAt: ago(16),
      },
      {
        messageId: "m-3",
        participantRole: "USER",
        actorRef: "visitor",
        safeActorDisplayName: "Eve Lin",
        content:
          "I'd like to discuss accessibility arrangements with someone before booking. Could you connect me with a person?",
        attachmentRefs: [],
        createdAt: ago(3),
      },
      {
        messageId: "m-4",
        participantRole: "ASSISTANT",
        actorRef: "ai",
        safeActorDisplayName: "AI assistant",
        content:
          "Absolutely. I've shared the context with the support team so you won't need to start again.",
        attachmentRefs: [],
        createdAt: ago(2),
      },
      {
        messageId: "m-5",
        participantRole: "SYSTEM",
        actorRef: "system",
        safeActorDisplayName: "System",
        content: "Human support requested. Waiting for an agent.",
        attachmentRefs: [],
        createdAt: ago(1),
      },
    ],
    agents: [
      {
        agentId: "assignment-1",
        agentReference: "agent-morgan",
        displayName: "Morgan Reed",
        email: "morgan@example.test",
        roles: ["SUPPORT", "REVIEWER"],
        createdAt: ago(720),
      },
      {
        agentId: "assignment-2",
        agentReference: "agent-sam",
        displayName: "Sam Taylor",
        email: "sam@example.test",
        roles: ["SUPPORT"],
        createdAt: ago(1440),
      },
    ],
    reviews,
    reviewDetails: {} as Record<string, Partial<ReviewDetail>>,
    decisionFailure: null as ReviewDecisionFailure | null,
    decisionFailureOutcome: null as ReviewOutcome | null,
    decisionFailureStatus: 409,
    decisionSuccessorTaskId: null as string | null,
    decisionOutcomes: {
      "task-1": {
        actionName: "confirm_appointment",
        message: "The application returned appointment reference REQ-1042.",
        data: { reference: "REQ-1042", appointmentTime: "14:30" },
      },
      "task-2": null,
    } as Record<string, ReviewOutcome | null>,
    calls: [] as {
      path: string;
      method: string;
      body: Record<string, unknown> | null;
      csrf: string | undefined;
    }[],
    failReply: false,
    callbacks: [] as {
      callbackId: string;
      handoverId: string;
      eventType: string;
      status: string;
      attemptCount: number;
      failureCode?: string;
      createdAt: string;
      updatedAt: string;
      retryEligible: boolean;
    }[],
    failDetail: false,
    expire: false,
  };
}
export type Fixture = ReturnType<typeof fixtureState>;
export async function mockDesk(page: Page, state = fixtureState()) {
  const json = (route: Route, data: unknown, status = 200) =>
    route.fulfill({ status, json: data });
  await page.route("**/api/desk/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const body = route.request().postDataJSON() as Record<
      string,
      unknown
    > | null;
    state.calls.push({
      path: url.pathname,
      method,
      body,
      csrf: route.request().headers()["x-csrf-test"],
    });
    if (url.pathname.endsWith("/config"))
      return json(route, {
        registrationEnabled: true,
        oidcEnabled: true,
        supabaseUrl: "https://identity.example.test",
        supabasePublishableKey: "public-fixture-key",
        loginUrl: "/oauth2/authorization/supabase",
      });
    if (url.pathname.endsWith("/session"))
      return json(
        route,
        state.expire ? { authenticated: false, bindings: [] } : state.session,
      );
    if (state.expire)
      return json(route, { message: "Your session has expired." }, 401);
    if (url.pathname.endsWith("/agents/register")) {
      state.session.registered = true;
      return json(route, { agentReference: "agent-morgan" });
    }
    const match = url.pathname.match(/\/bindings\/([^/]+)\/(.+)/);
    if (!match) return json(route, { message: "Unknown fixture path" }, 404);
    const [, binding, resource] = match;
    if (binding === "support-stage" && resource === "conversations")
      return json(route, [
        {
          ...state.conversations[0],
          handoverId: "cedar-hand",
          safeCustomerLabel: "Cedar visitor",
        },
      ]);
    if (resource === "conversations") return json(route, state.conversations);
    if (resource === "operations/callbacks")
      return json(route, state.callbacks);
    if (resource?.startsWith("operations/callbacks/")) {
      const callback = state.callbacks.find(
        (item) => item.callbackId === resource.split("/")[2],
      );
      if (callback) {
        callback.status = "PENDING";
        callback.retryEligible = false;
      }
      return json(route, {
        accepted: true,
        outcome: "RETRY_QUEUED",
        callbacks: state.callbacks,
      });
    }
    if (resource?.startsWith("conversations/")) {
      const [, id, operation] = resource.split("/");
      const conversation = state.conversations.find(
        (item) => item.handoverId === id,
      );
      if (!conversation)
        return json(route, { message: "Conversation not found" }, 404);
      const detail = () => ({
        conversation: { ...conversation },
        consentedIntake: {
          name: conversation.safeCustomerLabel,
          email: "eve@example.test",
          reason: "Visit arrangements",
        },
        messages: state.messages,
      });
      if (method === "GET")
        return state.failDetail
          ? json(
              route,
              { message: "The transcript service is temporarily unavailable." },
              503,
            )
          : json(route, detail());
      if (operation === "claim") {
        conversation.status = "ACTIVE";
        conversation.claimedBy = "agent-1";
        conversation.claimedDisplayName = "Morgan Reed";
        conversation.unreadCount = 0;
      }
      if (operation === "messages") {
        if (state.failReply)
          return json(
            route,
            {
              message: "Reply could not be accepted. Your draft is preserved.",
            },
            503,
          );
        state.messages.push({
          messageId: crypto.randomUUID(),
          participantRole: "OPERATOR",
          actorRef: "agent-1",
          safeActorDisplayName: "Morgan Reed",
          content: String(body?.text),
          attachmentRefs: [],
          createdAt: stamp,
        });
      }
      if (operation === "return-to-ai") conversation.status = "RETURNED_TO_AI";
      if (operation === "close") conversation.status = "CLOSED";
      return json(route, {
        accepted: true,
        outcome:
          operation === "messages"
            ? "REPLY_ACCEPTED_DELIVERY_PENDING"
            : "ACCEPTED",
        conversation: detail(),
      });
    }
    if (resource === "agents" && method === "GET")
      return json(route, state.agents);
    if (resource === "agents" && method === "POST") {
      const existing = state.agents.find(
        (item) =>
          body?.agentReference === item.agentReference ||
          body?.email === item.email,
      );
      if (existing) existing.roles = body?.roles as string[];
      else
        state.agents.push({
          agentId: "assignment-new",
          agentReference: "agent-new",
          displayName: "New agent",
          email: String(body?.email ?? body?.agentReference),
          roles: body?.roles as string[],
          createdAt: stamp,
        });
      return json(route, { assigned: true });
    }
    if (resource?.startsWith("agents/") && method === "DELETE") {
      state.agents = state.agents.filter(
        (item) => item.agentId !== resource.split("/")[1],
      );
      return route.fulfill({ status: 204 });
    }
    if (resource === "reviews") return json(route, state.reviews);
    if (resource?.startsWith("reviews/")) {
      const [, id, operation] = resource.split("/");
      const task = state.reviews.find((item) => item.taskId === id);
      if (!task) return json(route, { message: "Task not found" }, 404);
      if (operation === "decisions") {
        if (state.decisionFailure) {
          const result: DecisionResult = {
            task,
            outcome: state.decisionFailureOutcome,
            successorTaskId: state.decisionSuccessorTaskId,
            failure: state.decisionFailure,
          };
          return json(route, result, state.decisionFailureStatus);
        }
        task.status = body?.decision === "APPROVE" ? "APPROVED" : "REJECTED";
        task.version++;
        const result: DecisionResult = {
          task,
          outcome:
            body?.decision === "APPROVE"
              ? (state.decisionOutcomes[task.taskId] ?? null)
              : null,
          successorTaskId: state.decisionSuccessorTaskId,
          failure: null,
        };
        return json(route, result);
      }
      const detail: ReviewDetail = {
        task,
        requestedInformation: null,
        suppliedInformation: {
          visitor: "Eve Lin",
          appointment: { day: "Tuesday", time: "14:30" },
          notes: "Accessibility arrangements requested",
        },
        message: null,
        outcome: null,
        successorTaskId: null,
        failureReason: null,
        ...state.reviewDetails[task.taskId],
      };
      return json(route, detail);
    }
    return json(route, { message: `Unknown fixture path: ${resource}` }, 404);
  });
  await page.route("**/logout", (route) => route.fulfill({ status: 204 }));
  return state;
}
