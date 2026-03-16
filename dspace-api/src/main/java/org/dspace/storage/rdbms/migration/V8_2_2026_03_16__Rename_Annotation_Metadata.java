/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.rdbms.migration;

import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.rdbms.DatabaseUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Rename 'dc.relation.annotation' metadata to 'dc.relation.webannotation' for all existing records.
 * This migration runs only if the configuration property 'flyway.migration.rename-annotation-metadata'
 * is set to true (which is the default value if the property is not present).
 *
 * @author Piaget Bouaka (piaget.bouaka at 4science.it)
 */
public class V8_2_2026_03_16__Rename_Annotation_Metadata extends BaseJavaMigration {
    // Size of migration script run
    protected Integer migration_file_size = -1;

    @Override
    public void migrate(Context context) throws Exception {

        ConfigurationService configurationService = DSpaceServicesFactory
                .getInstance().getConfigurationService();

        boolean shouldMigrate = configurationService.getBooleanProperty(
                "flyway.migration.rename-annotation-metadata", true);

        if (!shouldMigrate) {
            return;
        }

        String dbtype = DatabaseUtils.getDbType(context.getConnection());
        String sqlMigrationPath = "org/dspace/storage/rdbms/sqlmigration/metadata/" + dbtype + "/";
        String dataMigrateSQL = MigrationUtils.getResourceAsString(
                sqlMigrationPath + "V8.2_2026.03.16__Rename_Annotation_Metadata.sql");

        DatabaseUtils.executeSql(context.getConnection(), dataMigrateSQL);

        migration_file_size = dataMigrateSQL.length();
    }

    @Override
    public Integer getChecksum() {
        return migration_file_size;
    }
}
