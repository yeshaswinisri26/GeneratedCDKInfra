package com.example.architecture.resources;

import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.kms.IKey;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * IamResources provisions IAM roles and instance profiles for EC2 instances.
 *
 * EC2 Instance Role permissions (least privilege):
 * - secretsmanager:GetSecretValue — scoped to specific secret ARN (attached after secret creation)
 * - secretsmanager:DescribeSecret — scoped to specific secret ARN
 * - kms:Decrypt — scoped to Secrets Manager KMS key (for decrypting secrets)
 * - cloudwatch:PutMetricData — for custom metrics
 * - logs:CreateLogGroup, logs:CreateLogStream, logs:PutLogEvents — for CloudWatch Logs
 * - ssm:GetParameter — for SSM Parameter Store access (e.g., AMI lookup)
 * - ssmmessages:*, ec2messages:* — for SSM Session Manager (replaces SSH)
 * - ec2:DescribeInstances — for instance metadata
 */
public class IamResources extends Construct {

    private final Role ec2InstanceRole;
    private final CfnInstanceProfile ec2InstanceProfile;

    public IamResources(final Construct scope, final String id, final IKey secretsKmsKey) {
        super(scope, id);

        // -----------------------------------------------------------------------
        // EC2 Instance Role
        // -----------------------------------------------------------------------
        this.ec2InstanceRole = Role.Builder.create(this, "Ec2InstanceRole")
                .roleName("multi-tier-app-ec2-instance-role")
                .description("IAM role for EC2 application instances - Secrets Manager, CloudWatch, SSM access")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .build();

        // AWS managed policy for SSM Session Manager (replaces SSH)
        this.ec2InstanceRole.addManagedPolicy(
                ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
        );

        // CloudWatch Agent policy for log and metric shipping
        this.ec2InstanceRole.addManagedPolicy(
                ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
        );

        // KMS Decrypt permission for Secrets Manager KMS key
        this.ec2InstanceRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AllowKmsDecryptForSecrets")
                .effect(Effect.ALLOW)
                .actions(Collections.singletonList("kms:Decrypt"))
                .resources(Collections.singletonList(secretsKmsKey.getKeyArn()))
                .build());

        // CloudWatch Logs permissions (fine-grained)
        this.ec2InstanceRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AllowCloudWatchLogs")
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogStreams",
                        "logs:DescribeLogGroups"
                ))
                .resources(Collections.singletonList("arn:aws:logs:*:*:*"))
                .build());

        // CloudWatch Metrics permissions
        this.ec2InstanceRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AllowCloudWatchMetrics")
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                ))
                .resources(Collections.singletonList("*"))
                .build());

        // EC2 metadata permissions (for CloudWatch Agent)
        this.ec2InstanceRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AllowEc2Metadata")
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "ec2:DescribeInstances",
                        "ec2:DescribeTags"
                ))
                .resources(Collections.singletonList("*"))
                .build());

        // -----------------------------------------------------------------------
        // EC2 Instance Profile
        // -----------------------------------------------------------------------
        this.ec2InstanceProfile = CfnInstanceProfile.Builder.create(this, "Ec2InstanceProfile")
                .instanceProfileName("multi-tier-app-ec2-instance-profile")
                .roles(Collections.singletonList(this.ec2InstanceRole.getRoleName()))
                .build();
    }

    /**
     * Attaches a least-privilege policy to allow GetSecretValue on the specific secret.
     * Called after the secret is created to scope the ARN precisely.
     *
     * @param secret The Secrets Manager secret for RDS credentials
     */
    public void attachSecretPolicy(final ISecret secret) {
        this.ec2InstanceRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AllowGetRdsSecret")
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "secretsmanager:GetSecretValue",
                        "secretsmanager:DescribeSecret"
                ))
                .resources(Collections.singletonList(secret.getSecretArn()))
                .build());
    }

    public Role getEc2InstanceRole() {
        return ec2InstanceRole;
    }

    public CfnInstanceProfile getEc2InstanceProfile() {
        return ec2InstanceProfile;
    }
}