package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AwsLogDriver;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateServiceProps;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.FargateTaskDefinitionProps;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
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
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyProps;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.amazon.awscdk.services.s3.notifications.SqsDestination;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.amazon.awscdk.services.sqs.QueueProps;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InvoicesServiceStack extends Stack {

    public InvoicesServiceStack(final Construct scope, final String id,
                                final StackProps props, InvoicesServiceStackProps invoicesServiceProps){
        super(scope, id, props);

        Table invoicesDdb = new Table(this, "InvoicesDdb", TableProps.builder()
                .tableName("invoices")
                .removalPolicy(RemovalPolicy.DESTROY)
                .partitionKey(Attribute.builder()
                        .name("pk")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("sk")
                        .type(AttributeType.STRING)
                        .build())
                .timeToLiveAttribute("ttl")
                .billingMode(BillingMode.PROVISIONED)
                .readCapacity(1)
                .writeCapacity(1)
                .build());

        Bucket bucket = new Bucket(this, "InvoicesBucket", BucketProps.builder()
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .lifecycleRules(Collections.singletonList(LifecycleRule.builder()
                                .enabled(true)
                                .expiration(Duration.days(1))
                        .build()))
                .build());

        Queue invoicesEventsDlq = new Queue(this, "InvoicesEventsDlq",
                QueueProps.builder()
                        .queueName("invoices-events-dlq")
                        .retentionPeriod(Duration.days(10))
                        .enforceSsl(false)
                        .encryption(QueueEncryption.UNENCRYPTED)
                        .build()
        );
        Queue invoicesEventsQueue = new Queue(this, "InvoicesEventsQueue",
                QueueProps.builder()
                        .queueName("invoices-events")
                        .enforceSsl(false)
                        .encryption(QueueEncryption.UNENCRYPTED)
                        .deadLetterQueue(DeadLetterQueue.builder()
                                .queue(invoicesEventsDlq)
                                .maxReceiveCount(3)
                                .build())
                        .build()
        );
        bucket.addEventNotification(EventType.OBJECT_CREATED, new SqsDestination(invoicesEventsQueue));

        FargateTaskDefinition fargateTaskDefinition = new FargateTaskDefinition(this, "TaskDefinition",
                FargateTaskDefinitionProps.builder()
                        .family("invoices-service")
                        .cpu(512)
                        .memoryLimitMiB(1024)
                        .build());
        fargateTaskDefinition.getTaskRole().addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AWSXrayWriteOnlyAccess"));
        invoicesDdb.grantReadWriteData(fargateTaskDefinition.getTaskRole());
        invoicesEventsQueue.grantConsumeMessages(fargateTaskDefinition.getTaskRole());

        PolicyStatement invoiceBucketPutObjectPolicy = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("s3:PutObject", "s3:DeleteObject", "s3:GetObject"))
                .resources(Collections.singletonList(bucket.getBucketArn() + "/*"))
                .build());
        Policy s3TaskRolePolicy = new Policy(this, "S3TaskRolePolicy", PolicyProps.builder()
                .statements(Collections.singletonList(invoiceBucketPutObjectPolicy))
                .build());
        s3TaskRolePolicy.attachToRole(fargateTaskDefinition.getTaskRole());

        AwsLogDriver logDriver = new AwsLogDriver(AwsLogDriverProps.builder()
                .logGroup(new LogGroup(this, "LogGroup",
                        LogGroupProps.builder()
                                .logGroupName("InvoicesService")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_MONTH)
                                .build()))
                .streamPrefix("InvoicesService")
                .build());

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("SERVER_PORT", "9095");
        envVariables.put("AWS_REGION", this.getRegion());
        envVariables.put("AWS_XRAY_DAEMON_ADDRESS", "0.0.0.0:2000");
        envVariables.put("AWS_XRAY_CONTEXT_MISSING", "IGNORE_ERROR");
        envVariables.put("AWS_XRAY_TRACING_NAME", "invoicesservice");
        envVariables.put("LOGGING_LEVEL_ROOT", "INFO");
        envVariables.put("INVOICES_DDB_NAME", invoicesDdb.getTableName());
        envVariables.put("INVOICES_BUCKET_NAME", bucket.getBucketName());
        envVariables.put("AWS_SQS_QUEUE_INVOICE_EVENTS_URL", invoicesEventsQueue.getQueueName());

        fargateTaskDefinition.addContainer("InvoicesServiceContainer",
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromEcrRepository(invoicesServiceProps.repository(), "1.0.0"))
                        .containerName("invoicesService")
                        .logging(logDriver)
                        .portMappings(Collections.singletonList(PortMapping.builder()
                                .containerPort(9095)
                                .protocol(Protocol.TCP)
                                .build()))
                        .environment(envVariables)
                        .cpu(384)
                        .memoryLimitMiB(896)
                        .build());

        fargateTaskDefinition.addContainer("xray", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/xray/aws-xray-daemon:latest"))
                .containerName("XRayInvoicesService")
                .logging(new AwsLogDriver(AwsLogDriverProps.builder()
                        .logGroup(new LogGroup(this, "XRayLogGroup", LogGroupProps.builder()
                                .logGroupName("XRayInvoicesService")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_MONTH)
                                .build()))
                        .streamPrefix("XRayInvoicesService")
                        .build()))
                .portMappings(Collections.singletonList(PortMapping.builder()
                        .containerPort(2000)
                        .protocol(Protocol.UDP)
                        .build()))
                .cpu(128)
                .memoryLimitMiB(128)
                .build());

        ApplicationListener applicationListener = invoicesServiceProps.applicationLoadBalancer()
                .addListener("InvoicesServiceAlbListener", ApplicationListenerProps.builder()
                        .port(9095)
                        .protocol(ApplicationProtocol.HTTP)
                        .loadBalancer(invoicesServiceProps.applicationLoadBalancer())
                        .build());

        FargateService fargateService = new FargateService(this, "InvoicesService",
                FargateServiceProps.builder()
                        .serviceName("InvoicesService")
                        .cluster(invoicesServiceProps.cluster())
                        .taskDefinition(fargateTaskDefinition)
                        .desiredCount(2)
                        //DO NOT DO THIS IN PRODUCTION!!!
                        //.assignPublicIp(true)
                        .assignPublicIp(false)
                        .build());
        invoicesServiceProps.repository().grantPull(Objects.requireNonNull(fargateTaskDefinition.getExecutionRole()));

        fargateService.getConnections().getSecurityGroups().get(0).addIngressRule(
                Peer.ipv4(invoicesServiceProps.vpc().getVpcCidrBlock()), Port.tcp(9095));

        applicationListener.addTargets("InvoicesServiceAlbTarget",
                AddApplicationTargetsProps.builder()
                        .targetGroupName("invoicesServiceAlb")
                        .port(9095)
                        .protocol(ApplicationProtocol.HTTP)
                        .targets(Collections.singletonList(fargateService))
                        .deregistrationDelay(Duration.seconds(30))
                        .healthCheck(HealthCheck.builder()
                                .enabled(true)
                                .interval(Duration.seconds(30))
                                .timeout(Duration.seconds(10))
                                .path("/actuator/health")
                                .port("9095")
                                .build())
                        .build());

        NetworkListener networkListener = invoicesServiceProps.networkLoadBalancer()
                .addListener("InvoicesServiceNlbListener", BaseNetworkListenerProps.builder()
                        .port(9095)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .build());

        networkListener.addTargets("InvoicesServiceNlbTarget",
                AddNetworkTargetsProps.builder()
                        .port(9095)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .targetGroupName("invoicesServiceNlb")
                        .targets(Collections.singletonList(
                                fargateService.loadBalancerTarget(LoadBalancerTargetOptions.builder()
                                        .containerName("invoicesService")
                                        .containerPort(9095)
                                        .protocol(Protocol.TCP)
                                        .build())
                        ))
                        .build());
    }
}

record InvoicesServiceStackProps(
        Vpc vpc,
        Cluster cluster,
        NetworkLoadBalancer networkLoadBalancer,
        ApplicationLoadBalancer applicationLoadBalancer,
        Repository repository
) {}