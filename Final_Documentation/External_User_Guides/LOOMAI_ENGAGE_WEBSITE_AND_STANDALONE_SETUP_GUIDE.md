# LoomAI Engage: Website And Standalone Setup

LoomAI Engage delivers your AI workspace on your website, at its own address,
or both. Each delivery channel has its own installation ID and lifecycle. Both
can use the same consumer assignment, deployment, experience pack, indexed
knowledge and enabled capabilities. A second delivery channel does not create
another runtime, vector index or business database.

## Embed On Your Website

1. Sign in to [Console](https://console.loomai.pro/) and open **Engage apps**.
2. Choose **Create Engage app**, select your active consumer assignment and a
   starter or existing **Experience configuration**. Select **Embed on my website**.
3. Enter your exact HTTPS website origins, for example
   `https://www.example.com`. Include the apex origin separately if used.
   Paths and wildcards are not origins.
4. Save the draft. Its **Installation script** section contains your exact
   script, allowed websites and readiness results. Origin and identity changes
   are projected into the deployment draft. If readiness reports missing
   runtime settings, publish/apply the deployment update through the normal
   version flow, then refresh readiness.
5. Activate the ready installation. Copy the script into your website's shared
   layout, once before the closing `</body>` tag. Open your website and test it.

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="YOUR_INSTALLATION_ID">
</script>
```

Use the script shown in your Console, not a guessed installation ID. It contains
no private key. Platform serves the installer, configuration and assets; the
workspace then talks to the assigned deployment or configured private adapter.
Readiness confirms deployment compatibility, not that you have inserted the
script into your website.

## Add A Standalone App

Choose **Create Engage app** and **Standalone app**. Select the same consumer
assignment and experience configuration, then enter a unique app slug and any
optional custom domains. Save, verify the address, resolve readiness and
activate. The resulting app uses the shared Engage frontend at its own HTTPS
address; it requires no script in a customer page.

See the [standalone delivery guide](LOOMAI_ENGAGE_STANDALONE_AI_WORKSPACE_DELIVERY_GUIDE.md)
for custom-domain DNS and visitor sign-in details.

## Use Both

Open an existing standalone app and choose **Add website embed**, or open an
embedded installation and choose **Add standalone app**. These actions prepare a
new installation with the same consumer and full experience configuration,
including custom tools, branding and authentication. They do not change or
disable the existing app. Supply the new channel's website origins or app slug,
save, verify and activate it separately.

Installations share their assigned AI deployment, not visitor browser storage.
A conversation is not automatically transferred between unrelated website and
standalone origins. Changing the consumer assignment changes where subsequent
bootstrap resolves; it does not migrate visitor conversations.

## Appearance And Preview

During **Create Engage app**, customize **Customer-facing appearance** beside
the **Customer preview**. For an existing app, open its resource details and
choose **Appearance & preview**. The assistant name is what visitors see;
the workspace name identifies the installation in your account.

Change the assistant name, HTTPS logo image URL, brand colour, light/dark/system
appearance, welcome message and input placeholder. The preview updates without
saving. Switch between Website dock, MAX workspace and Standalone app, desktop
and phone, and sample welcome/conversation/human-support screens. On a smaller
screen, use the Settings and Preview tabs.

The canvas uses sample content and shared widget presentation components. It
does not call AI, execute actions, open visitor conversations, or change your
deployment. A sample human-support screen does not enable human support.
Only the appearance controls are saved; preview view/device/sample switches
do not change delivery or capabilities.

Logos must be publicly browser-loadable HTTPS images without embedded
credentials. The image host must permit cross-origin display; an unavailable
image uses the default assistant mark. LoomAI does not upload or manage your
logo storage. Clear the URL to restore the default mark.

An active app can be previewed safely. To publish changes, explicitly choose
**Disable for editing**, confirm, save the draft, check readiness and activate
again. Viewing or closing the preview never changes an active app. Changes
apply to this installation only; a second delivery channel remains independent.

## Configuration And Access

- Templates supply startup defaults, not permanent delivery restrictions.
- Each installation has independent exact origins and active/draft/disabled
  state. Disable an active installation before editing it, then verify and
  reactivate it.
- Anonymous direct mode needs only the script for public-safe capabilities.
  Login-required and protected-capability installations retain their configured
  identity provider. Shopify/private integrations retain their private adapter.
  Adding a delivery channel never downgrades authentication.
- Add new website origins to your identity provider's redirect allowlist when
  using provider-hosted email/OAuth redirects. Deploying runtime configuration
  does not configure an external identity provider automatically.
- Website content-security policies must allow the Platform asset/configuration
  origin and assigned runtime or private-adapter connections. See the developer
  [one-script installation guide](../../max-mode-widget/docs/AI_WORKSPACE_ONE_SCRIPT_INSTALLATION_GUIDE.md)
  for CSP and connection-contract details.
- Missing/foreign origins and inactive installations cannot obtain a public
  installation manifest. Do not place provider credentials, runtime API keys,
  private assertions or service-role keys in website HTML.

This flow is experience-pack neutral: dealership, generic knowledge, and
compatible private integrations use the same installation editor and APIs.
