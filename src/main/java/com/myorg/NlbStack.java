package com.myorg;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.VpcLink;
import software.amazon.awscdk.services.apigateway.VpcLinkProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancerProps;
import software.constructs.Construct;

import java.util.Collections;

public class NlbStack extends Stack {
    private final VpcLink vpcLink;
    private final NetworkLoadBalancer networkLoadBalancer;
    private final ApplicationLoadBalancer applicationLoadBalancer;

    public NlbStack(final Construct scope,
                        final String id,
                        final StackProps props,
                        NlbStackProps nlbStackProps) {
        super(scope, id, props);

        this.networkLoadBalancer = new NetworkLoadBalancer(this,"Nlb",
                NetworkLoadBalancerProps.builder()
                        .loadBalancerName("EcommerceNlb")
                        .internetFacing(false)
                        .vpc(nlbStackProps.vpc())
                        .build());

        this.vpcLink = new VpcLink(this, "VpcLink",
                VpcLinkProps.builder()
                        .targets(Collections.singletonList(this.networkLoadBalancer))
                        .build());

        this.applicationLoadBalancer = new ApplicationLoadBalancer(this, "Alb",
                ApplicationLoadBalancerProps.builder()
                        .loadBalancerName("EcommerceAlb")
                        .internetFacing(false)
                        .vpc(nlbStackProps.vpc())
                        .build());
    }

    public ApplicationLoadBalancer getApplicationLoadBalancer() {
        return applicationLoadBalancer;
    }

    public VpcLink getVpcLink() {
        return vpcLink;
    }

    public NetworkLoadBalancer getNetworkLoadBalancer() {
        return networkLoadBalancer;
    }
}

record NlbStackProps(
        Vpc vpc
){}