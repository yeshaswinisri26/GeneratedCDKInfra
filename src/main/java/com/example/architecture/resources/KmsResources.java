package com.example.architecture.resources;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.kms.KeySpec;
import software.amazon.awscdk.services.kms.KeyUsage;
import software.constructs.Construct;

/**
 * KmsResources provisions AWS KMS Customer Managed Keys (CMKs) for:
 * - RDS storage encryption
 * - Secrets Manager secret encryption
 *
 * Separate keys are used per service following the principle of least privilege
 * and to allow independent key rotation and access control policies.
 */
public class KmsResources extends Construct {

    private final Key rdsKmsKey;
    private final Key secretsKmsKey;

    public KmsResources(final Construct scope, final String id) {
        super(scope, id);

        // KMS key for RDS storage encryption
        this.rdsKmsKey = Key.Builder.create(this, "RdsKmsKey")
                .description("CMK for Amazon RDS storage encryption - Multi-Tier Web App")
                .enableKeyRotation(true)
                .keySpec(KeySpec.SYMMETRIC_DEFAULT)
                .keyUsage(KeyUsage.ENCRYPT_DECRYPT)
                .pendingWindow(Duration.days(7))
                .alias("alias/multi-tier-app/rds")
                .build();

        // KMS key for Secrets Manager secret encryption
        this.secretsKmsKey = Key.Builder.create(this, "SecretsKmsKey")
                .description("CMK for AWS Secrets Manager encryption - Multi-Tier Web App")
                .enableKeyRotation(true)
                .keySpec(KeySpec.SYMMETRIC_DEFAULT)
                .keyUsage(KeyUsage.ENCRYPT_DECRYPT)
                .pendingWindow(Duration.days(7))
                .alias("alias/multi-tier-app/secrets")
                .build();
    }

    public Key getRdsKmsKey() {
        return rdsKmsKey;
    }

    public Key getSecretsKmsKey() {
        return secretsKmsKey;
    }
}