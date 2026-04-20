package com.example.architecture.resources;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RdsResources provisions Amazon RDS PostgreSQL with Multi-AZ deployment.
 *
 * Configuration:
 * - Engine: PostgreSQL 15.x
 * - Instance class: db.t3.medium
 * - Storage: gp3, 100 GB minimum, autoscaling enabled (up to 500 GB)
 * - Multi-AZ: enabled (standby replica in Private Subnet B / AZ2)
 * - Backup retention: 7 days
 * - Storage encryption: KMS CMK
 * - DB Subnet Group: spans Private Subnet A (AZ1) and Private Subnet B (AZ2)
 * - Parameter Group: custom PostgreSQL 15 parameter group
 * - Deletion protection: enabled (set to false only for dev/test)
 * - Enhanced monitoring: enabled (60-second interval)
 * - Performance Insights: enabled
 *
 * Assumption: PostgreSQL 15 is used as the database engine.
 * Assumption: Database name is "appdb", master username is "dbadmin".
 */
public class RdsResources extends Construct {

    private final DatabaseInstance dbInstance;

    public RdsResources(
            final Construct scope,
            final String id,
            final IVpc vpc,
            final List<ISubnet> privateSubnets,
            final SecurityGroup rdsSg,
            final IKey rdsKmsKey) {
        super(scope, id);

        // -----------------------------------------------------------------------
        // DB Subnet Group — spans both private subnets (AZ1 and AZ2)
        // -----------------------------------------------------------------------
        SubnetGroup dbSubnetGroup = SubnetGroup.Builder.create(this, "DbSubnetGroup")
                .description("DB subnet group for Multi-Tier App RDS - spans AZ1 and AZ2 private subnets")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(privateSubnets)
                        .build())
                .subnetGroupName("multi-tier-app-db-subnet-group")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // -----------------------------------------------------------------------
        // Custom Parameter Group for PostgreSQL 15 performance tuning
        // -----------------------------------------------------------------------
        ParameterGroup dbParameterGroup = ParameterGroup.Builder.create(this, "DbParameterGroup")
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_15)
                                .build()))
                .description("Custom parameter group for Multi-Tier App PostgreSQL 15")
                .parameters(java.util.Map.of(
                        "log_min_duration_statement", "1000",   // Log queries slower than 1 second
                        "log_connections", "1",
                        "log_disconnections", "1",
                        "log_lock_waits", "1",
                        "shared_preload_libraries", "pg_stat_statements",
                        "pg_stat_statements.track", "all"
                ))
                .build();

        // -----------------------------------------------------------------------
        // RDS PostgreSQL Instance (Multi-AZ)
        // -----------------------------------------------------------------------
        this.dbInstance = DatabaseInstance.Builder.create(this, "RdsPostgresInstance")
                .instanceIdentifier("multi-tier-app-postgres")
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_15)
                                .build()))
                .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MEDIUM))
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(privateSubnets)
                        .build())
                .subnetGroup(dbSubnetGroup)
                .securityGroups(java.util.Collections.singletonList(rdsSg))
                .multiAz(true)
                .databaseName("appdb")
                .credentials(Credentials.fromGeneratedSecret(
                        "dbadmin",
                        CredentialsBaseOptions.builder()
                                .secretName("multi-tier-app/rds/master-credentials")
                                .build()))
                .storageType(StorageType.GP3)
                .allocatedStorage(100)
                .maxAllocatedStorage(500)
                .storageEncrypted(true)
                .storageEncryptionKey(rdsKmsKey)
                .backupRetention(Duration.days(7))
                .preferredBackupWindow("03:00-04:00")
                .preferredMaintenanceWindow("sun:04:00-sun:05:00")
                .parameterGroup(dbParameterGroup)
                .enablePerformanceInsights(true)
                .performanceInsightRetention(PerformanceInsightRetention.DEFAULT)
                .monitoringInterval(Duration.seconds(60))
                .cloudwatchLogsExports(java.util.Arrays.asList(
                        "postgresql",
                        "upgrade"
                ))
                .cloudwatchLogsRetention(software.amazon.awscdk.services.logs.RetentionDays.ONE_MONTH)
                .deletionProtection(true)
                .removalPolicy(RemovalPolicy.SNAPSHOT)
                .autoMinorVersionUpgrade(true)
                .copyTagsToSnapshot(true)
                .build();
    }

    public DatabaseInstance getDbInstance() {
        return dbInstance;
    }
}