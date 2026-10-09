const path = require("path");
const pptxgen = require("pptxgenjs");
const QRCode = require("qrcode");

const pptx = new pptxgen();
pptx.layout = "LAYOUT_WIDE";
pptx.author = "Loom AI Labs";
pptx.company = "Loom AI Labs";
pptx.subject = "Autotrader partnership discussion";
pptx.title = "LoomAI Platform for Autotrader Connect";
pptx.lang = "en-GB";
pptx.theme = {
  headFontFace: "Arial",
  bodyFontFace: "Arial",
  lang: "en-GB",
};
pptx.defineSlideMaster({
  title: "LOOMAI",
  background: { color: "F7F9FE" },
  objects: [
    {
      line: {
        x: 0,
        y: 7.18,
        w: 13.333,
        h: 0,
        line: { color: "DCE5F6", width: 0.6 },
      },
    },
  ],
});

const C = {
  navy: "071B4D",
  blue: "1265F3",
  cyan: "05BCE7",
  purple: "7443F5",
  green: "0F9F74",
  greenPale: "E9F8F1",
  amber: "B86A00",
  amberPale: "FFF4DD",
  red: "B33D4B",
  ink: "102448",
  text: "30466E",
  muted: "667797",
  border: "C9D7EF",
  panel: "FFFFFF",
  panelBlue: "EEF4FF",
  panelCyan: "EAFBFE",
  panelPurple: "F2EEFF",
  pale: "F7F9FE",
  white: "FFFFFF",
};

const ROOT = path.resolve(__dirname, "../../..");
const OUT = path.join(ROOT, "Final_Documentation/Presentations/AUTOTRADER_LOOMAI_PARTNERSHIP_6_SLIDE_DECK_2026-10-06.pptx");
const LOGO = path.join(ROOT, "Platfrom/loomai-site/public/assets/reference/loom-wordmark.png");
const DEMO = path.join(ROOT, "Final_Documentation/Presentations/assets/autotrader-dealership-live-demo-2026-10-05.png");
const WORKSPACE = path.join(ROOT, "Final_Documentation/Presentations/assets/dealership-ai-workspace-max-mode-2026-10-06.png");
const ENABLEMENT_PRODUCTS = path.join(ROOT, "Final_Documentation/Presentations/assets/loomai-ai-enablement-products-autotrader-2026-10-06.png");
const ONLY_SLIDE = Number(process.env.ONLY_SLIDE || 0);

function addBrand(slide, slideNo, section) {
  slide.addImage({ path: LOGO, x: 0.42, y: 0.24, w: 1.76, h: 0.43 });
  slide.addText(section.toUpperCase(), {
    x: 9.1,
    y: 0.34,
    w: 3.55,
    h: 0.18,
    fontFace: "Arial",
    fontSize: 8,
    bold: true,
    color: C.blue,
    charSpacing: 1.3,
    align: "right",
    margin: 0,
  });
  slide.addText("CONFIDENTIAL PARTNERSHIP DISCUSSION", {
    x: 0.42,
    y: 7.22,
    w: 3.0,
    h: 0.13,
    fontSize: 7,
    bold: true,
    color: C.muted,
    charSpacing: 0.8,
    margin: 0,
  });
  slide.addText(String(slideNo).padStart(2, "0"), {
    x: 12.35,
    y: 7.2,
    w: 0.45,
    h: 0.15,
    fontSize: 8,
    color: C.muted,
    align: "right",
    margin: 0,
  });
}

function addTitle(slide, eyebrow, title, subtitle) {
  slide.addText(eyebrow.toUpperCase(), {
    x: 0.55,
    y: 0.86,
    w: 5.6,
    h: 0.22,
    fontSize: 9,
    bold: true,
    color: C.cyan,
    charSpacing: 1.5,
    margin: 0,
  });
  slide.addText(title, {
    x: 0.55,
    y: 1.1,
    w: 12.0,
    h: 0.68,
    fontFace: "Arial",
    fontSize: 28,
    bold: true,
    color: C.navy,
    breakLine: false,
    margin: 0,
    fit: "shrink",
  });
  if (subtitle) {
    slide.addText(subtitle, {
      x: 0.57,
      y: 1.78,
      w: 11.7,
      h: 0.42,
      fontSize: 13,
      color: C.text,
      margin: 0,
      breakLine: false,
      fit: "shrink",
    });
  }
}

function addPill(slide, text, x, y, w, fill, colour = C.ink) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h: 0.32,
    rectRadius: 0.06,
    fill: { color: fill },
    line: { color: fill },
  });
  slide.addText(text, {
    x: x + 0.08,
    y: y + 0.075,
    w: w - 0.16,
    h: 0.12,
    fontSize: 8,
    bold: true,
    color: colour,
    align: "center",
    margin: 0,
    fit: "shrink",
  });
}

function addArrow(slide, x, y, w = 0.38, colour = C.blue) {
  slide.addShape(pptx.ShapeType.chevron, {
    x,
    y,
    w,
    h: 0.34,
    fill: { color: colour },
    line: { color: colour },
  });
}

function addCard(slide, { x, y, w, h, title, subtitle, accent = C.blue, fill = C.panel, number }) {
  const compact = h < 0.9;
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y,
    w,
    h,
    rectRadius: 0.08,
    fill: { color: fill },
    line: { color: C.border, width: 0.9 },
    shadow: { type: "outer", color: "A9B9D4", opacity: 0.12, blur: 1.2, angle: 45, distance: 0.7 },
  });
  slide.addShape(pptx.ShapeType.rect, {
    x,
    y,
    w: 0.06,
    h,
    fill: { color: accent },
    line: { color: accent },
  });
  if (number !== undefined) {
    slide.addShape(pptx.ShapeType.ellipse, {
      x: x + 0.18,
      y: y + 0.18,
      w: 0.38,
      h: 0.38,
      fill: { color: accent },
      line: { color: accent },
    });
    slide.addText(String(number), {
      x: x + 0.18,
      y: y + 0.285,
      w: 0.38,
      h: 0.1,
      fontSize: 9,
      bold: true,
      color: C.white,
      align: "center",
      margin: 0,
    });
  }
  const tx = number !== undefined ? x + 0.68 : x + 0.24;
  slide.addText(title, {
    x: tx,
    y: y + (compact ? 0.12 : 0.19),
    w: w - (tx - x) - 0.18,
    h: compact ? 0.2 : 0.27,
    fontSize: compact ? 11.2 : 12.2,
    bold: true,
    color: C.ink,
    margin: 0,
    fit: "shrink",
  });
  slide.addText(subtitle, {
    x: tx,
    y: y + (compact ? 0.39 : 0.53),
    w: w - (tx - x) - 0.18,
    h: compact ? 0.15 : h - 0.65,
    fontSize: compact ? 8.5 : 9.2,
    color: C.text,
    margin: 0,
    valign: "top",
    breakLine: false,
    fit: "shrink",
  });
}

function addBulletList(slide, items, x, y, w, h, options = {}) {
  const runs = [];
  items.forEach((item, index) => {
    runs.push({
      text: item,
      options: {
        bullet: { indent: 13 },
        hanging: 3,
        breakLine: index < items.length - 1,
      },
    });
  });
  slide.addText(runs, {
    x,
    y,
    w,
    h,
    fontSize: options.fontSize || 10.2,
    color: options.color || C.text,
    breakLine: false,
    paraSpaceAfterPt: options.paraSpaceAfterPt || 8,
    margin: 0.04,
    valign: "top",
    fit: "shrink",
  });
}

function addSource(slide, text) {
  slide.addText(text, {
    x: 4.0,
    y: 7.21,
    w: 7.9,
    h: 0.15,
    fontSize: 6.3,
    color: C.muted,
    align: "right",
    margin: 0,
    fit: "shrink",
  });
}

// Slide 1
if (!ONLY_SLIDE || ONLY_SLIDE === 1) {
  const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 1, "LoomAI Platform x Autotrader Connect");
  addTitle(
    slide,
    "AI enablement, not another chatbot",
    "One AI enablement layer. Four product behaviours.",
    "LoomAI supports conversational, event-driven and agentic products inside a governed, dealer-isolated deployment."
  );

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.55,
    y: 2.37,
    w: 8.02,
    h: 4.38,
    rectRadius: 0.06,
    fill: { color: C.white },
    line: { color: C.border, width: 0.8 },
    shadow: { type: "outer", color: "9BADCC", opacity: 0.12, blur: 1.2, angle: 45, distance: 0.8 },
  });
  slide.addImage({ path: ENABLEMENT_PRODUCTS, x: 0.64, y: 2.46, w: 7.84, h: 4.17 });
  addPill(slide, "AUTHORISED DATA", 0.83, 2.63, 1.28, C.panelCyan, C.cyan);
  addPill(slide, "GOVERNED ENABLEMENT", 3.0, 2.63, 1.73, C.panelPurple, C.purple);
  addPill(slide, "DEALER OUTCOMES", 6.7, 2.63, 1.42, C.greenPale, C.green);

  slide.addText("LOOMAI PRODUCT BEHAVIOURS", {
    x: 8.83,
    y: 2.4,
    w: 3.92,
    h: 0.15,
    fontSize: 8.2,
    bold: true,
    color: C.blue,
    charSpacing: 1.15,
    margin: 0,
  });

  const productBehaviours = [
    ["Conversational AI", "User-led dialogue, grounded answers and governed actions.", C.cyan, C.panelCyan],
    ["Smart Brain", "Events trigger fixed plans, task queues and durable analysis.", C.blue, C.panelBlue],
    ["Multi-agent teams", "A conversation manager coordinates specialist workers.", C.purple, C.panelPurple],
    ["Sequential + parallel plans", "Declarative workers run in order or concurrently.", C.green, C.greenPale],
  ];
  productBehaviours.forEach(([title, subtitle, accent, fill], index) => {
    const y = 2.68 + index * 0.91;
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 8.82,
      y,
      w: 3.93,
      h: 0.78,
      rectRadius: 0.05,
      fill: { color: fill },
      line: { color: C.border, width: 0.75 },
    });
    slide.addShape(pptx.ShapeType.ellipse, {
      x: 9.02,
      y: y + 0.17,
      w: 0.42,
      h: 0.42,
      fill: { color: accent },
      line: { color: accent },
    });
    slide.addText(String(index + 1), {
      x: 9.02,
      y: y + 0.285,
      w: 0.42,
      h: 0.1,
      fontSize: 8.5,
      bold: true,
      color: C.white,
      align: "center",
      margin: 0,
    });
    slide.addText(title, {
      x: 9.58,
      y: y + 0.13,
      w: 2.92,
      h: 0.18,
      fontSize: 11.2,
      bold: true,
      color: C.ink,
      margin: 0,
      fit: "shrink",
    });
    slide.addText(subtitle, {
      x: 9.58,
      y: y + 0.4,
      w: 2.92,
      h: 0.2,
      fontSize: 7.8,
      color: C.text,
      margin: 0,
      fit: "shrink",
    });
  });

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 8.82,
    y: 6.37,
    w: 3.93,
    h: 0.38,
    rectRadius: 0.04,
    fill: { color: C.navy },
    line: { color: C.navy },
  });
  slide.addText("Templates + plugins + policies -> one isolated dealer deployment", {
    x: 9.0,
    y: 6.5,
    w: 3.56,
    h: 0.11,
    fontSize: 7.6,
    bold: true,
    color: C.white,
    align: "center",
    margin: 0,
    fit: "shrink",
  });
  slide.addNotes("Open by correcting the category assumption: LoomAI is not a chatbot product. It is an AI enablement and deployment layer that can productise four behaviours from the same governed primitives. Conversational AI handles user-led journeys. Smart Brain runs fixed analysis or operational plans from system events and queues. Multi-agent deployments coordinate genuinely distinct specialists through a conversation manager. Declarative plans execute workers sequentially or in parallel. For Autotrader, authorised data can power any of these behaviours while every dealer remains isolated and governed.");
}

// Slide 2
if (!ONLY_SLIDE || ONLY_SLIDE === 2) {
  const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 2, "The buyer experience");
  addTitle(
    slide,
    "From intent to action",
    "One journey, from a vague need to a qualified handoff.",
    "The buyer can search naturally, understand the trade-offs and move forward without repeating the conversation."
  );

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 6.75,
    y: 2.34,
    w: 5.98,
    h: 3.74,
    rectRadius: 0.06,
    fill: { color: C.white },
    line: { color: C.border, width: 0.9 },
    shadow: { type: "outer", color: "9BADCC", opacity: 0.16, blur: 1.5, angle: 45, distance: 1 },
  });
  slide.addImage({ path: DEMO, x: 6.89, y: 2.48, w: 5.7, h: 3.56 });
  addPill(slide, "LIVE DEMO", 11.23, 2.6, 1.02, C.greenPale, C.green);

  const steps = [
    ["Describe", "Everyday needs, budget, journey and preferences", C.cyan],
    ["Discover", "Semantic shortlist from this dealer's permitted stock", C.blue],
    ["Compare", "Consistent facts, evidence and visible trade-offs", C.purple],
    ["Verify", "Re-read current price, availability and key facts", C.green],
    ["Handoff", "Customer-approved context plus a durable receipt", C.amber],
  ];
  steps.forEach(([title, subtitle, accent], i) => {
    addCard(slide, {
      x: 0.58,
      y: 2.32 + i * 0.75,
      w: 5.75,
      h: 0.62,
      title,
      subtitle,
      accent,
      fill: C.white,
      number: i + 1,
    });
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.58,
    y: 6.35,
    w: 12.14,
    h: 0.45,
    rectRadius: 0.05,
    fill: { color: C.panelBlue },
    line: { color: C.border, width: 0.8 },
  });
  slide.addText("Customer surface", { x: 0.82, y: 6.49, w: 1.15, h: 0.12, fontSize: 8, bold: true, color: C.blue, margin: 0 });
  slide.addText("Docked chat for quick questions  |  Max workspace for search, comparison, sources and confirmed actions", {
    x: 2.0,
    y: 6.46,
    w: 8.82,
    h: 0.16,
    fontSize: 9.2,
    color: C.ink,
    margin: 0,
    fit: "shrink",
  });
  slide.addText("loomai.pro/demos/dealership-ai", {
    x: 10.75,
    y: 6.47,
    w: 1.68,
    h: 0.14,
    fontSize: 7.2,
    bold: true,
    color: C.purple,
    align: "right",
    margin: 0,
    fit: "shrink",
  });
  slide.addText("Demonstration inventory is fictional and clearly disclosed in the experience.", {
    x: 7.05,
    y: 6.08,
    w: 5.45,
    h: 0.16,
    fontSize: 7,
    italic: true,
    color: C.muted,
    align: "right",
    margin: 0,
  });
  slide.addNotes("Demo the docked composer first, then Max Mode. Emphasise that chat is only the surface: the differentiated value is dealer-scoped discovery, explicit evidence and a structured handoff. Do not imply the visible inventory is live Autotrader data.");
}

// Slide 3
if (!ONLY_SLIDE || ONLY_SLIDE === 3) {
  const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 3, "The AI-enabled workspace");
  addTitle(
    slide,
    "Beyond chat",
    "Transform the dealership website into an AI-enabled workspace.",
    "The assistant carries context, evidence and governed tools through the customer's journey."
  );

  const workspaceCapabilities = [
    {
      x: 0.58,
      y: 2.38,
      title: "Persistent context",
      subtitle: "Conversation, selected vehicles and attached pages persist across navigation.",
      accent: C.cyan,
      fill: C.panelCyan,
    },
    {
      x: 3.3,
      y: 2.38,
      title: "Adaptive tools",
      subtitle: "Browse stock or switch to vehicle-specific tools without losing either scope.",
      accent: C.blue,
      fill: C.panelBlue,
    },
    {
      x: 0.58,
      y: 4.02,
      title: "Visible evidence",
      subtitle: "Combine live provider facts, indexed stock, dealership documents and attachments.",
      accent: C.purple,
      fill: C.panelPurple,
    },
    {
      x: 3.3,
      y: 4.02,
      title: "Governed actions",
      subtitle: "Request a test drive, callback or handoff with confirmation and a durable receipt.",
      accent: C.green,
      fill: C.greenPale,
    },
  ];
  workspaceCapabilities.forEach((capability, index) => {
    addCard(slide, {
      ...capability,
      w: 2.5,
      h: 1.34,
      number: index + 1,
    });
  });

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 6.12,
    y: 2.36,
    w: 6.61,
    h: 3.66,
    rectRadius: 0.06,
    fill: { color: C.white },
    line: { color: C.border, width: 0.9 },
    shadow: { type: "outer", color: "9BADCC", opacity: 0.14, blur: 1.4, angle: 45, distance: 0.8 },
  });
  slide.addImage({ path: WORKSPACE, x: 6.25, y: 2.49, w: 6.35, h: 3.49 });
  slide.addShape(pptx.ShapeType.roundRect, {
    x: 6.86,
    y: 2.84,
    w: 4.95,
    h: 0.5,
    rectRadius: 0.04,
    fill: { color: C.white },
    line: { color: C.border, width: 0.6 },
  });
  slide.addText("Live vehicle facts + dealer policy evidence + governed actions", {
    x: 7.02,
    y: 3.035,
    w: 4.63,
    h: 0.11,
    fontSize: 7.7,
    bold: true,
    color: C.ink,
    align: "center",
    margin: 0,
    fit: "shrink",
  });
  addPill(slide, "MAX WORKSPACE", 10.95, 2.62, 1.35, C.panelPurple, C.purple);

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.58,
    y: 6.27,
    w: 12.14,
    h: 0.55,
    rectRadius: 0.05,
    fill: { color: C.navy },
    line: { color: C.navy },
  });
  const workspaceFlow = [
    "Dealer website",
    "Shared context",
    "AI workspace",
    "Live facts + policy evidence",
    "Customer-approved outcome",
  ];
  workspaceFlow.forEach((label, index) => {
    const x = 0.78 + index * 2.42;
    slide.addText(label, {
      x,
      y: 6.47,
      w: 1.95,
      h: 0.13,
      fontSize: 8.4,
      bold: true,
      color: index === 2 ? "7EE8FF" : C.white,
      align: "center",
      margin: 0,
      fit: "shrink",
    });
    if (index < workspaceFlow.length - 1) {
      addArrow(slide, x + 2.0, 6.38, 0.24, index === 1 ? C.cyan : C.blue);
    }
  });
  slide.addNotes("This is the experience transformation: AI is no longer a separate support bubble. It becomes a persistent workspace around the dealer's real pages, selected items, evidence and approved actions. Point out that the generic chat application accepts dealership-specific tools and presentations without embedding automotive logic in the generic component.");
}

// Slide 4
if (!ONLY_SLIDE || ONLY_SLIDE === 4) {
  const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 4, "Data freshness and grounded reasoning");
  addTitle(
    slide,
    "Data freshness and grounded reasoning",
    "Live data stays live. Indexed data stays useful.",
    "LoomAI separates discovery evidence from operational truth, then revalidates important facts before a decision."
  );

  const flowY = 2.62;
  const flow = [
    { x: 0.58, w: 2.05, title: "Autotrader Connect", subtitle: "Dealer-scoped baseline + stock events", accent: C.cyan, fill: C.panelCyan },
    { x: 3.03, w: 2.05, title: "DATA plugin", subtitle: "Authenticate, normalise, reconcile and delete", accent: C.purple, fill: C.panelPurple },
    { x: 5.48, w: 2.05, title: "Dealer projection", subtitle: "Typed current records with source identity", accent: C.blue, fill: C.panelBlue },
    { x: 7.93, w: 2.05, title: "Semantic index", subtitle: "Derived discovery evidence, rebuildable", accent: C.green, fill: C.greenPale },
    { x: 10.38, w: 2.35, title: "Grounded answer", subtitle: "Source, freshness and unsupported-field honesty", accent: C.amber, fill: C.amberPale },
  ];
  flow.forEach((c, i) => {
    addCard(slide, { ...c, y: flowY, h: 1.28 });
    if (i < flow.length - 1) addArrow(slide, c.x + c.w + 0.09, flowY + 0.46, 0.25, C.blue);
  });

  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.58,
    y: 4.26,
    w: 5.85,
    h: 1.34,
    rectRadius: 0.06,
    fill: { color: C.navy },
    line: { color: C.navy },
  });
  slide.addText("EVENT PATH", { x: 0.87, y: 4.52, w: 1.3, h: 0.13, fontSize: 8, bold: true, color: "7EE8FF", charSpacing: 1, margin: 0 });
  slide.addText("Stock change -> verified notification -> targeted stockId fetch -> upsert or delete -> indexing completion", {
    x: 0.87,
    y: 4.78,
    w: 5.15,
    h: 0.48,
    fontSize: 11,
    bold: true,
    color: C.white,
    margin: 0,
    fit: "shrink",
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x: 6.7,
    y: 4.26,
    w: 6.03,
    h: 1.34,
    rectRadius: 0.06,
    fill: { color: C.white },
    line: { color: C.border, width: 0.9 },
  });
  slide.addText("DECISION PATH", { x: 6.98, y: 4.52, w: 1.45, h: 0.13, fontSize: 8, bold: true, color: C.blue, charSpacing: 1, margin: 0 });
  slide.addText("Use the index to find candidates. Use a typed live action to confirm price, availability or evidence before presenting or handing off.", {
    x: 6.98,
    y: 4.78,
    w: 5.42,
    h: 0.48,
    fontSize: 10.5,
    bold: true,
    color: C.ink,
    margin: 0,
    fit: "shrink",
  });

  const trust = [
    ["1 advertiser", "per deployment"],
    ["Permitted fields", "only"],
    ["No cross-dealer", "retrieval"],
    ["Deterministic", "deletion"],
    ["Model is never", "the authority"],
  ];
  trust.forEach(([title, sub], i) => {
    const x = 0.58 + i * 2.44;
    slide.addShape(pptx.ShapeType.roundRect, {
      x,
      y: 5.93,
      w: 2.22,
      h: 0.65,
      rectRadius: 0.05,
      fill: { color: i % 2 ? C.panelPurple : C.panelBlue },
      line: { color: C.border, width: 0.7 },
    });
    slide.addText(title, { x: x + 0.12, y: 6.08, w: 1.98, h: 0.15, fontSize: 9, bold: true, color: C.ink, align: "center", margin: 0 });
    slide.addText(sub, { x: x + 0.12, y: 6.29, w: 1.98, h: 0.12, fontSize: 7.5, color: C.muted, align: "center", margin: 0 });
  });
  addSource(slide, "Architecture proposed for a granted Connect capability; production rights and contracts remain to be agreed.");
  slide.addNotes("The key phrase is: index for discovery, re-read for decisions. A full baseline is the recovery path; targeted event reconciliation keeps an individual stock record current. Dealer policies remain a separately attributed knowledge source.");
}

// Slide 5
if (!ONLY_SLIDE || ONLY_SLIDE === 5) {
  const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 5, "Why partner");
  addTitle(
    slide,
    "Extend Autotrader's platform without competing with it.",
    "Autotrader remains the data and automotive-product authority. LoomAI provides the governed AI experience and repeatable deployment layer."
  );

  slide.addShape(pptx.ShapeType.roundRect, { x: 0.58, y: 2.4, w: 3.72, h: 2.58, rectRadius: 0.07, fill: { color: C.panelCyan }, line: { color: "A9DDEB", width: 1 } });
  slide.addText("AUTOTRADER BRINGS", { x: 0.88, y: 2.73, w: 2.3, h: 0.14, fontSize: 8.5, bold: true, color: C.cyan, charSpacing: 1.2, margin: 0 });
  slide.addText("Trusted automotive authority", { x: 0.88, y: 3.02, w: 3.0, h: 0.34, fontSize: 17, bold: true, color: C.navy, margin: 0 });
  addBulletList(slide, [
    "Authorised data and advertiser scope",
    "Connect capabilities and real-time contracts",
    "Marketplace reach and retailer relationships",
    "Deal Builder, Co-Driver and conversion journeys",
  ], 0.9, 3.49, 3.02, 1.15, { fontSize: 10 });

  slide.addShape(pptx.ShapeType.ellipse, { x: 4.48, y: 3.28, w: 0.72, h: 0.72, fill: { color: C.navy }, line: { color: C.navy } });
  slide.addText("+", { x: 4.48, y: 3.43, w: 0.72, h: 0.24, fontSize: 22, bold: true, color: C.white, align: "center", margin: 0 });

  slide.addShape(pptx.ShapeType.roundRect, { x: 5.38, y: 2.4, w: 3.72, h: 2.58, rectRadius: 0.07, fill: { color: C.panelPurple }, line: { color: "C6B9FA", width: 1 } });
  slide.addText("LOOMAI BRINGS", { x: 5.68, y: 2.73, w: 2.3, h: 0.14, fontSize: 8.5, bold: true, color: C.purple, charSpacing: 1.2, margin: 0 });
  slide.addText("Governed AI enablement", { x: 5.68, y: 3.02, w: 3.0, h: 0.34, fontSize: 17, bold: true, color: C.navy, margin: 0 });
  addBulletList(slide, [
    "Dealer-isolated deployments and lifecycle",
    "Grounded reasoning across stock and policy",
    "Governed actions, confirmations and receipts",
    "Reusable templates, plugins and UI surfaces",
  ], 5.7, 3.49, 3.02, 1.15, { fontSize: 10 });

  slide.addShape(pptx.ShapeType.roundRect, { x: 9.28, y: 2.4, w: 3.44, h: 2.58, rectRadius: 0.07, fill: { color: C.navy }, line: { color: C.navy } });
  slide.addText("JOINT OUTCOME", { x: 9.58, y: 2.73, w: 1.9, h: 0.14, fontSize: 8.5, bold: true, color: "7EE8FF", charSpacing: 1.2, margin: 0 });
  slide.addText("Better-qualified buyers", { x: 9.58, y: 3.02, w: 2.75, h: 0.34, fontSize: 17, bold: true, color: C.white, margin: 0 });
  addBulletList(slide, [
    "Relevant dealer-only shortlists",
    "Fewer stale or unsupported answers",
    "Consented context into the next journey",
    "Measurable conversion and trust evidence",
  ], 9.6, 3.49, 2.65, 1.15, { fontSize: 9.5, color: "E2EBFC" });

  slide.addText("FLEXIBILITY WITHOUT ONE-OFF BUILDS", { x: 0.6, y: 5.34, w: 3.3, h: 0.14, fontSize: 8.5, bold: true, color: C.blue, charSpacing: 1.2, margin: 0 });
  const flexibility = [
    "One reusable dealership template",
    "Plugins compile only granted capabilities",
    "Approved model, vector and storage choices",
    "Embed script or native host integration",
    "Deployment-local traffic and secrets",
  ];
  flexibility.forEach((text, i) => addPill(slide, text, 0.58 + i * 2.43, 5.7, 2.24, i % 2 ? C.panelPurple : C.panelBlue, i % 2 ? C.purple : C.blue));
  slide.addShape(pptx.ShapeType.roundRect, { x: 0.58, y: 6.25, w: 12.14, h: 0.44, rectRadius: 0.04, fill: { color: C.amberPale }, line: { color: "F2D49B", width: 0.8 } });
  slide.addText("Not proposing: a new marketplace, a data-resale layer, a replacement for Deal Builder, or another generic chatbot.", {
    x: 0.84,
    y: 6.39,
    w: 11.65,
    h: 0.13,
    fontSize: 9,
    bold: true,
    color: C.amber,
    align: "center",
    margin: 0,
  });
  addSource(slide, "Sources: autotrader.co.uk/partners/retailer/platform/autotrader-connect | help.autotrader.co.uk Dealer Websites");
  slide.addNotes("Position LoomAI as complementary. Autotrader owns trusted data, automotive products and the retailer relationship. LoomAI makes those approved assets usable inside a governed AI journey on dealer-owned surfaces.");
}

// Slide 6
(async () => {
  if (!ONLY_SLIDE || ONLY_SLIDE === 6) {
    const slide = pptx.addSlide("LOOMAI");
  addBrand(slide, 6, "Pilot proposal and partnership ask");
  addTitle(
    slide,
    "Start with one bounded, measurable pilot.",
    "A read-first Connect pilot can prove usefulness, freshness, isolation and conversion value before any broader rollout."
  );

  slide.addShape(pptx.ShapeType.roundRect, { x: 0.58, y: 2.37, w: 4.0, h: 3.36, rectRadius: 0.07, fill: { color: C.navy }, line: { color: C.navy } });
  slide.addText("PILOT SCOPE", { x: 0.9, y: 2.72, w: 1.5, h: 0.14, fontSize: 8.5, bold: true, color: "7EE8FF", charSpacing: 1.2, margin: 0 });
  slide.addText("One dealer. One advertiser. One isolated deployment.", { x: 0.9, y: 3.02, w: 3.35, h: 1.02, fontSize: 17.5, bold: true, color: C.white, margin: 0, fit: "shrink", valign: "mid" });
  addBulletList(slide, [
    "Read-first stock discovery and comparison",
    "Dealer-owned policy knowledge",
    "Live validation of selected facts",
    "One confirmed callback, test-drive or agreed handoff",
    "No provider write unless explicitly granted",
  ], 0.92, 4.17, 3.18, 1.14, { fontSize: 8.8, color: "E2EBFC", paraSpaceAfterPt: 5 });

  slide.addShape(pptx.ShapeType.roundRect, { x: 4.82, y: 2.37, w: 4.7, h: 3.36, rectRadius: 0.07, fill: { color: C.white }, line: { color: C.border, width: 0.9 } });
  slide.addText("WHAT WE NEED FROM AUTOTRADER", { x: 5.14, y: 2.72, w: 3.1, h: 0.14, fontSize: 8.5, bold: true, color: C.purple, charSpacing: 1.1, margin: 0 });
  addBulletList(slide, [
    "Named product sponsor and Connect Integration Manager",
    "Sandbox credentials, capability grants, test advertiser and representative stock",
    "Written rights for retention, embeddings, inference, display and attribution",
    "Notification registration, retries and test-event path",
    "Preferred Deal Builder, enquiry, CRM or appointment handoff",
    "Go-live checklist and one candidate design-partner dealer",
  ], 5.14, 3.06, 3.98, 2.28, { fontSize: 9.4, paraSpaceAfterPt: 7 });

  slide.addShape(pptx.ShapeType.roundRect, { x: 9.78, y: 2.37, w: 2.94, h: 3.36, rectRadius: 0.07, fill: { color: C.greenPale }, line: { color: "A5DFC9", width: 0.9 } });
  slide.addText("SUCCESS GATES", { x: 10.06, y: 2.72, w: 1.9, h: 0.14, fontSize: 8.5, bold: true, color: C.green, charSpacing: 1.1, margin: 0 });
  addBulletList(slide, [
    "Useful-result rate",
    "Shortlist and comparison completion",
    "Fact-support and freshness",
    "Qualified handoff rate",
    "Zero cross-dealer access",
    "Deletion and rollback proof",
  ], 10.05, 3.07, 2.24, 1.82, { fontSize: 9.4, paraSpaceAfterPt: 8 });

  const qr = await QRCode.toDataURL("https://loomai.pro/demos/dealership-ai", { margin: 1, width: 220, color: { dark: `#${C.navy}`, light: "#FFFFFF" } });
  slide.addImage({ data: qr, x: 10.88, y: 4.72, w: 0.68, h: 0.68 });
  slide.addText("Open live demo", { x: 10.0, y: 5.45, w: 2.4, h: 0.13, fontSize: 7.5, bold: true, color: C.green, align: "center", margin: 0 });

  slide.addShape(pptx.ShapeType.roundRect, { x: 0.58, y: 6.02, w: 12.14, h: 0.72, rectRadius: 0.06, fill: { color: C.panelBlue }, line: { color: "9DBBFA", width: 1 } });
  slide.addText("THE ASK", { x: 0.88, y: 6.28, w: 0.7, h: 0.13, fontSize: 8.2, bold: true, color: C.blue, charSpacing: 1.1, margin: 0 });
  slide.addText("Approve an architecture and data-rights workshop, then define a one-dealer Autotrader Connect pilot.", {
    x: 1.7,
    y: 6.21,
    w: 10.55,
    h: 0.28,
    fontSize: 14,
    bold: true,
    color: C.navy,
    margin: 0,
    align: "center",
    fit: "shrink",
  });
    addSource(slide, "Discussion basis: Autotrader Connect official product, partner and advertiser guidance reviewed 5 Oct 2026.");
    slide.addNotes("Close on a decision, not a generic follow-up. Ask for the sponsor, integration manager, sandbox/grants, written data rights, the preferred handoff path and one design-partner dealer. The next working session should be architecture plus data rights.");
  }

  const previewSlide = Number(process.env.PREVIEW_SLIDE || 0);
  if (process.env.DEBUG_SLIDES === "true") {
    console.log(pptx._slides.map((slide) => ({
      slideNum: slide._slideNum,
      objectCount: slide._slideObjects && slide._slideObjects.length,
    })));
  }
  if (previewSlide >= 1 && previewSlide <= pptx._slides.length) {
    const selected = pptx._slides[previewSlide - 1];
    pptx._slides = [selected, ...pptx._slides.filter((slide) => slide !== selected)];
  }
  const outputFile = process.env.DECK_OUTPUT || OUT;
  await pptx.writeFile({ fileName: outputFile });
  console.log(outputFile);
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
