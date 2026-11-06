# Assisted Customer Kit Setup

LoomAI staff can prepare a customer's AI experience before the customer signs in.
The Kit, Smart Brain and Engage apps belong to the customer's workspace from
creation. There is no later transfer or temporary email account.

## Staff Setup

1. Sign in to the staff Console as a Platform administrator.
2. Open **Customers**. Create the company workspace if it does not exist, then
   choose **Set up kit** on that customer.
3. Choose **Give customer access** and enter the customer's real email and name.
   This prepares an administrator for that company. If that active administrator
   already belongs to the same company, the existing account is reused.
4. Follow the existing Kit setup form: choose an available experience, name the
   Kit, set the branding, and choose website embed, standalone app or both.
   The page identifies the customer that will own the setup.
5. Finish connections, document indexing, release verification and Engage
   activation in the existing Smart Brain and Engage editors. Creating a Kit
   alone does not start infrastructure or make it live.
6. Share the access instructions with the customer and confirm they can see the
   prepared Kit using their own session. Preparing access does not send an
   invitation email or expose a password.

## Customer Sign In

Use the instructions returned by **Give customer access**:

- Already connected to Console: open `/sign-in` and use the existing login.
- Existing Supabase login not yet connected to this Console account: open
  `/recover-account`, request a fresh email link and complete the verified
  recovery flow. This connects the prepared account without moving resources.
- Brand-new email identity: register and verify the email through `/sign-up`
  first, then complete `/recover-account` to connect the prepared account.
  Do not create a second company workspace when the existing-account notice
  appears.

The email link proves mailbox ownership. No staff-chosen password needs to be
shared. An expired or consumed link requires a fresh email link.

## Ownership And Access

The customer administrator sees the same customer-owned Kit and its products.
There is no copy of the inventory, vector index or deployment to hand over.
Existing infrastructure quotas and normal staging/production release controls
still apply.

An email already belonging to a different customer, a staff account or a disabled/
revoked account is not silently moved or re-enabled. Use the correct customer
login or resolve that membership explicitly with an administrator.

## Customer-Controlled LoomAI Staff Access

In **Account & team -> LoomAI staff access**, enter an existing LoomAI staff
operator's email and choose **Grant access**. No staff account is created and
no invitation email is sent. A customer administrator controls only their own
workspace's grants.

The staff member signs in with their own account and opens **Customer
workspaces -> Open kits**. They can prepare/configure that customer's Kits,
Smart Brains and Engage apps and use release operations permitted by the
existing operator endpoints. Ownership never changes and audit records retain
the staff identity. This does not give access to customer sign-ins, team/user
administration, global secrets or Platform-administrator powers.

Choose the remove-person icon beside an active grant, review the named staff
member and confirm **Revoke access**. Future collaboration requests are denied
without waiting for staff logout, including access through that operator's
earlier deployment assignments in this workspace. Already-running work may
finish; revocation does not undo saved configuration or running resources.
Granting the email again restores the same workspace/staff relationship.

This list covers customer-controlled grants. Independent legacy deployment
assignments are not enumerated here; once an operator's workspace grant is
revoked, those assignments cannot bypass its explicit denial. Global Platform
administration and privileged machine credentials remain a separate LoomAI
authority, not customer-revokable grants. Use a normal staff operator account
for this collaboration flow, not a global administrator account.

Desk human-agent assignment remains separate from Console configuration access.
