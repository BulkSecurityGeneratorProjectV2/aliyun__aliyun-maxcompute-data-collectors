package com.aliyun.odps.datacarrier.taskscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MmaMetaManagerFsImplTest {
  private static final Path DEFAULT_MMA_PARENT_DIR =
      Paths.get(System.getProperty("user.dir")).toAbsolutePath();
  private static final Path DEFAULT_MMA_META_DIR = Paths.get(DEFAULT_MMA_PARENT_DIR.toString(),
                                                             ".mma");
  private static final String DEFAULT_DB = "test";

  private static MetaSource metaSource = new MockHiveMetaSource();

  @BeforeClass
  public static void beforeClass() throws IOException {
    if (DEFAULT_MMA_META_DIR.toFile().exists()) {
      DirUtils.removeDir(DEFAULT_MMA_META_DIR);
    }
    MmaMetaManagerFsImpl.init(DEFAULT_MMA_PARENT_DIR.toString(), metaSource);
  }

  @AfterClass
  public static void afterClass() throws IOException {
    if (DEFAULT_MMA_META_DIR.toFile().exists()) {
      DirUtils.removeDir(DEFAULT_MMA_META_DIR);
    }
  }

  @Before
  public void before() throws Exception {
    // Add migration jobs
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      MmaConfig.AdditionalTableConfig config =
          new MmaConfig.AdditionalTableConfig(
              null,
              null,
              10,
              1);
      MmaConfig.TableMigrationConfig tableMigrationConfig =
          new MmaConfig.TableMigrationConfig(DEFAULT_DB, table, DEFAULT_DB, table, config);
      MmaMetaManagerFsImpl.getInstance().addMigrationJob(tableMigrationConfig);
    }
  }

  @After
  public void after() throws Exception {
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      MmaMetaManagerFsImpl.getInstance().removeMigrationJob(DEFAULT_DB, table);
    }
  }

  @Test
  public void testInitJobs() throws Exception {
    // Check if the directory structure and file content is expected
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      MetaSource.TableMetaModel tableMetaModel = metaSource.getTableMeta(DEFAULT_DB, table);

      // Make sure the metadata dir exists
      Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, table);
      assertTrue(dir.toFile().exists());

      // Make sure the table metadata file exists
      Path metadataPath = Paths.get(dir.toString(), "metadata");
      assertTrue(metadataPath.toFile().exists());

      // Make sure the content of table metadata file is expected
      String metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

      // Make sure the config file exists
      Path configPath = Paths.get(dir.toString(), "config");
      assertTrue(configPath.toFile().exists());

      // Make sure the partition metadata file exists and its content is expected
      if (tableMetaModel.partitionColumns.size() > 0) {
        Path partitionMetadataPath = Paths.get(dir.toString(), "partitions_all");
        assertTrue(partitionMetadataPath.toFile().exists());

        String partitionMetadata = DirUtils.readFile(partitionMetadataPath);
        assertEquals("hello_world\n", partitionMetadata);
      }
    }
  }

  @Test
  public void testRestartJobPartitioned() throws Exception {
    // Update status to succeeded so that it could be restarted
    MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB,
                                                    "test_partitioned",
                                                    MmaMetaManager.MigrationStatus.SUCCEEDED);

    // Restart job
    MmaConfig.AdditionalTableConfig config =
        new MmaConfig.AdditionalTableConfig(
            null,
            null,
            10,
            1);
    MmaConfig.TableMigrationConfig tableMigrationConfig =
        new MmaConfig.TableMigrationConfig(DEFAULT_DB,
                                           "test_partitioned",
                                           DEFAULT_DB,
                                           "test_partitioned",
                                           config);
    MmaMetaManagerFsImpl.getInstance().addMigrationJob(tableMigrationConfig);

    // Make sure the metadata dir exists
    Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, "test_partitioned");
    assertTrue(dir.toFile().exists());

    // Make sure the table metadata file exists
    Path metadataPath = Paths.get(dir.toString(), "metadata");
    assertTrue(metadataPath.toFile().exists());

    // Make sure the content of table metadata file is expected
    String metadata = DirUtils.readFile(metadataPath);
    assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

    // Make sure the config file exists
    Path configPath = Paths.get(dir.toString(), "config");
    assertTrue(configPath.toFile().exists());

    // Make sure the partition metadata file exists and its content is expected
    Path partitionMetadataPath = Paths.get(dir.toString(), "partitions_all");
    assertTrue(partitionMetadataPath.toFile().exists());
    String partitionMetadata = DirUtils.readFile(partitionMetadataPath);
    assertEquals("hello_world\n", partitionMetadata);
  }

  @Test
  public void testRestartJobNonPartitioned() throws Exception {
    // Update status to succeeded so that it could be restarted
    MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB,
                                                    "test_non_partitioned",
                                                    MmaMetaManager.MigrationStatus.SUCCEEDED);

    // Restart job
    MmaConfig.AdditionalTableConfig config =
        new MmaConfig.AdditionalTableConfig(
            null,
            null,
            10,
            1);
    MmaConfig.TableMigrationConfig tableMigrationConfig =
        new MmaConfig.TableMigrationConfig(DEFAULT_DB,
                                           "test_non_partitioned",
                                           DEFAULT_DB,
                                           "test_non_partitioned",
                                           config);
    MmaMetaManagerFsImpl.getInstance().addMigrationJob(tableMigrationConfig);

    // Make sure the metadata dir exists
    Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, "test_non_partitioned");
    assertTrue(dir.toFile().exists());

    // Make sure the table metadata file exists
    Path metadataPath = Paths.get(dir.toString(), "metadata");
    assertTrue(metadataPath.toFile().exists());

    // Make sure the content of table metadata file is expected
    String metadata = DirUtils.readFile(metadataPath);
    assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

    // Make sure the config file exists
    Path configPath = Paths.get(dir.toString(), "config");
    assertTrue(configPath.toFile().exists());
  }

  @Test
  public void testAddPartitionsToExistingJob() throws IOException {
    // Update status to succeeded so that it could be restarted
    MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB,
                                                    "test_partitioned",
                                                    MmaMetaManager.MigrationStatus.SUCCEEDED);
    // Restart job
    MmaConfig.AdditionalTableConfig config =
        new MmaConfig.AdditionalTableConfig(
            null,
            null,
            10,
            1);

    MmaConfig.TableMigrationConfig tableMigrationConfig =
        new MmaConfig.TableMigrationConfig(DEFAULT_DB,
                                           "test_partitioned",
                                           DEFAULT_DB,
                                           "test_partitioned",
                                           new LinkedList<>(Collections.singletonList(
                                               Collections.singletonList("foo"))),
                                           config);
    MmaMetaManagerFsImpl.getInstance().addMigrationJob(tableMigrationConfig);

    // Make sure the metadata dir exists
    Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, "test_partitioned");
    assertTrue(dir.toFile().exists());

    // Make sure the table metadata file exists
    Path metadataPath = Paths.get(dir.toString(), "metadata");
    assertTrue(metadataPath.toFile().exists());

    // Make sure the content of table metadata file is expected
    String metadata = DirUtils.readFile(metadataPath);
    assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

    // Make sure the config file exists
    Path configPath = Paths.get(dir.toString(), "config");
    assertTrue(configPath.toFile().exists());

    // Make sure the partition metadata file exists and its content is expected
    Path partitionMetadataPath = Paths.get(dir.toString(), "partitions_all");
    assertTrue(partitionMetadataPath.toFile().exists());
    String partitionMetadata = DirUtils.readFile(partitionMetadataPath);
    assertEquals("hello_world\nfoo\n", partitionMetadata);
  }

  @Test
  public void testGetStatus() throws Exception {
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, table);
      Path metadataPath = Paths.get(dir.toString(), "metadata");

      // Make sure the content of table metadata file is expected
      String metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);
      assertEquals(MmaMetaManager.MigrationStatus.PENDING,
                   MmaMetaManagerFsImpl.getInstance().getStatus(DEFAULT_DB, table));
    }
  }

  @Test
  public void testUpdateTableStatusToFailed() throws Exception {
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      MetaSource.TableMetaModel tableMetaModel = metaSource.getTableMeta(DEFAULT_DB, table);

      Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, table);
      Path metadataPath = Paths.get(dir.toString(), "metadata");

      // Should be PENDING at beginning
      String metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

      // Change to RUNNING
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table,
                                                      MmaMetaManager.MigrationStatus.RUNNING);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.RUNNING, 0), metadata);

      if (tableMetaModel.partitionColumns.size() > 0) {
        List<List<String>> partitionValuesList = new LinkedList<>();
        partitionValuesList.add(tableMetaModel.partitions.get(0).partitionValues);
        MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, partitionValuesList,
                                 MmaMetaManager.MigrationStatus.FAILED);

        Path succeededPartitionsPath = Paths.get(dir.toString(), "partitions_failed");
        String succeededPartitions = DirUtils.readFile(succeededPartitionsPath);
        assertEquals("hello_world\n", succeededPartitions);
      }

      // Change to FAILED, but since retry limit is 1, the status should be set to PENDING
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, MmaMetaManager.MigrationStatus.FAILED);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 1), metadata);

      // Change to FAILED, this time should be FAILED
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, MmaMetaManager.MigrationStatus.FAILED);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.FAILED, 2), metadata);
    }
  }

  @Test
  public void testUpdateTableStatusToSucceeded() throws Exception {
    for (String table : metaSource.listTables(DEFAULT_DB)) {
      MetaSource.TableMetaModel tableMetaModel = metaSource.getTableMeta(DEFAULT_DB, table);

      Path dir = Paths.get(DEFAULT_MMA_META_DIR.toString(), DEFAULT_DB, table);
      Path metadataPath = Paths.get(dir.toString(), "metadata");

      // Should be PENDING at beginning
      String metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 0), metadata);

      // Change to RUNNING
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, MmaMetaManager.MigrationStatus.RUNNING);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.RUNNING, 0), metadata);

      // Change to FAILED, but since retry limit is 1, the status should be set to PENDING
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, MmaMetaManager.MigrationStatus.FAILED);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.PENDING, 1), metadata);

      if (tableMetaModel.partitionColumns.size() > 0) {
        List<List<String>> partitionValuesList = new LinkedList<>();
        partitionValuesList.add(tableMetaModel.partitions.get(0).partitionValues);
        MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, partitionValuesList,
                                 MmaMetaManager.MigrationStatus.SUCCEEDED);

        Path succeededPartitionsPath = Paths.get(dir.toString(), "partitions_succeeded");
        String succeededPartitions = DirUtils.readFile(succeededPartitionsPath);
        assertEquals("hello_world\n", succeededPartitions);
      }

      // Change to SUCCEED, this time should be SUCCEED
      MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, table, MmaMetaManager.MigrationStatus.SUCCEEDED);
      metadata = DirUtils.readFile(metadataPath);
      assertEquals(String.format("%s\n%d", MmaMetaManager.MigrationStatus.SUCCEEDED, 1), metadata);
    }
  }

  @Test
  public void testGetPendingTables() {
    List<MetaSource.TableMetaModel> pendingTables = MmaMetaManagerFsImpl.getInstance().getPendingTables();
    assertEquals(2, pendingTables.size());

    for (MetaSource.TableMetaModel tableMetaModel : pendingTables) {
      if ("test_non_partitioned".equals(tableMetaModel.tableName)) {
        assertEquals(DEFAULT_DB, tableMetaModel.odpsProjectName);
        assertEquals("test_non_partitioned", tableMetaModel.odpsTableName);
        assertEquals(1, tableMetaModel.columns.size());
        assertEquals("foo", tableMetaModel.columns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.columns.get(0).odpsType.toLowerCase());
        assertEquals(0, tableMetaModel.partitionColumns.size());
        assertEquals(0, tableMetaModel.partitions.size());
      } else if ("test_partitioned".equals(tableMetaModel.tableName)) {
        assertEquals(DEFAULT_DB, tableMetaModel.odpsProjectName);
        assertEquals("test_partitioned", tableMetaModel.odpsTableName);
        assertEquals(1, tableMetaModel.columns.size());
        assertEquals("foo", tableMetaModel.columns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.columns.get(0).odpsType.toLowerCase());
        assertEquals(1, tableMetaModel.partitionColumns.size());
        assertEquals("bar", tableMetaModel.partitionColumns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.partitionColumns.get(0).odpsType.toLowerCase());
        assertEquals(1, tableMetaModel.partitions.size());
        assertEquals("hello_world", tableMetaModel.partitions.get(0).partitionValues.get(0));
      }
    }
  }

  @Test
  public void testGetPendingTablesAfterUpdateTableStatus() {
    MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, "test_non_partitioned",
                             MmaMetaManager.MigrationStatus.SUCCEEDED);

    List<MetaSource.TableMetaModel> pendingTables = MmaMetaManagerFsImpl.getInstance().getPendingTables();
    assertEquals(1, pendingTables.size());

    MetaSource.TableMetaModel tableMetaModel = pendingTables.get(0);
    assertEquals(DEFAULT_DB, tableMetaModel.odpsProjectName);
    assertEquals("test_partitioned", tableMetaModel.odpsTableName);
    assertEquals(1, tableMetaModel.columns.size());
    assertEquals("foo", tableMetaModel.columns.get(0).odpsColumnName);
    assertEquals("string", tableMetaModel.columns.get(0).odpsType.toLowerCase());
    assertEquals(1, tableMetaModel.partitionColumns.size());
    assertEquals("bar", tableMetaModel.partitionColumns.get(0).odpsColumnName);
    assertEquals("string", tableMetaModel.partitionColumns.get(0).odpsType.toLowerCase());
    assertEquals(1, tableMetaModel.partitions.size());
    assertEquals("hello_world", tableMetaModel.partitions.get(0).partitionValues.get(0));
  }

  @Test
  public void testGetPendingTablesAfterUpdatePartitionStatus() {
    List<String> partitionValues = new LinkedList<>();
    partitionValues.add("hello_world");
    List<List<String>> partitionValuesList = new LinkedList<>();
    partitionValuesList.add(partitionValues);
    MmaMetaManagerFsImpl.getInstance().updateStatus(DEFAULT_DB, "test_partitioned", partitionValuesList,
                             MmaMetaManager.MigrationStatus.SUCCEEDED);

    List<MetaSource.TableMetaModel> pendingTables = MmaMetaManagerFsImpl.getInstance().getPendingTables();
    assertEquals(2, pendingTables.size());

    for (MetaSource.TableMetaModel tableMetaModel : pendingTables) {
      if ("test_non_partitioned".equals(tableMetaModel.tableName)) {
        assertEquals(DEFAULT_DB, tableMetaModel.odpsProjectName);
        assertEquals("test_non_partitioned", tableMetaModel.odpsTableName);
        assertEquals(1, tableMetaModel.columns.size());
        assertEquals("foo", tableMetaModel.columns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.columns.get(0).odpsType.toLowerCase());
        assertEquals(0, tableMetaModel.partitionColumns.size());
        assertEquals(0, tableMetaModel.partitions.size());
      } else if ("test_partitioned".equals(tableMetaModel.tableName)) {
        assertEquals(DEFAULT_DB, tableMetaModel.odpsProjectName);
        assertEquals("test_partitioned", tableMetaModel.odpsTableName);
        assertEquals(1, tableMetaModel.columns.size());
        assertEquals("foo", tableMetaModel.columns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.columns.get(0).odpsType.toLowerCase());
        assertEquals(1, tableMetaModel.partitionColumns.size());
        assertEquals("bar", tableMetaModel.partitionColumns.get(0).odpsColumnName);
        assertEquals("string", tableMetaModel.partitionColumns.get(0).odpsType.toLowerCase());
        assertEquals(0, tableMetaModel.partitions.size());
      }
    }
  }

  // TODO: test get config
  // TODO: test update partition status results in table status updated
}