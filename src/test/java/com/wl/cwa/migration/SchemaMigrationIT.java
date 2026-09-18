package com.wl.cwa.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wl.cwa.support.ContainerTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that Flyway migrates an empty database into the exact IAM baseline: all six tables
 * exist, the charset is utf8mb4, and the permission seed rows are present.
 */
class SchemaMigrationIT extends ContainerTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allIamTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = database() and table_name in "
                        + "('iam_user','iam_role','iam_permission','iam_user_role','iam_role_permission','audit_event') "
                        + "order by table_name",
                String.class);

        assertThat(tables).containsExactly(
                "audit_event", "iam_permission", "iam_role", "iam_role_permission", "iam_user", "iam_user_role");
    }

    @Test
    void flywayHistoryContainsSuccessfulV1() {
        List<Map<String, Object>> history =
                jdbcTemplate.queryForList("select version, success from flyway_schema_history order by installed_rank");

        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("version", "1").containsEntry("success", true);
    }

    @Test
    void permissionSeedRowsExist() {
        List<String> codes =
                jdbcTemplate.queryForList("select code from iam_permission order by code", String.class);

        assertThat(codes).containsExactly("audit:read", "user:read", "user:role:write", "user:write");
    }

    @Test
    void tablesUseUtf8Mb4() {
        String collation = jdbcTemplate.queryForObject(
                "select table_collation from information_schema.tables "
                        + "where table_schema = database() and table_name = 'iam_user'",
                String.class);

        assertThat(collation).isEqualTo("utf8mb4_0900_ai_ci");
    }

    @Test
    void auditResultCheckConstraintRejectsUnknownValues() {
        Set<String> columns = Set.copyOf(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = database() and table_name = 'audit_event'",
                String.class));

        assertThat(columns)
                .contains("public_id", "occurred_at", "event_type", "result", "request_id", "details_json", "client_ip_hash");
    }
}
