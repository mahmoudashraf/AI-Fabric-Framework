package com.ai.fabric.platform.backend.deployment.admission;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Reads durable delivery receipts for the exact cycle/channel/key. Never accept a browser 'sent' flag. */
public interface AdmissionNoticeDeliverySource {
    NoticeDelivery read(NoticeWork work);
}
