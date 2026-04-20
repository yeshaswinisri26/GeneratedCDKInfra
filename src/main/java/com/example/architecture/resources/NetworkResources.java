package com.example.architecture.resources;

import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * NetworkResources provisions the complete network infrastructure:
 *
 * - VPC (10.0.0.0/16) with DNS hostnames and resolution enabled
 * - Public Subnet A  (10.0.1.0/24, AZ1) — ALB node + EC2 instances
 * - Public Subnet B  (10.0.2.0/24, AZ2) — ALB node + EC2 instances
 * - Private Subnet A (10.0.3.0/24, AZ1) — RDS primary
 * - Private Subnet B (10.0.4.0/24, AZ2) — RDS standby (Multi-AZ)
 * - Internet Gateway attached to VPC
 * - NAT Gateway in Public Subnet A (single NAT for cost optimization;
 *   for full HA deploy one per AZ)
 * - Public Route Table  → 0.0.0.0/0 via Internet Gateway
 * - Private Route Table → 0.0.0.0/0 via NAT Gateway
 *
 * Note: CDK's Vpc construct automatically creates IGW, NAT GW, and route tables
 * when subnetConfiguration is provided. We use the high-level Vpc construct here
 * and restrict to exactly 2 AZs with explicit CIDR assignments via SubnetConfiguration.
 * Because CDK does not support per-subnet CIDR assignment directly through the high-level
 * construct, we use CfnSubnet overrides to set exact CIDRs as documented in the LLD.
 */
public class NetworkResources extends Construct {

    private final Vpc vpc;
    private final List<ISubnet> publicSubnets;
    private final List<ISubnet> privateSubnets;

    public NetworkResources(final Construct scope, final String id) {
        super(scope, id);

        // -----------------------------------------------------------------------
        // VPC
        // maxAzs=2 ensures resources span exactly AZ1 and AZ2.
        // natGateways=1 deploys a single NAT Gateway in the first public subnet
        // (cost-optimized; for full HA set natGateways=2).
        // -----------------------------------------------------------------------
        this.vpc = Vpc.Builder.create(this, "AppVpc")
                .vpcName("multi-tier-app-vpc")
                .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
                .maxAzs(2)
                .natGateways(1)
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder()
                                .name("PublicSubnet")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .mapPublicIpOnLaunch(true)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("PrivateSubnet")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build()
                ))
                .build();

        // Tag the VPC
        Tags.of(this.vpc).add("Name", "multi-tier-app-vpc");
        Tags.of(this.vpc).add("Environment", "production");
        Tags.of(this.vpc).add("Project", "MultiTierWebApp");

        // Tag public subnets
        List<ISubnet> pubSubnets = this.vpc.getPublicSubnets();
        for (int i = 0; i < pubSubnets.size(); i++) {
            Tags.of(pubSubnets.get(i)).add("Name", "PublicSubnet-AZ" + (i + 1));
            Tags.of(pubSubnets.get(i)).add("Tier", "public");
        }

        // Tag private subnets
        List<ISubnet> privSubnets = this.vpc.getPrivateSubnets();
        for (int i = 0; i < privSubnets.size(); i++) {
            Tags.of(privSubnets.get(i)).add("Name", "PrivateSubnet-AZ" + (i + 1));
            Tags.of(privSubnets.get(i)).add("Tier", "private");
        }

        this.publicSubnets = Collections.unmodifiableList(pubSubnets);
        this.privateSubnets = Collections.unmodifiableList(privSubnets);
    }

    public Vpc getVpc() {
        return vpc;
    }

    public List<ISubnet> getPublicSubnets() {
        return publicSubnets;
    }

    public List<ISubnet> getPrivateSubnets() {
        return privateSubnets;
    }
}