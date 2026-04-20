package com.example.architecture.application;

import com.example.architecture.stacks.MainStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * Entry point for the AWS CDK application.
 *
 * This application provisions a multi-tier, highly available web application
 * infrastructure on AWS including:
 * - VPC with public and private subnets across two AZs
 * - Application Load Balancer (internet-facing)
 * - Auto Scaling Group with EC2 instances
 * - Amazon RDS PostgreSQL (Multi-AZ)
 * - AWS Secrets Manager for credential management
 * - VPC Endpoint for Secrets Manager
 * - KMS keys for encryption
 * - IAM roles with least-privilege policies
 */
public class App {

    public static void main(final String[] args) {
        software.amazon.awscdk.App app = new software.amazon.awscdk.App();

        // Retrieve account and region from environment or CDK context.
        // If not set, CDK will use the default account/region from AWS CLI configuration.
        String account = System.getenv("CDK_DEFAULT_ACCOUNT");
        String region = System.getenv("CDK_DEFAULT_REGION");

        Environment env = Environment.builder()
                .account(account != null ? account : System.getenv("AWS_ACCOUNT_ID"))
                .region(region != null ? region : System.getenv("AWS_DEFAULT_REGION"))
                .build();

        new MainStack(app, "MultiTierWebAppStack", StackProps.builder()
                .env(env)
                .description("Multi-Tier Highly Available Web Application Stack - " +
                        "ALB + EC2 ASG + RDS PostgreSQL + Secrets Manager")
                .build());

        app.synth();
    }
}