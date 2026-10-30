# LoomAI Engage: Website And Standalone Setup

LoomAI Engage delivers your AI workspace on your website, at its own address,
or both. Each delivery channel has its own installation ID and lifecycle. Both
can use the same consumer assignment, deployment, experience pack, indexed
knowledge and enabled capabilities. A second delivery channel does not create
another runtime, vector index or business database.

## Embed On Your Website

1. Sign in to [Console](https://console.loomai.pro/) and open **Engage apps**.
2. Choose **Create Engage app**, then **Connect deployment**. Select your AI
   deployment. Engage uses its current assignment and shows the environment and
   runtime status; a fresh undeployed deployment supports a configuration draft.
   Choose a starter or existing **Experience
   configuration** and **Embed on my website**. You do not need an assignment ID.
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

Choose **Create Engage app** and **Standalone app**. Connect the same deployment
and choose an experience configuration, then enter a unique app slug and any
optional custom domains. Save, verify the address, resolve readiness and
activate. The resulting app uses the shared Engage frontend at its own HTTPS
address; it requires no script in a customer page.

See the [standalone delivery guide](LOOMAI_ENGAGE_STANDALONE_AI_WORKSPACE_DELIVERY_GUIDE.md)
for custom-domain DNS and visitor sign-in details.

## Connect Or Change Deployment

From a deployment's details, choose **Connect Engage app**, then create an app
or select an existing app. From an app, choose **Change deployment**. Both open
the same deployment-first panel. The current assignment is retained, with a
read-only environment badge and runtime status. **Change connection** reveals
environment/release options only when you deliberately want another connection,
such as switching from customer staging to verified production. If multiple
different assignments exist and no current app identifies one, choose explicitly;
Engage does not guess. Technical assignment references remain in **Advanced**.

Selecting an existing connection does not rebind it. Moving one app uses a
destination connection, preserving the other apps on the original assignment.
Your branding, authentication, tools and delivery settings stay in the app;
review any readiness differences before activating. Unsaved appearance edits
also survive opening the connection panel.

A deployment/environment connection without a release supports configuration
and saving a draft, not runtime access. When the runtime is ready, use **Change
connection** to select its verified release, save, refresh readiness and activate.
Newer releases do not automatically move an existing app. An active installation
must first be explicitly disabled for editing. If a connection is created but
app saving fails, it remains available for selection; do not create another
connection to retry the same save.

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
and phone, and sample welcome/conversation/result screens. On a smaller
screen, use the Settings and Preview tabs.

The canvas uses the live pack's presentation defaults, configured default and
contextual tool groups, icons, tool rail and registered result renderers.
**Attached context** lets you inspect contextual tools without capturing a real
page. Available samples include sources, confirmations and receipts; human
support samples and intake controls follow the assigned runtime's safe
capability projection. Sign-in samples follow the selected interaction profile.
Unknown runtime capabilities and unsupported extensions are shown as limitations,
not simulated as working integrations. Private adapter responses are not called.

The canvas uses synthetic content and shared widget presentation components. It
does not call AI, execute actions, open visitor conversations, or change your
deployment. A sample human-support screen does not enable human support.
Only configuration edits are saved; preview view/device/sample/sign-in switches
do not change delivery or capabilities.

Logos must be publicly browser-loadable HTTPS images without embedded
credentials. The image host must permit cross-origin display; an unavailable
image uses the default assistant mark. LoomAI does not upload or manage your
logo storage. Clear the URL to restore the default mark.

An active app can be previewed safely. To publish changes, explicitly choose
**Disable for editing**, confirm, save the draft, check readiness and activate
again. Viewing or closing the preview never changes an active app. Changes
apply to this installation only; a second delivery channel remains independent.

## Custom Domain Records

For a standalone app, save an exact custom hostname. Its domain panel shows
copyable **TXT ownership** and **CNAME routing** records. Add both at your DNS
provider, then select **Verify and prepare** (or **Verify domain** for one
address). After ownership passes, Platform provisions the route and continues
checking HTTPS automatically. Route/HTTPS pending is not yet verified; keep the
app details open to see verification complete without another Verify click.
If the CNAME target is missing, this is a Platform setup issue, not a value the
customer should guess. A website embed on your existing site needs no CNAME/TXT.

If runtime origins or identity settings need updating, select **Apply required
deployment update**. Inspect the proposed origins, affected apps and changed
configuration sections. **Inspect deployment changes** opens the existing
deployment change view, including unrelated draft edits. Confirm **Publish and
apply update** only when all included changes are intended. Platform reuses the
assigned environment and runtime image through its normal release mechanism;
it does not choose a newer image or move the app to another environment.

After the release is verified, choose **Use verified update** to update the
shared app connection. Apps sharing that connection will use the verified
release. Review readiness, then **Activate app** explicitly. Domain verification,
release completion and app activation are separate steps. For magic links or OAuth,
also configure the exact new address in your identity provider's redirects.
Domain verification does not modify that external provider on your behalf.

The routing record normally points to `engage.loomai.pro`, LoomAI's shared
Engage frontend. Visitors remain on your custom hostname; their branding and
assigned deployment are resolved for that hostname. This is not a separate AI
runtime or customer database. In direct-runtime mode the browser talks to the
assigned AI deployment, not through the shared frontend server.

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
