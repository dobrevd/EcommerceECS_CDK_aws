# Ecommerce Microservices Project

## Overview

This project is an e-commerce platform built using a microservices architecture. The system consists of three main microservices: [ProductsService](https://github.com/dobrevd/productservice_aws), [AuditService](https://github.com/dobrevd/auditservice_aws), and [InvoicesService](https://github.com/dobrevd/invoiceservice_aws).This project demonstrates an infrastructure-as-code (IaC) solution for deploying a scalable ecommerce application using the AWS Cloud Development Kit (CDK) with Java. Each microservice is designed to handle specific aspects of the platform, ensuring modularity, scalability, and maintainability.


### **[ProductsService](https://github.com/dobrevd/productservice_aws)**

*Description will be added.*


### **[AuditService](https://github.com/dobrevd/auditservice_aws)**

*Description will be added.*

### **[InvoicesService](https://github.com/dobrevd/invoiceservice_aws)**  
This is a cloud-based microservice designed to handle the import, processing, and management of invoice files for an e-commerce backend system. This service integrates with a legacy system to enable efficient storage and retrieval of invoices, leveraging AWS technologies such as DynamoDB, S3, SQS, and ECS Fargate for scalability, reliability, and performance.

## Infrastructure

The project uses the following AWS resources:
- **Amazon VPC**: Provides an isolated network environment.
- **Amazon ECS**: Deploys and runs Docker containers for the microservices.
- **Amazon ECR**: Stores Docker container images for the microservices.
- **AWS CDK**: Automates the provisioning of cloud resources with code.
- **Amazon NLB (Network Load Balancer)**: Distributes traffic to the microservices.
- **Amazon ALB (Application Load Balancer)**: Routes HTTP/HTTPS traffic based on application-layer rules.

## Architecture Overview

The microservices are deployed on a robust infrastructure consisting of:
- A custom VPC for secure networking.
- An ECS cluster with an auto-scaling capability.
- A network and application load balancer to manage incoming traffic.
- ECR repositories for storing Docker images.
- Tags for cost allocation and team identification.
