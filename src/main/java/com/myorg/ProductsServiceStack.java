package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.CreateAlarmOptions;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.IScalableTableAttribute;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.dynamodb.UtilizationScalingProps;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AwsLogDriver;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateServiceProps;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.FargateTaskDefinitionProps;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddNetworkTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseNetworkListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.logs.FilterPattern;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;
import software.amazon.awscdk.services.logs.MetricFilter;
import software.amazon.awscdk.services.logs.MetricFilterOptions;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.TopicProps;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscriptionProps;
import software.constructs.Construct;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProductsServiceStack extends Stack {
    private final Topic productEventsTopic;

    public ProductsServiceStack(final Construct scope, final String id,
                                final StackProps props, ProductsServiceProps productsServiceProps) {
        super(scope, id, props);

        this.productEventsTopic = new Topic(this, "ProductEventsTopic", TopicProps.builder()
                .displayName("Product events topic")
                .topicName("product-events")
                .build());

            Table productsDdb = new Table(this, "ProductsDdb",
            TableProps.builder()
                    .partitionKey(Attribute.builder()
                            .name("id")
                            .type(AttributeType.STRING)
                            .build())
                    .tableName("products")
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .billingMode(BillingMode.PROVISIONED)
                    .readCapacity(1)
                    .writeCapacity(1)
                    .build());

            productsDdb.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                            .indexName("codeInx")
                            .partitionKey(Attribute.builder()
                                    .name("code")
                                    .type(AttributeType.STRING)
                                    .build())
                            .projectionType(ProjectionType.KEYS_ONLY)
                            .readCapacity(1)
                            .writeCapacity(1)
                    .build());

        IScalableTableAttribute readScale = productsDdb.autoScaleReadCapacity(
                software.amazon.awscdk.services.dynamodb.EnableScalingProps.builder()
                .maxCapacity(4)
                .minCapacity(1)
                .build());
        readScale.scaleOnUtilization(UtilizationScalingProps.builder()
                        .targetUtilizationPercent(10)
                        .scaleInCooldown(Duration.seconds(20))
                        .scaleOutCooldown(Duration.seconds(20))
                .build());

        IScalableTableAttribute writeScale = productsDdb.autoScaleWriteCapacity(
                software.amazon.awscdk.services.dynamodb.EnableScalingProps.builder()
                        .maxCapacity(4)
                        .minCapacity(1)
                        .build());
        writeScale.scaleOnUtilization(UtilizationScalingProps.builder()
                .targetUtilizationPercent(10)
                .scaleInCooldown(Duration.seconds(20))
                .scaleOutCooldown(Duration.seconds(20))
                .build());

        IScalableTableAttribute readIndexScale = productsDdb.autoScaleGlobalSecondaryIndexReadCapacity(
                "codeInx", software.amazon.awscdk.services.dynamodb.EnableScalingProps.builder()
                        .maxCapacity(4)
                        .minCapacity(1)
                        .build());
        readIndexScale.scaleOnUtilization(UtilizationScalingProps.builder()
                .targetUtilizationPercent(10)
                .scaleInCooldown(Duration.seconds(20))
                .scaleOutCooldown(Duration.seconds(20))
                .build());



        FargateTaskDefinition fargateTaskDefinition = new FargateTaskDefinition(this, "TaskDefinition",
                FargateTaskDefinitionProps.builder()
                        .family("products-service")
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .build());
        productsDdb.grantReadWriteData(fargateTaskDefinition.getTaskRole());
        this.productEventsTopic.grantPublish(fargateTaskDefinition.getTaskRole());

        LogGroup logGroup = new LogGroup(this, "LogGroup",
                LogGroupProps.builder()
                        .logGroupName("ProductsService")
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .retention(RetentionDays.ONE_MONTH)
                        .build());

        AwsLogDriver logDriver = new AwsLogDriver(AwsLogDriverProps.builder()
                .logGroup(logGroup)
                .streamPrefix("ProductsService")
                .build());

        //Metric
        MetricFilter productNotFoundMetricFilter = logGroup.addMetricFilter("ProductWithSameCode", MetricFilterOptions.builder()
                .filterPattern(FilterPattern.literal("Can not create a product with same code."))
                .metricName("ProductWithSameCode")
                .metricNamespace("Product")
                .build());

        //Alarm
        Alarm productNotFoundAlarm = productNotFoundMetricFilter.metric()
                .with(MetricOptions.builder()
                        .period(Duration.minutes(2))
                        .statistic("Sum")
                        .build())
                .createAlarm(this, "ProductWithSameCodeAlarm", CreateAlarmOptions.builder()
                        .alarmName("ProductWithSameCodeAlarm")
                        .alarmDescription("Some product was not created due code duplicity")
                        .evaluationPeriods(1)
                        .threshold(2)
                        .actionsEnabled(true)
                        .treatMissingData(TreatMissingData.NOT_BREACHING)
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .build());

        //Action
        Topic productAlarmsTopic = new Topic(this, "ProductsAlarmsTopic", TopicProps.builder()
                .displayName("Product alarms topic")
                .topicName("product-alarms")
                .build());
        productAlarmsTopic.addSubscription(new EmailSubscription("email@gmail.com",
                EmailSubscriptionProps.builder()
                        .json(false)
                        .build()));
        productNotFoundAlarm.addAlarmAction(new SnsAction(productAlarmsTopic));

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("SERVER_PORT", "8080");
        envVariables.put("AWS_PRODUCTSDDB_NAME", productsDdb.getTableName());
        envVariables.put("AWS_SNS_TOPIC_PRODUCT_EVENTS", this.productEventsTopic.getTopicArn());
        envVariables.put("AWS_REGION", this.getRegion());
        envVariables.put("AWS_XRAY_DAEMON_ADDRESS", "0.0.0.0:2000");
        envVariables.put("AWS_XRAY_CONTEXT_MISSING","IGNORE_ERROR");
        envVariables.put("AWS_XRAY_TRACING_NAME","productsservice");
        envVariables.put("LOGGING_LEVEL_ROOT", "INFO");


        fargateTaskDefinition.addContainer("ProductsServiceContainer",
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromEcrRepository(productsServiceProps.repository(), "1.8.0"))
                        .containerName("productsService")
                        .logging(logDriver)
                        .portMappings(Collections.singletonList(PortMapping.builder()
                                .containerPort(8080)
                                .protocol(Protocol.TCP)
                                .build()))
                        .environment(envVariables)
                        .cpu(384)
                        .memoryLimitMiB(896)
                        .build());

        fargateTaskDefinition.addContainer("xray", ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("public.ecr.aws/xray/aws-xray-daemon:latest"))
                        .containerName("XRayProductsService")
                        .logging(new AwsLogDriver(AwsLogDriverProps.builder()
                                .logGroup(new LogGroup(this,"XRayLogGroup", LogGroupProps.builder()
                                        .logGroupName("XRayProductsService")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_MONTH)
                                        .build()))
                                .streamPrefix("XRayProductsService")
                                .build()))
                        .portMappings(Collections.singletonList(PortMapping.builder()
                                        .containerPort(2000)
                                        .protocol(Protocol.UDP)
                                .build()))
                        .cpu(128)
                        .memoryLimitMiB(128)
                .build());
        fargateTaskDefinition.getTaskRole().addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AWSXrayWriteOnlyAccess"));

        ApplicationListener applicationListener = productsServiceProps.applicationLoadBalancer()
                .addListener("ProductsServiceAlbListener", ApplicationListenerProps.builder()
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .loadBalancer(productsServiceProps.applicationLoadBalancer())
                        .build());

        FargateService fargateService = new FargateService(this, "ProductsService",
                FargateServiceProps.builder()
                        .serviceName("ProductsService")
                        .cluster(productsServiceProps.cluster())
                        .taskDefinition(fargateTaskDefinition)
                        .desiredCount(2)
                        //DO NOT DO THIS IN PRODUCTION!!!
                        //.assignPublicIp(true)
                        .assignPublicIp(false)
                        .build());
        productsServiceProps.repository().grantPull(Objects.requireNonNull(fargateTaskDefinition.getExecutionRole()));
        fargateService.getConnections().getSecurityGroups().get(0).addIngressRule(Peer.anyIpv4(), Port.tcp(8080));

        applicationListener.addTargets("ProductsServiceAlbTarget",
                AddApplicationTargetsProps.builder()
                        .targetGroupName("productsServiceAlb")
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .targets(Collections.singletonList(fargateService))
                        .deregistrationDelay(Duration.seconds(30))
                        .healthCheck(HealthCheck.builder()
                                .enabled(true)
                                .interval(Duration.seconds(30))
                                .timeout(Duration.seconds(10))
                                .path("/actuator/health")
                                .port("8080")
                                .build())
                        .build()
        );

        NetworkListener networkListener = productsServiceProps.networkLoadBalancer()
                .addListener("ProductsServiceNlbListener", BaseNetworkListenerProps.builder()
                        .port(8080)
                        .protocol(
                                software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP
                        )
                        .build());

        networkListener.addTargets("ProductsServiceNlbTarget",
                AddNetworkTargetsProps.builder()
                        .port(8080)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .targetGroupName("productsServiceNlb")
                        .targets(Collections.singletonList(
                                fargateService.loadBalancerTarget(LoadBalancerTargetOptions.builder()
                                        .containerName("productsService")
                                        .containerPort(8080)
                                        .protocol(Protocol.TCP)
                                        .build())
                        ))
                        .build()
        );

        ScalableTaskCount scalableTaskCount = fargateService.autoScaleTaskCount(
                EnableScalingProps.builder()
                .maxCapacity(4)
                .minCapacity(2)
                .build());
        scalableTaskCount.scaleOnCpuUtilization("ProductsServiceAutoScaling",
                CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(80)
                        .scaleInCooldown(Duration.seconds(60))
                        .scaleOutCooldown(Duration.seconds(60))
                        .build());
    }

    public Topic getProductEventsTopic() {
        return productEventsTopic;
    }
}

record ProductsServiceProps(
        Vpc vpc,
        Cluster cluster,
        NetworkLoadBalancer networkLoadBalancer,
        ApplicationLoadBalancer applicationLoadBalancer,
        Repository repository
){}