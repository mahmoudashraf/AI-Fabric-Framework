update shopify_companion_package_profiles
set verification_pack_id = 'starter-launch-readiness',
    updated_at = current_timestamp
where profile_key = 'LOW_COST'
  and verification_pack_id = 'shopify-companion-free-readiness';
