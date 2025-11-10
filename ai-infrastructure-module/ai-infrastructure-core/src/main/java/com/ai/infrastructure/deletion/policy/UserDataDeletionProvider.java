package com.ai.infrastructure.deletion.policy;

/**
 * Infrastructure hook for right-to-delete orchestration. Customer implementations own the
 * domain specific deletion of user data.
 */
public interface UserDataDeletionProvider {

    /**
     * Determine if the user can be deleted (e.g. no active orders).
     *
     * @param userId identifier of the user
     * @return {@code true} when deletion may proceed
     */
    boolean canDeleteUser(String userId);

    /**
     * Delete or anonymise any domain data linked to the user.
     *
     * @param userId identifier of the user
     * @return number of records that were deleted or anonymised
     */
    int deleteUserDomainData(String userId);

    /**
     * Notify downstream systems that the user has been deleted.
     *
     * @param userId identifier of the deleted user
     */
    void notifyAfterDeletion(String userId);
}
