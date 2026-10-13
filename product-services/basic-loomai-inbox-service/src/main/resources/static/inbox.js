const state = { key: sessionStorage.getItem("loomai-inbox-key") || "", operator: null, queue: [], callbacks: [], selected: null, filter: "" };
const $ = (id) => document.getElementById(id);
const requestId = () => crypto.randomUUID();

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", "X-LoomAI-Operator-Key": state.key, ...(options.headers || {}) },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || `Request failed (${response.status})`);
  return data;
}

async function login(key) {
  state.key = key;
  state.operator = await api("/api/operator/me");
  sessionStorage.setItem("loomai-inbox-key", key);
  $("operator-name").textContent = state.operator.displayName;
  $("login").hidden = true;
  $("app").hidden = false;
  await refresh();
}

async function refresh() {
  const query = state.filter ? `?status=${encodeURIComponent(state.filter)}` : "";
  [state.queue, state.callbacks] = await Promise.all([
    api(`/api/operator/conversations${query}`),
    api("/api/operator/operations/callbacks?limit=100"),
  ]);
  renderQueue();
  renderDeliveryIssues();
  if (state.selected) await selectConversation(state.selected.conversation.handoverId);
}

function renderDeliveryIssues() {
  const dead = state.callbacks.filter((callback) => callback.retryEligible);
  $("delivery-count").textContent = String(dead.length);
  const root = $("delivery-issues");
  root.textContent = "";
  if (!dead.length) {
    const empty = document.createElement("p");
    empty.className = "muted delivery-empty";
    empty.textContent = "No callback delivery issues.";
    root.append(empty);
    return;
  }
  dead.forEach((callback) => {
    const row = document.createElement("div");
    row.className = "delivery-item";
    const label = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = callback.eventType.replaceAll("_", " ");
    const meta = document.createElement("span");
    meta.textContent = `${callback.failureCode || "Delivery failed"} · ${callback.attemptCount} attempts`;
    label.append(title, meta);
    const retry = document.createElement("button");
    retry.className = "secondary";
    retry.textContent = "Retry";
    retry.onclick = async () => {
      retry.disabled = true;
      try {
        const result = await api(`/api/operator/operations/callbacks/${encodeURIComponent(callback.callbackId)}/retry`, {
          method: "POST",
          body: JSON.stringify({ idempotencyKey: requestId() }),
        });
        state.callbacks = result.callbacks || [];
        renderDeliveryIssues();
      } catch (error) {
        $("action-error").textContent = error.message;
        retry.disabled = false;
      }
    };
    row.append(label, retry);
    root.append(row);
  });
}

function renderQueue() {
  const root = $("queue");
  root.textContent = "";
  if (!state.queue.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.style.padding = "18px";
    empty.textContent = "No conversations in this view.";
    root.append(empty);
    return;
  }
  state.queue.forEach((item) => {
    const button = document.createElement("button");
    button.className = `queue-item${state.selected?.conversation.handoverId === item.handoverId ? " selected" : ""}`;
    button.innerHTML = `<span class="queue-row"><strong></strong><span class="status"></span></span><span class="queue-meta"></span>`;
    button.querySelector("strong").textContent = item.safeCustomerLabel;
    button.querySelector(".status").textContent = item.status.replaceAll("_", " ");
    button.querySelector(".queue-meta").textContent = `${item.unreadCount} unread · ${new Date(item.updatedAt).toLocaleString()}`;
    button.onclick = () => selectConversation(item.handoverId);
    root.append(button);
  });
}

async function selectConversation(handoverId) {
  state.selected = await api(`/api/operator/conversations/${encodeURIComponent(handoverId)}`);
  $("empty").hidden = true;
  $("conversation").hidden = false;
  const conversation = state.selected.conversation;
  $("conversation-label").textContent = conversation.externalConversationRef;
  $("conversation-title").textContent = conversation.safeCustomerLabel;
  $("conversation-status").textContent = conversation.status.replaceAll("_", " ");
  const intake = Object.entries(state.selected.consentedIntake || {});
  $("intake").hidden = !intake.length;
  $("intake").textContent = intake.map(([key, value]) => `${key}: ${value}`).join(" · ");
  const messages = $("messages");
  messages.textContent = "";
  state.selected.messages.forEach((message) => {
    const item = document.createElement("article");
    item.className = `message ${message.participantRole.toLowerCase()}`;
    const meta = document.createElement("div");
    meta.className = "message-meta";
    const author = document.createElement("span");
    author.textContent = message.safeActorDisplayName || message.participantRole;
    const time = document.createElement("time");
    time.textContent = new Date(message.createdAt).toLocaleString();
    meta.append(author, time);
    const content = document.createElement("div");
    content.textContent = message.content;
    item.append(meta, content);
    messages.append(item);
  });
  messages.scrollTop = messages.scrollHeight;
  const ownActive = conversation.status === "ACTIVE" && conversation.claimedBy === state.operator.operatorId;
  $("claim").hidden = !["WAITING", "CLAIM_PENDING"].includes(conversation.status);
  $("return-ai").hidden = !ownActive;
  $("close").hidden = !ownActive;
  $("reply").disabled = !ownActive;
  $("reply-form").querySelector("button").disabled = !ownActive;
  renderQueue();
}

async function command(path, body) {
  $("action-error").textContent = "";
  try {
    const result = await api(path, { method: "POST", body: JSON.stringify(body) });
    state.selected = result.conversation;
    await refresh();
  } catch (error) {
    $("action-error").textContent = error.message;
  }
}

$("login-form").onsubmit = async (event) => {
  event.preventDefault();
  $("login-error").textContent = "";
  try { await login($("operator-key").value); } catch (error) { $("login-error").textContent = error.message; }
};
$("logout").onclick = () => { sessionStorage.removeItem("loomai-inbox-key"); location.reload(); };
$("refresh").onclick = refresh;
document.querySelectorAll(".filters button").forEach((button) => {
  button.onclick = async () => {
    document.querySelectorAll(".filters button").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    state.filter = button.dataset.status;
    await refresh();
  };
});
$("claim").onclick = () => command(`/api/operator/conversations/${state.selected.conversation.handoverId}/claim`, { idempotencyKey: requestId() });
$("return-ai").onclick = () => command(`/api/operator/conversations/${state.selected.conversation.handoverId}/return-to-ai`, { idempotencyKey: requestId() });
$("close").onclick = () => command(`/api/operator/conversations/${state.selected.conversation.handoverId}/close`, { idempotencyKey: requestId() });
$("reply-form").onsubmit = async (event) => {
  event.preventDefault();
  const text = $("reply").value.trim();
  if (!text) return;
  await command(`/api/operator/conversations/${state.selected.conversation.handoverId}/messages`, { text, idempotencyKey: requestId() });
  $("reply").value = "";
};

if (state.key) login(state.key).catch(() => sessionStorage.removeItem("loomai-inbox-key"));
setInterval(() => { if (state.operator) refresh().catch(() => {}); }, 4000);
