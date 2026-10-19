package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Read-only privilege/identity inspection. It never creates a database, schema, role or business record. */
@Component
public class DeploymentAiStateStorageProbe {
    public void verify(DeploymentAiStateStorageConnection expected) {
        try (Connection connection = connect(expected)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                select current_database() = ?, current_user = ?,
                       not (rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls),
                       not pg_has_role(current_user, 'pg_read_all_data', 'MEMBER')
                         and not pg_has_role(current_user, 'pg_write_all_data', 'MEMBER')
                         and not pg_has_role(current_user, 'pg_execute_server_program', 'MEMBER')
                         and not pg_has_role(current_user, 'pg_read_server_files', 'MEMBER')
                         and not pg_has_role(current_user, 'pg_write_server_files', 'MEMBER')
                         and not has_database_privilege(current_user, current_database(), 'CREATE'),
                       has_schema_privilege(current_user, ?, 'USAGE') and has_schema_privilege(current_user, ?, 'CREATE'),
                       has_schema_privilege(current_user, ?, 'USAGE') and has_schema_privilege(current_user, ?, 'CREATE')
                  from pg_roles where rolname = current_user
                """)) {
                statement.setQueryTimeout(10);
                statement.setString(1, expected.databaseName()); statement.setString(2, expected.username());
                statement.setString(3, expected.runtimeSchema()); statement.setString(4, expected.runtimeSchema());
                statement.setString(5, expected.connectorSchema()); statement.setString(6, expected.connectorSchema());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw denied("AI_STATE_STORAGE_ROLE_UNAVAILABLE");
                    for (int column = 1; column <= 6; column++) {
                        if (!result.getBoolean(column)) throw denied("AI_STATE_STORAGE_DEDICATED_ROLE_REQUIRED");
                    }
                }
            }
            try (var statement = connection.prepareStatement("""
                select exists (
                    select 1 from pg_namespace n
                     where n.nspname not in (?, ?, 'information_schema')
                       and n.nspname not like 'pg\\_%' escape '\\'
                       and exists (select 1 from pg_roles r where pg_has_role(current_user,r.oid,'MEMBER')
                           and (has_schema_privilege(r.oid, n.oid, 'CREATE')
                                or pg_has_role(r.oid, n.nspowner, 'MEMBER')))
                ) or exists (
                    select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
                     where n.nspname not in (?, ?, 'information_schema')
                       and n.nspname not like 'pg\\_%' escape '\\'
                       and c.relkind in ('r','p','v','m','f')
                       and exists (select 1 from pg_roles r where pg_has_role(current_user,r.oid,'MEMBER')
                           and (has_table_privilege(r.oid,c.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                                or pg_has_role(r.oid,c.relowner,'MEMBER')))
                )
                """)) {
                statement.setQueryTimeout(10);
                statement.setString(1, expected.runtimeSchema()); statement.setString(2, expected.connectorSchema());
                statement.setString(3, expected.runtimeSchema()); statement.setString(4, expected.connectorSchema());
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getBoolean(1)) throw denied("AI_STATE_STORAGE_BUSINESS_AUTHORITY_FORBIDDEN");
                }
            }
            // NOINHERIT roles can still SET ROLE, so inspect all reachable memberships, not only effective grants.
            try (var statement = connection.prepareStatement("""
                select exists (
                    select 1 from pg_roles r
                     where r.oid <> (select oid from pg_roles where rolname=current_user)
                       and pg_has_role(current_user, r.oid, 'MEMBER')
                       and (r.rolsuper or r.rolcreatedb or r.rolcreaterole or r.rolreplication or r.rolbypassrls
                            or has_database_privilege(r.oid,current_database(),'CREATE'))
                ) or exists (
                    select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
                     where n.nspname not in ('information_schema')
                       and n.nspname not like 'pg\\_%' escape '\\'
                       and p.prosecdef and exists (
                           select 1 from pg_roles r where pg_has_role(current_user,r.oid,'MEMBER')
                             and has_schema_privilege(r.oid,n.oid,'USAGE')
                             and has_function_privilege(r.oid,p.oid,'EXECUTE'))
                )
                """)) {
                statement.setQueryTimeout(10);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getBoolean(1)) throw denied("AI_STATE_STORAGE_BUSINESS_AUTHORITY_FORBIDDEN");
                }
            }
            connection.rollback();
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            // JDBC exceptions may contain endpoints, usernames or supplied connection data.
            throw denied("AI_STATE_STORAGE_CONNECTION_VERIFICATION_FAILED");
        }
    }

    Connection connect(DeploymentAiStateStorageConnection expected) throws SQLException {
        return DriverManager.getConnection(expected.runtimeJdbcUrl(), expected.username(), expected.password());
    }

    private ResponseStatusException denied(String code) {
        return new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, code);
    }
}
