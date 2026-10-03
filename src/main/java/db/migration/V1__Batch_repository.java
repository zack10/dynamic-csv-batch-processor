package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Pin repository DDL to the Spring Batch version managed by the Boot BOM. */
public class V1__Batch_repository extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();
        String dialect = switch (product) {
            case "H2" -> "h2";
            case "PostgreSQL" -> "postgresql";
            default -> throw new IllegalArgumentException("Unsupported database: " + product);
        };
        ScriptUtils.executeSqlScript(context.getConnection(),
                new ClassPathResource("org/springframework/batch/core/schema-" + dialect + ".sql"));
    }
}
