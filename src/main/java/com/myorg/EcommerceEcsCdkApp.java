package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.HashMap;
import java.util.Map;

public class EcommerceEcsCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        Environment environment = Environment.builder()
                .account("")
                .region("")
                .build();

        Map<String, String> infraTags = new HashMap<>();
        infraTags.put("team", "NumberOne");
        infraTags.put("cost", "EcommerceInfra");

        EcrStack ecrStack = new EcrStack(app, "Ecr", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build());

        VpcStack vpcStack = new VpcStack(app, "Vpc", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build());

        ClusterStack clusterStack = new ClusterStack(app, "Cluster", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build(), new ClusterStackProps(vpcStack.getVpc()));
        clusterStack.addDependency(vpcStack);

        NlbStack nlbStack = new NlbStack(app, "Nlb", StackProps.builder()
                .env(environment)
                .tags(infraTags)
                .build(), new NlbStackProps(vpcStack.getVpc()));
        nlbStack.addDependency(vpcStack);

        Map<String, String> productsServiceTags = new HashMap<>();
        productsServiceTags.put("team", "NumberOne");
        productsServiceTags.put("cost", "ProductsService");

        ProductsServiceStack productsServiceStack = new ProductsServiceStack(app, "ProductsService",
                StackProps.builder()
                        .env(environment)
                        .tags(productsServiceTags)
                        .build(),
                new ProductsServiceProps(
                        vpcStack.getVpc(),
                        clusterStack.getCluster(),
                        nlbStack.getNetworkLoadBalancer(),
                        nlbStack.getApplicationLoadBalancer(),
                        ecrStack.getProductsServiceRepository()));
        productsServiceStack.addDependency(vpcStack);
        productsServiceStack.addDependency(clusterStack);
        productsServiceStack.addDependency(nlbStack);
        productsServiceStack.addDependency(ecrStack);

        Map<String, String> auditServiceTags = new HashMap<>();
        auditServiceTags.put("team", "NumberOne");
        auditServiceTags.put("cost", "AuditService");

        AuditServiceStack auditServiceStack = new AuditServiceStack(app, "AuditService",
                StackProps.builder()
                        .env(environment)
                        .tags(auditServiceTags)
                        .build(),
                new AuditServiceProps(
                        vpcStack.getVpc(),
                        clusterStack.getCluster(),
                        nlbStack.getNetworkLoadBalancer(),
                        nlbStack.getApplicationLoadBalancer(),
                        ecrStack.getAuditServiceRepository(),
                        productsServiceStack.getProductEventsTopic()));
        auditServiceStack.addDependency(vpcStack);
        auditServiceStack.addDependency(clusterStack);
        auditServiceStack.addDependency(nlbStack);
        auditServiceStack.addDependency(ecrStack);
        auditServiceStack.addDependency(productsServiceStack);

        ApiStack apiStack = new ApiStack(app, "Api",
                StackProps.builder()
                        .env(environment)
                        .tags(infraTags)
                        .build(),
                new ApiStackProps(nlbStack.getVpcLink(), nlbStack.getNetworkLoadBalancer()));
        apiStack.addDependency(nlbStack);

        app.synth();
    }
}