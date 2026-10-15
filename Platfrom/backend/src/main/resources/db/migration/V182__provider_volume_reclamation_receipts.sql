ALTER TABLE deployment_provider_resource_handles
    ADD COLUMN volume_deletion_intent_receipt VARCHAR(128),
    ADD COLUMN volume_reclamation_receipt VARCHAR(128);
