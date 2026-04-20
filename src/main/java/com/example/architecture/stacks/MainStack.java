package com.example.architecture.stacks;

import com.example.architecture.resources.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

/**
 * MainStack orchestrates all infrastructure resources for the multi-tier web application.
 *
 * Resource creation order respects dependencies:
 * 1. KMS Keys (used by RDS and Secrets Manager)
 * 2. Network (VPC, subnets, IGW, NAT, route tables)
 * 3. Security Groups (depend on VPC)
 * 4. IAM Roles (depend on KMS key ARNs)
 * 5. Secrets Manager Secret (depends on KMS, RDS not yet created — secret created first, attached after)
 * 6. RDS (depends on network, security groups, KMS)
 * 7. ALB (depends on network, security groups)
 * 8. Compute / ASG (depends on network, security groups, IAM, ALB target group, secret ARN)
 * 9. VPC Endpoints (depends on VPC, security groups, route tables)
 */
public class MainStack extends Stack {

    public MainStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // -----------------------------------------------------------------------
        // 1. KMS Resources
        // -----------------------------------------------------------------------
        KmsResources kmsResources = new KmsResources(this, "KmsResources");

        // -----------------------------------------------------------------------
        // 2. Network Resources
        // VPC, subnets, Internet Gateway, NAT Gateway, route tables
        // -----------------------------------------------------------------------
        NetworkResources networkResources = new NetworkResources(this, "NetworkResources");

        // -----------------------------------------------------------------------
        // 3. Security Group Resources
        // ALB SG, EC2 SG, RDS SG, VPC Endpoint SG
        // -----------------------------------------------------------------------
        SecurityGroupResources sgResources = new SecurityGroupResources(
                this, "SecurityGroupResources", networkResources.getVpc());

        // -----------------------------------------------------------------------
        // 4. IAM Resources
        // EC2 instance profile role with Secrets Manager + CloudWatch permissions
        // -----------------------------------------------------------------------
        IamResources iamResources = new IamResources(
                this, "IamResources", kmsResources.getSecretsKmsKey());

        // -----------------------------------------------------------------------
        // 5. RDS Resources
        // PostgreSQL Multi-AZ instance in private subnets
        // -----------------------------------------------------------------------
        RdsResources rdsResources = new RdsResources(
                this, "RdsResources",
                networkResources.getVpc(),
                networkResources.getPrivateSubnets(),
                sgResources.getRdsSg(),
                kmsResources.getRdsKmsKey());

        // -----------------------------------------------------------------------
        // 6. Secrets Manager Resources
        // RDS credentials secret with KMS encryption and rotation
        // -----------------------------------------------------------------------
        SecretsResources secretsResources = new SecretsResources(
                this, "SecretsResources",
                kmsResources.getSecretsKmsKey(),
                iamResources.getEc2InstanceRole(),
                rdsResources.getDbInstance());

        // -----------------------------------------------------------------------
        // 7. Update IAM role with specific secret ARN (least privilege)
        // -----------------------------------------------------------------------
        iamResources.attachSecretPolicy(secretsResources.getDbSecret());

        // -----------------------------------------------------------------------
        // 8. ALB Resources
        // Internet-facing ALB, listeners, target group
        // -----------------------------------------------------------------------
        AlbResources albResources = new AlbResources(
                this, "AlbResources",
                networkResources.getVpc(),
                networkResources.getPublicSubnets(),
                sgResources.getAlbSg(),
                this.getNode().tryGetContext("acmCertificateArn") != null
                        ? (String) this.getNode().tryGetContext("acmCertificateArn")
                        : null);

        // -----------------------------------------------------------------------
        // 9. Compute Resources
        // Launch Template, Auto Scaling Group
        // -----------------------------------------------------------------------
        new ComputeResources(
                this, "ComputeResources",
                networkResources.getVpc(),
                networkResources.getPublicSubnets(),
                sgResources.getEc2Sg(),
                iamResources.getEc2InstanceProfile(),
                albResources.getTargetGroup(),
                secretsResources.getDbSecret());

        // -----------------------------------------------------------------------
        // 10. VPC Endpoint Resources
        // Interface endpoint for Secrets Manager in private subnets
        // -----------------------------------------------------------------------
        new VpcEndpointResources(
                this, "VpcEndpointResources",
                networkResources.getVpc(),
                networkResources.getPrivateSubnets(),
                sgResources.getVpcEndpointSg());
    }
}