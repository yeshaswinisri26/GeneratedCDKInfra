package com.example.architecture.resources;

import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

/**
 * SecurityGroupResources provisions all security groups for the multi-tier application:
 *
 * sg-alb (ALB Security Group):
 *   Inbound:  TCP 443 from 0.0.0.0/0 (HTTPS)
 *   Inbound:  TCP 80  from 0.0.0.0/0 (HTTP redirect)
 *   Outbound: TCP 8080 to sg-ec2 (forward to application)
 *
 * sg-ec2 (EC2 Security Group):
 *   Inbound:  TCP 8080 from sg-alb (application traffic from ALB only)
 *   Outbound: TCP 5432 to sg-rds (PostgreSQL queries)
 *   Outbound: TCP 443  to 0.0.0.0/0 (Secrets Manager, CloudWatch, AWS APIs)
 *   Note: SSH (port 22) is intentionally omitted; use SSM Session Manager.
 *
 * sg-rds (RDS Security Group):
 *   Inbound:  TCP 5432 from sg-ec2 (PostgreSQL from EC2 only)
 *   Outbound: None (no outbound rules required for RDS)
 *
 * sg-vpce (VPC Endpoint Security Group):
 *   Inbound:  TCP 443 from sg-ec2 (HTTPS from EC2 to Secrets Manager endpoint)
 *   Outbound: None required
 */
public class SecurityGroupResources extends Construct {

    private static final int APP_PORT = 8080;
    private static final int DB_PORT = 5432;  // PostgreSQL

    private final SecurityGroup albSg;
    private final SecurityGroup ec2Sg;
    private final SecurityGroup rdsSg;
    private final SecurityGroup vpcEndpointSg;

    public SecurityGroupResources(final Construct scope, final String id, final IVpc vpc) {
        super(scope, id);

        // -----------------------------------------------------------------------
        // ALB Security Group
        // -----------------------------------------------------------------------
        this.albSg = SecurityGroup.Builder.create(this, "AlbSecurityGroup")
                .vpc(vpc)
                .securityGroupName("sg-alb-multi-tier-app")
                .description("Security group for Application Load Balancer - allows inbound HTTP/HTTPS from internet")
                .allowAllOutbound(false)
                .build();

        // Allow inbound HTTPS from internet
        this.albSg.addIngressRule(
                Peer.anyIpv4(),
                Port.tcp(443),
                "Allow HTTPS inbound from internet"
        );

        // Allow inbound HTTP from internet (for redirect to HTTPS)
        this.albSg.addIngressRule(
                Peer.anyIpv4(),
                Port.tcp(80),
                "Allow HTTP inbound from internet for redirect to HTTPS"
        );

        // -----------------------------------------------------------------------
        // EC2 Security Group
        // -----------------------------------------------------------------------
        this.ec2Sg = SecurityGroup.Builder.create(this, "Ec2SecurityGroup")
                .vpc(vpc)
                .securityGroupName("sg-ec2-multi-tier-app")
                .description("Security group for EC2 application instances - allows traffic from ALB only")
                .allowAllOutbound(false)
                .build();

        // Allow outbound HTTPS to AWS APIs (Secrets Manager, CloudWatch, SSM)
        this.ec2Sg.addEgressRule(
                Peer.anyIpv4(),
                Port.tcp(443),
                "Allow HTTPS outbound to AWS APIs (Secrets Manager, CloudWatch, SSM)"
        );

        // Allow outbound to RDS on PostgreSQL port
        this.ec2Sg.addEgressRule(
                Peer.anyIpv4(),
                Port.tcp(DB_PORT),
                "Allow outbound to RDS PostgreSQL port"
        );

        // -----------------------------------------------------------------------
        // RDS Security Group
        // -----------------------------------------------------------------------
        this.rdsSg = SecurityGroup.Builder.create(this, "RdsSecurityGroup")
                .vpc(vpc)
                .securityGroupName("sg-rds-multi-tier-app")
                .description("Security group for RDS PostgreSQL - allows inbound from EC2 instances only")
                .allowAllOutbound(false)
                .build();

        // -----------------------------------------------------------------------
        // VPC Endpoint Security Group
        // -----------------------------------------------------------------------
        this.vpcEndpointSg = SecurityGroup.Builder.create(this, "VpcEndpointSecurityGroup")
                .vpc(vpc)
                .securityGroupName("sg-vpce-multi-tier-app")
                .description("Security group for VPC Interface Endpoints (Secrets Manager, SSM, CloudWatch)")
                .allowAllOutbound(false)
                .build();

        // -----------------------------------------------------------------------
        // Cross-security-group rules (added after all SGs are created to avoid circular refs)
        // -----------------------------------------------------------------------

        // ALB → EC2 on application port
        this.albSg.addEgressRule(
                Peer.securityGroupId(this.ec2Sg.getSecurityGroupId()),
                Port.tcp(APP_PORT),
                "Allow ALB to forward traffic to EC2 on application port"
        );

        // EC2 ← ALB on application port
        this.ec2Sg.addIngressRule(
                Peer.securityGroupId(this.albSg.getSecurityGroupId()),
                Port.tcp(APP_PORT),
                "Allow inbound from ALB on application port only"
        );

        // RDS ← EC2 on PostgreSQL port
        this.rdsSg.addIngressRule(
                Peer.securityGroupId(this.ec2Sg.getSecurityGroupId()),
                Port.tcp(DB_PORT),
                "Allow inbound PostgreSQL from EC2 instances only"
        );

        // VPC Endpoint ← EC2 on HTTPS
        this.vpcEndpointSg.addIngressRule(
                Peer.securityGroupId(this.ec2Sg.getSecurityGroupId()),
                Port.tcp(443),
                "Allow HTTPS from EC2 instances to VPC Interface Endpoints"
        );
    }

    public SecurityGroup getAlbSg() {
        return albSg;
    }

    public SecurityGroup getEc2Sg() {
        return ec2Sg;
    }

    public SecurityGroup getRdsSg() {
        return rdsSg;
    }

    public SecurityGroup getVpcEndpointSg() {
        return vpcEndpointSg;
    }
}