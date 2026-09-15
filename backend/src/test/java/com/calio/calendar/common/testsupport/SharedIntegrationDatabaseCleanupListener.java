package com.calio.calendar.common.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class SharedIntegrationDatabaseCleanupListener implements TestExecutionListener, Ordered {

    private static final int ORDER = 3_000;

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);
        clearApplicationTables(dataSource);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private void clearApplicationTables(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String tableName : applicationTableNames(statement)) {
                statement.execute("TRUNCATE TABLE \"" + tableName + "\" RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private List<String> applicationTableNames(Statement statement) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND TABLE_TYPE = 'BASE TABLE'
                  AND TABLE_NAME <> 'flyway_schema_history'
                """)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tableNames;
    }
}
