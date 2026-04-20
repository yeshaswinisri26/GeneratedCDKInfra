# AWS Multi-Tier Web Application — CDK Java Project

## Overview

This AWS CDK v2 Java project provisions a production-grade, multi-tier, highly available web application infrastructure on AWS. The architecture follows a classical three-tier pattern:

- **Presentation / Ingress Tier:** Internet Gateway + Application Load Balancer (ALB)
- **Application / Compute Tier:** Auto-Scaled Amazon EC2 instances across two Availability Zones
- **Data Tier:** Amazon RDS (PostgreSQL) in private subnets with Multi-AZ standby

---

## Architecture Components

### Networking
- **VPC** with CIDR `10.0.0.0/16`, DNS hostnames and resolution enabled
- **Public Subnet A** (`10.0.1.0/24`, AZ1) — ALB node + EC2 instances
- **Public Subnet B** (`10.0.2.0/24`, AZ2) — ALB node + EC2 instances
- **Private Subnet A** (`10.0.3.0/24`, AZ1) — RDS primary instance
- **Private Subnet B** (`10.0.4.0/24`, AZ2) — RDS standby (Multi-AZ)
- **Internet Gateway** attached to VPC
- **NAT Gateway** in Public Subnet A for private subnet outbound access
- **Route Tables** for public (IGW route) and private (NAT route) subnets
- **Security Groups** for ALB, EC2, and RDS with least-privilege rules
- **VPC Endpoint** for AWS Secrets Manager (Interface endpoint) to avoid secrets traffic traversing the internet

### Compute
- **EC2 Launch Template** using Amazon Linux 2 AMI, `t3.medium` instance type
- **Auto Scaling Group** with min=2, desired=2, max=10 across both AZs
- **Target Tracking Scaling Policy** targeting 60% average CPU utilization
- **ELB health checks** with 300-second grace period
- **IAM Instance Profile** with least-privilege permissions for Secrets Manager and CloudWatch

### Load Balancing
- **Application Load Balancer** (internet-facing) across both public subnets
- **HTTP Listener (port 80)** with redirect to HTTPS
- **HTTPS Listener (port 443)** forwarding to EC2 target group
- **Target Group** with `/health` health check path, 30s interval, 2 healthy / 3 unhealthy thresholds

### Database
- **Amazon RDS PostgreSQL** (`db.t3.medium`) in private subnets
- **Multi-AZ** deployment enabled for high availability
- **Storage encryption** using AWS KMS
- **Automated backups** with 7-day retention
- **DB Subnet Group** spanning Private Subnet A and B

### Secrets Management
- **AWS Secrets Manager** secret for RDS credentials (username, password, host, port, db name)
- **Automatic rotation** every 30 days using a Lambda rotation function (managed by CDK/RDS integration)
- **KMS Customer Managed Key (CMK)** for secret encryption
- **Resource policy** restricting access to the EC2 instance profile role only

### Observability
- **CloudWatch** log groups for EC2 application logs (via CloudWatch Agent — configured in user data)
- **CloudWatch** metrics for ASG scaling activity

---

## Prerequisites

1. **Java 11+** installed
2. **Maven 3.8+** installed
3. **Node.js 18+** installed (required by CDK CLI)
4. **AWS CDK CLI v2** installed: `npm install -g aws-cdk`
5. **AWS CLI** configured with appropriate credentials and region
6. **CDK Bootstrap** executed in target account/region: `cdk bootstrap`

---

## SSL/TLS Certificate

**Assumption:** The HTTPS listener on the ALB requires an ACM certificate. This project expects the certificate ARN to be provided via the environment variable `ACM_CERTIFICATE_ARN` or the CDK context key `acmCertificateArn`.

To set via context:
```bash
cdk deploy -c acmCertificateArn=arn:aws:acm:us-east-1:123456789012:certificate/xxxx
```

If no certificate ARN is provided, the HTTPS listener will be skipped and only HTTP (port 80) will be configured. This is flagged in the code.

---

## Build and Deploy

```bash
# Build the project
mvn clean package

# Synthesize CloudFormation template
cdk synth

# Deploy the stack
cdk deploy

# Deploy with ACM certificate
cdk deploy -c acmCertificateArn=arn:aws:acm:REGION:ACCOUNT:certificate/CERT-ID
```

---

## Assumptions and Design Decisions

| Item | Assumption / Decision |
|---|---|
| **Database Engine** | PostgreSQL 15.x (most common; easily changed in `RdsResources.java`) |
| **Instance Type** | `t3.medium` for EC2 and `db.t3.medium` for RDS (suitable for moderate workloads) |
| **Application Port** | `8080` (EC2 instances listen on port 8080; ALB forwards to this port) |
| **AMI** | Latest Amazon Linux 2 AMI (fetched dynamically via SSM parameter) |
| **SSH Access** | SSH (port 22) is disabled in the EC2 security group; AWS Systems Manager Session Manager is the preferred access method |
| **NAT Gateway** | Single NAT Gateway in Public Subnet A (cost-optimized; for full HA, deploy one per AZ) |
| **Secrets Manager VPC Endpoint** | Interface VPC endpoint deployed in private subnets to keep secrets traffic within AWS network |
| **ACM Certificate** | Must be pre-provisioned and ARN provided via CDK context; HTTPS listener is conditional |
| **User Data** | Minimal bootstrap script included; replace with actual application deployment logic |
| **KMS Keys** | Separate CMKs for RDS storage encryption and Secrets Manager secret encryption |
| **RDS Database Name** | `appdb` (default; configurable) |
| **RDS Master Username** | `dbadmin` (default; configurable) |
| **CloudWatch Agent** | User data installs and configures CloudWatch Agent for application log shipping |

---

## Project Structure

```
aws-cdk-java-project/
├── pom.xml
├── cdk.json
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── architecture/
                        ├── application/
                        │   └── App.java
                        ├── stacks/
                        │   └── MainStack.java
                        └── resources/
                            ├── NetworkResources.java
                            ├── SecurityGroupResources.java
                            ├── IamResources.java
                            ├── AlbResources.java
                            ├── ComputeResources.java
                            ├── RdsResources.java
                            ├── SecretsResources.java
                            └── S3Resources.java
```

---

## Cleanup

```bash
cdk destroy
```

> **Warning:** Destroying the stack will delete the RDS instance and all data. Ensure backups are taken before destroying.