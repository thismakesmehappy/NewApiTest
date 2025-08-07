package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ToyApi Monitoring Stack - Comprehensive observability and alerting
 * 
 * Features:
 * - CloudWatch Dashboard with custom metrics
 * - API performance and error monitoring
 * - Lambda function health monitoring
 * - Cost and usage tracking
 * - Multi-threshold alerting system
 * - Log aggregation and analysis
 */
public class MonitoringStack extends Stack {
    
    private final String environment;
    private final String resourcePrefix;
    private final Topic alertTopic;
    private final Dashboard dashboard;
    
    public MonitoringStack(final Construct scope, final String id, final StackProps props, 
                          final String environment) {
        super(scope, id, props);
        
        this.environment = environment;
        this.resourcePrefix = "toyapi-" + environment;
        
        // Create SNS topic for alerts
        this.alertTopic = createAlertTopic();
        
        // Create main dashboard
        this.dashboard = createMainDashboard();
        
        // Set up monitoring components
        setupApiGatewayMonitoring();
        setupLambdaMonitoring();
        setupDynamoDBMonitoring();  // New: Free DynamoDB metrics
        setupCognitoMonitoring();   // New: Free Cognito metrics
        setupErrorMonitoring();
        setupPerformanceMonitoring();
        setupBusinessMetrics();     // New: Log-based business insights
        setupSecurityMonitoring();  // New: Free security pattern detection
        setupCostMonitoring();
        setupLogAggregationAndAnalysis();
        
        // Create outputs
        createOutputs();
    }
    
    /**
     * Creates SNS topic for all monitoring alerts
     */
    private Topic createAlertTopic() {
        Topic topic = Topic.Builder.create(this, "AlertTopic")
                .topicName(resourcePrefix + "-alerts")
                .displayName("ToyApi " + environment.toUpperCase() + " Alerts")
                .build();
                
        // Add email subscription (replace with actual email)
        topic.addSubscription(EmailSubscription.Builder.create("admin@thismakesmehappy.co")
                .build());
                
        return topic;
    }
    
    /**
     * Creates main CloudWatch dashboard
     */
    private Dashboard createMainDashboard() {
        return Dashboard.Builder.create(this, "MainDashboard")
                .dashboardName(resourcePrefix + "-monitoring")
                .build();
    }
    
    /**
     * Sets up enhanced API Gateway monitoring and alerts
     * Includes per-endpoint metrics, authentication tracking, and performance analysis
     */
    private void setupApiGatewayMonitoring() {
        String apiName = resourcePrefix + "-api";
        
        // Overall API Gateway metrics
        Metric apiCallsMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("Count")
                .dimensionsMap(Map.of("ApiName", apiName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric apiLatencyMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("Latency")
                .dimensionsMap(Map.of("ApiName", apiName))
                .statistic("Average")
                .period(Duration.minutes(5))
                .build();
                
        Metric apiP99LatencyMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("Latency")
                .dimensionsMap(Map.of("ApiName", apiName))
                .statistic("p99")
                .period(Duration.minutes(5))
                .build();
                
        Metric apiErrorsMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("4XXError")
                .dimensionsMap(Map.of("ApiName", apiName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric apiServerErrorsMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("5XXError")
                .dimensionsMap(Map.of("ApiName", apiName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        // Per-endpoint metrics for detailed analysis
        Map<String, Metric> endpointMetrics = createEndpointSpecificMetrics(apiName);
        
        // Authentication-related metrics
        Metric authSuccessMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("Count")
                .dimensionsMap(Map.of("ApiName", apiName, "Method", "POST", "Resource", "/auth/login"))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric authFailureMetric = Metric.Builder.create()
                .namespace("AWS/ApiGateway")
                .metricName("4XXError")
                .dimensionsMap(Map.of("ApiName", apiName, "Method", "POST", "Resource", "/auth/login"))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        // Enhanced dashboard widgets with better visualizations
        dashboard.addWidgets(
            // Top row - Overview metrics
            SingleValueWidget.Builder.create()
                .title("Total API Requests (5min)")
                .metrics(Arrays.asList(apiCallsMetric))
                .width(6)
                .height(3)
                .build(),
                
            SingleValueWidget.Builder.create()
                .title("Average Latency (ms)")
                .metrics(Arrays.asList(apiLatencyMetric))
                .width(6)
                .height(3)
                .build(),
                
            SingleValueWidget.Builder.create()
                .title("Error Rate (%)")
                .metrics(Arrays.asList(MathExpression.Builder.create()
                    .expression("(m1+m2)/m3*100")
                    .usingMetrics(Map.of(
                        "m1", apiErrorsMetric,
                        "m2", apiServerErrorsMetric,
                        "m3", apiCallsMetric
                    ))
                    .label("Error Rate %")
                    .build()))
                .width(6)
                .height(3)
                .build(),
                
            SingleValueWidget.Builder.create()
                .title("Auth Success Rate (%)")
                .metrics(Arrays.asList(MathExpression.Builder.create()
                    .expression("m1/(m1+m2)*100")
                    .usingMetrics(Map.of(
                        "m1", authSuccessMetric,
                        "m2", authFailureMetric
                    ))
                    .label("Auth Success %")
                    .build()))
                .width(6)
                .height(3)
                .build(),
                
            // Second row - Detailed performance
            GraphWidget.Builder.create()
                .title("API Gateway - Request Volume Over Time")
                .left(Arrays.asList(apiCallsMetric))
                .width(12)
                .height(6)
                .build(),
                
            GraphWidget.Builder.create()
                .title("API Gateway - Performance Distribution")
                .left(Arrays.asList(apiLatencyMetric))
                .right(Arrays.asList(apiP99LatencyMetric))
                .width(12)
                .height(6)
                .build(),
                
            // Third row - Error analysis and endpoint breakdown
            GraphWidget.Builder.create()
                .title("API Gateway - Error Analysis")
                .left(Arrays.asList(apiErrorsMetric, apiServerErrorsMetric))
                .width(12)
                .height(6)
                .build(),
                
            GraphWidget.Builder.create()
                .title("Per-Endpoint Request Volume")
                .left(endpointMetrics.values().stream().limit(6).collect(java.util.stream.Collectors.toList()))
                .width(12)
                .height(6)
                .build()
        );
        
        // High latency alarm
        Alarm highLatencyAlarm = Alarm.Builder.create(this, "ApiHighLatencyAlarm")
                .alarmName(resourcePrefix + "-api-high-latency")
                .alarmDescription("API Gateway latency is too high")
                .metric(apiLatencyMetric)
                .threshold(2000) // 2 seconds
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        highLatencyAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // High error rate alarm
        Alarm highErrorRateAlarm = Alarm.Builder.create(this, "ApiHighErrorRateAlarm")
                .alarmName(resourcePrefix + "-api-high-error-rate")
                .alarmDescription("API Gateway error rate is too high")
                .metric(apiErrorsMetric)
                .threshold(10) // 10 errors in 5 minutes
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        highErrorRateAlarm.addAlarmAction(new SnsAction(alertTopic));
    }
    
    /**
     * Sets up Lambda function monitoring
     */
    private void setupLambdaMonitoring() {
        List<String> functionNames = Arrays.asList(
            resourcePrefix + "-publicfunction",
            resourcePrefix + "-authfunction", 
            resourcePrefix + "-itemsfunction"
        );
        
        for (String functionName : functionNames) {
            // Lambda metrics
            Metric invocationsMetric = Metric.Builder.create()
                    .namespace("AWS/Lambda")
                    .metricName("Invocations")
                    .dimensionsMap(Map.of("FunctionName", functionName))
                    .statistic("Sum")
                    .period(Duration.minutes(5))
                    .build();
                    
            Metric errorsMetric = Metric.Builder.create()
                    .namespace("AWS/Lambda")
                    .metricName("Errors")
                    .dimensionsMap(Map.of("FunctionName", functionName))
                    .statistic("Sum")
                    .period(Duration.minutes(5))
                    .build();
                    
            Metric durationMetric = Metric.Builder.create()
                    .namespace("AWS/Lambda")
                    .metricName("Duration")
                    .dimensionsMap(Map.of("FunctionName", functionName))
                    .statistic("Average")
                    .period(Duration.minutes(5))
                    .build();
                    
            Metric throttlesMetric = Metric.Builder.create()
                    .namespace("AWS/Lambda")
                    .metricName("Throttles")
                    .dimensionsMap(Map.of("FunctionName", functionName))
                    .statistic("Sum")
                    .period(Duration.minutes(5))
                    .build();
            
            // Lambda error alarm
            Alarm lambdaErrorAlarm = Alarm.Builder.create(this, functionName + "-ErrorAlarm")
                    .alarmName(functionName + "-errors")
                    .alarmDescription("Lambda function " + functionName + " has errors")
                    .metric(errorsMetric)
                    .threshold(5) // 5 errors in 5 minutes
                    .evaluationPeriods(1)
                    .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                    .build();
                    
            lambdaErrorAlarm.addAlarmAction(new SnsAction(alertTopic));
            
            // Lambda throttle alarm
            Alarm lambdaThrottleAlarm = Alarm.Builder.create(this, functionName + "-ThrottleAlarm")
                    .alarmName(functionName + "-throttles")
                    .alarmDescription("Lambda function " + functionName + " is being throttled")
                    .metric(throttlesMetric)
                    .threshold(1) // Any throttling
                    .evaluationPeriods(1)
                    .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                    .build();
                    
            lambdaThrottleAlarm.addAlarmAction(new SnsAction(alertTopic));
        }
        
        // Add Lambda overview widget
        dashboard.addWidgets(
            SingleValueWidget.Builder.create()
                .title("Lambda Functions Status")
                .metrics(functionNames.stream()
                    .map(name -> Metric.Builder.create()
                        .namespace("AWS/Lambda")
                        .metricName("Invocations")
                        .dimensionsMap(Map.of("FunctionName", name))
                        .statistic("Sum")
                        .period(Duration.minutes(5))
                        .build())
                    .collect(java.util.stream.Collectors.toList()))
                .width(24)
                .height(6)
                .build()
        );
    }
    
    /**
     * Sets up comprehensive error monitoring
     */
    private void setupErrorMonitoring() {
        // Create custom metric for application errors
        Metric appErrorsMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("ApplicationErrors")
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        // Application error alarm
        Alarm appErrorAlarm = Alarm.Builder.create(this, "ApplicationErrorAlarm")
                .alarmName(resourcePrefix + "-application-errors")
                .alarmDescription("Application is experiencing high error rates")
                .metric(appErrorsMetric)
                .threshold(10)
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build();
                
        appErrorAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // Add error tracking widget
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("Application Error Tracking")
                .left(Arrays.asList(appErrorsMetric))
                .width(12)
                .height(6)
                .build()
        );
    }
    
    /**
     * Sets up performance monitoring and SLA tracking
     */
    private void setupPerformanceMonitoring() {
        // Create SLA metrics
        Metric slaMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("SLA_Compliance")
                .statistic("Average")
                .period(Duration.minutes(5))
                .build();
        
        // SLA breach alarm (below 95% uptime)
        Alarm slaAlarm = Alarm.Builder.create(this, "SLABreachAlarm")
                .alarmName(resourcePrefix + "-sla-breach")
                .alarmDescription("SLA compliance is below threshold")
                .metric(slaMetric)
                .threshold(0.95) // 95%
                .evaluationPeriods(3)
                .comparisonOperator(ComparisonOperator.LESS_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.BREACHING)
                .build();
                
        slaAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // Add SLA tracking widget
        dashboard.addWidgets(
            SingleValueWidget.Builder.create()
                .title("SLA Compliance")
                .metrics(Arrays.asList(slaMetric))
                .width(6)
                .height(6)
                .build()
        );
    }
    
    /**
     * Sets up cost monitoring and budget alerts
     */
    private void setupCostMonitoring() {
        // Estimated charges metric
        Metric estimatedChargesMetric = Metric.Builder.create()
                .namespace("AWS/Billing")
                .metricName("EstimatedCharges")
                .dimensionsMap(Map.of("Currency", "USD"))
                .statistic("Maximum")
                .period(Duration.hours(6))
                .build();
        
        // Cost alarm at $8 (80% of $10 budget)
        Alarm costAlarm = Alarm.Builder.create(this, "CostAlarm")
                .alarmName(resourcePrefix + "-cost-alert")
                .alarmDescription("Monthly costs approaching budget limit")
                .metric(estimatedChargesMetric)
                .threshold(8.0) // $8
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        costAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // Add cost tracking widget
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("Estimated Monthly Charges")
                .left(Arrays.asList(estimatedChargesMetric))
                .width(12)
                .height(6)
                .build()
        );
    }
    
    /**
     * Sets up comprehensive log aggregation and analysis
     */
    private void setupLogAggregationAndAnalysis() {
        // Create central log group for aggregated API logs
        LogGroup centralLogGroup = LogGroup.Builder.create(this, "CentralLogGroup")
                .logGroupName("/aws/apigateway/" + resourcePrefix + "-aggregated")
                .retention(RetentionDays.ONE_MONTH)  // 30 days retention
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                .build();
        
        // Set up log metric filters for different log patterns
        setupLogMetricFilters(centralLogGroup);
        
        // Create log insights queries
        setupLogInsightQueries();
        
        // Set up log-based alarms
        setupLogBasedAlarms();
        
        // Add log analysis widgets to dashboard
        addLogAnalyticsToDashboard();
    }
    
    /**
     * Creates metric filters for common log patterns
     */
    private void setupLogMetricFilters(LogGroup logGroup) {
        // Error rate metric filter (for 4xx and 5xx responses)
        MetricFilter errorFilter = MetricFilter.Builder.create(this, "ErrorMetricFilter")
                .logGroup(logGroup)
                .filterPattern(FilterPattern.anyTerm("ERROR", "4[0-9][0-9]", "5[0-9][0-9]", "Exception", "Failed"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("ErrorCount")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // API key usage metric filter
        MetricFilter apiKeyUsageFilter = MetricFilter.Builder.create(this, "ApiKeyUsageFilter")
                .logGroup(logGroup)
                .filterPattern(FilterPattern.exists("$.apiKeyId"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("ApiKeyRequests")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Slow request metric filter - using simple text matching
        MetricFilter slowRequestFilter = MetricFilter.Builder.create(this, "SlowRequestFilter")  
                .logGroup(logGroup)
                .filterPattern(FilterPattern.anyTerm("SLOW", "timeout", "performance"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("SlowRequests")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Developer registration metric filter - using simple text matching
        MetricFilter devRegistrationFilter = MetricFilter.Builder.create(this, "DeveloperRegistrationFilter")
                .logGroup(logGroup)
                .filterPattern(FilterPattern.anyTerm("registered", "signup", "new_user"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("DeveloperRegistrations")
                .metricValue("1")
                .defaultValue(0)
                .build();
    }
    
    /**
     * Sets up CloudWatch Logs Insights queries for analysis
     */
    private void setupLogInsightQueries() {
        // Note: CloudWatch Logs Insights queries are created manually in console
        // or via AWS CLI. CDK doesn't have direct support for saved queries yet.
        // Common queries would include:
        
        // 1. Top error messages by frequency
        // 2. API endpoint usage patterns  
        // 3. Developer API key usage analysis
        // 4. Performance bottlenecks identification
        // 5. Security anomaly detection
    }
    
    /**
     * Creates alarms based on log metrics
     */
    private void setupLogBasedAlarms() {
        // High error rate alarm
        Metric errorRateMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment)
                .metricName("ErrorCount")
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Alarm errorRateAlarm = Alarm.Builder.create(this, "LogErrorRateAlarm")
                .alarmName(resourcePrefix + "-log-error-rate")
                .alarmDescription("High error rate detected in API logs")
                .metric(errorRateMetric)
                .threshold(10) // More than 10 errors in 5 minutes
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build();
                
        errorRateAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // Too many slow requests alarm
        Metric slowRequestMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment)
                .metricName("SlowRequests")
                .statistic("Sum")
                .period(Duration.minutes(10))
                .build();
                
        Alarm slowRequestAlarm = Alarm.Builder.create(this, "SlowRequestAlarm")
                .alarmName(resourcePrefix + "-slow-requests")
                .alarmDescription("High number of slow requests detected")
                .metric(slowRequestMetric)
                .threshold(5) // More than 5 slow requests in 10 minutes
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build();
                
        slowRequestAlarm.addAlarmAction(new SnsAction(alertTopic));
    }
    
    /**
     * Adds log analytics widgets to the main dashboard
     */
    private void addLogAnalyticsToDashboard() {
        // Error rate over time
        Metric errorRateMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment)
                .metricName("ErrorCount")
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        // API key usage over time
        Metric apiKeyUsageMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment)
                .metricName("ApiKeyRequests")
                .statistic("Sum")
                .period(Duration.hours(1))
                .build();
        
        // Developer registrations over time
        Metric devRegistrationMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment)
                .metricName("DeveloperRegistrations")
                .statistic("Sum")
                .period(Duration.hours(6))
                .build();
        
        // Add widgets to dashboard
        dashboard.addWidgets(
            // Error rate graph
            GraphWidget.Builder.create()
                .title("Error Rate (5min intervals)")
                .left(Arrays.asList(errorRateMetric))
                .width(12)
                .height(6)
                .build(),
                
            // API key usage graph
            GraphWidget.Builder.create()
                .title("API Key Usage (hourly)")
                .left(Arrays.asList(apiKeyUsageMetric))
                .width(12)
                .height(6)
                .build(),
                
            // Developer registrations
            GraphWidget.Builder.create()
                .title("Developer Registrations (6hr intervals)")
                .left(Arrays.asList(devRegistrationMetric))
                .width(12)
                .height(6)
                .build()
        );
    }
    
    /**
     * Creates CloudFormation outputs
     */
    private void createOutputs() {
        software.amazon.awscdk.CfnOutput.Builder.create(this, "DashboardUrl")
                .value("https://console.aws.amazon.com/cloudwatch/home?region=" + 
                       this.getRegion() + "#dashboards:name=" + dashboard.getDashboardName())
                .description("CloudWatch Dashboard URL")
                .build();
                
        software.amazon.awscdk.CfnOutput.Builder.create(this, "AlertTopicArn")
                .value(alertTopic.getTopicArn())
                .description("SNS Topic ARN for monitoring alerts")
                .build();
    }
    
    /**
     * Creates endpoint-specific metrics for detailed API analysis
     * FREE: Uses existing CloudWatch metrics with different dimensions
     */
    private Map<String, Metric> createEndpointSpecificMetrics(String apiName) {
        Map<String, Metric> endpointMetrics = new HashMap<>();
        
        // Define key endpoints to monitor
        String[] endpoints = {"/public/message", "/auth/login", "/auth/message", "/items", "/items/{id}"};
        String[] methods = {"GET", "POST", "PUT", "DELETE"};
        
        for (String endpoint : endpoints) {
            for (String method : methods) {
                String key = method + " " + endpoint;
                endpointMetrics.put(key, Metric.Builder.create()
                    .namespace("AWS/ApiGateway")
                    .metricName("Count")
                    .dimensionsMap(Map.of(
                        "ApiName", apiName,
                        "Method", method,
                        "Resource", endpoint
                    ))
                    .statistic("Sum")
                    .period(Duration.minutes(5))
                    .build());
            }
        }
        
        return endpointMetrics;
    }
    
    /**
     * Sets up DynamoDB monitoring using free CloudWatch metrics
     * FREE: Uses built-in DynamoDB metrics at no additional cost
     */
    private void setupDynamoDBMonitoring() {
        String tableName = resourcePrefix + "-items";
        
        // DynamoDB read/write metrics
        Metric readCapacityMetric = Metric.Builder.create()
                .namespace("AWS/DynamoDB")
                .metricName("ConsumedReadCapacityUnits")
                .dimensionsMap(Map.of("TableName", tableName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric writeCapacityMetric = Metric.Builder.create()
                .namespace("AWS/DynamoDB")
                .metricName("ConsumedWriteCapacityUnits")
                .dimensionsMap(Map.of("TableName", tableName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric itemCountMetric = Metric.Builder.create()
                .namespace("AWS/DynamoDB")
                .metricName("ItemCount")
                .dimensionsMap(Map.of("TableName", tableName))
                .statistic("Average")
                .period(Duration.hours(1))
                .build();
                
        Metric tableSizeMetric = Metric.Builder.create()
                .namespace("AWS/DynamoDB")
                .metricName("TableSizeBytes")
                .dimensionsMap(Map.of("TableName", tableName))
                .statistic("Average")
                .period(Duration.hours(1))
                .build();
        
        // Add DynamoDB dashboard widgets
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("DynamoDB - Read/Write Capacity Usage")
                .left(Arrays.asList(readCapacityMetric))
                .right(Arrays.asList(writeCapacityMetric))
                .width(12)
                .height(6)
                .build(),
                
            GraphWidget.Builder.create()
                .title("DynamoDB - Table Growth")
                .left(Arrays.asList(itemCountMetric))
                .right(Arrays.asList(tableSizeMetric))
                .width(12)
                .height(6)
                .build()
        );
        
        // DynamoDB throttle alarm (free metric)
        Metric throttleMetric = Metric.Builder.create()
                .namespace("AWS/DynamoDB")
                .metricName("UserErrors")
                .dimensionsMap(Map.of("TableName", tableName))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Alarm dynamoThrottleAlarm = Alarm.Builder.create(this, "DynamoDBThrottleAlarm")
                .alarmName(resourcePrefix + "-dynamodb-throttles")
                .alarmDescription("DynamoDB table is experiencing throttling")
                .metric(throttleMetric)
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .build();
                
        dynamoThrottleAlarm.addAlarmAction(new SnsAction(alertTopic));
    }
    
    /**
     * Sets up Cognito monitoring using free CloudWatch metrics
     * FREE: Uses built-in Cognito metrics at no additional cost
     */
    private void setupCognitoMonitoring() {
        String userPoolName = resourcePrefix + "-users";
        
        // Cognito authentication metrics
        Metric authSuccessMetric = Metric.Builder.create()
                .namespace("AWS/Cognito")
                .metricName("SignInSuccesses")
                .dimensionsMap(Map.of("UserPool", userPoolName, "UserPoolClient", "ALL"))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
                
        Metric authFailureMetric = Metric.Builder.create()
                .namespace("AWS/Cognito")
                .metricName("SignInThrottles")
                .dimensionsMap(Map.of("UserPool", userPoolName, "UserPoolClient", "ALL"))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        // Add Cognito dashboard widgets
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("Cognito - Authentication Activity")
                .left(Arrays.asList(authSuccessMetric))
                .right(Arrays.asList(authFailureMetric))
                .width(12)
                .height(6)
                .build()
        );
        
        // High authentication failure alarm
        Alarm authFailureAlarm = Alarm.Builder.create(this, "CognitoAuthFailureAlarm")
                .alarmName(resourcePrefix + "-cognito-auth-failures")
                .alarmDescription("High number of authentication failures in Cognito")
                .metric(authFailureMetric)
                .threshold(10) // 10 failures in 5 minutes
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        authFailureAlarm.addAlarmAction(new SnsAction(alertTopic));
    }
    
    /**
     * Sets up business metrics using log analysis
     * FREE: Uses log metric filters to extract business insights at no additional cost
     */
    private void setupBusinessMetrics() {
        // Create log group if it doesn't exist (for business metrics extraction)
        LogGroup businessLogGroup = LogGroup.Builder.create(this, "BusinessMetricsLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-business-metrics")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                .build();
        
        // User registration metric filter
        MetricFilter userRegistrationFilter = MetricFilter.Builder.create(this, "UserRegistrationFilter")
                .logGroup(businessLogGroup)
                .filterPattern(FilterPattern.literal("[timestamp, requestId, level=\"INFO\", message=\"User registered\", ...]"))
                .metricNamespace("ToyApi/Business/" + environment)
                .metricName("UserRegistrations")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Item creation metric filter
        MetricFilter itemCreationFilter = MetricFilter.Builder.create(this, "ItemCreationFilter")
                .logGroup(businessLogGroup)
                .filterPattern(FilterPattern.literal("[timestamp, requestId, level=\"INFO\", message=\"Item created\", ...]"))
                .metricNamespace("ToyApi/Business/" + environment)
                .metricName("ItemsCreated")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // API usage by endpoint metric filter
        MetricFilter endpointUsageFilter = MetricFilter.Builder.create(this, "EndpointUsageFilter")
                .logGroup(businessLogGroup)
                .filterPattern(FilterPattern.literal("[timestamp, requestId, level=\"INFO\", message=\"API request\", endpoint, ...]"))
                .metricNamespace("ToyApi/Business/" + environment)
                .metricName("EndpointUsage")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Business metrics dashboard
        Metric userRegMetric = Metric.Builder.create()
                .namespace("ToyApi/Business/" + environment)
                .metricName("UserRegistrations")
                .statistic("Sum")
                .period(Duration.hours(1))
                .build();
                
        Metric itemCreationMetric = Metric.Builder.create()
                .namespace("ToyApi/Business/" + environment)
                .metricName("ItemsCreated")
                .statistic("Sum")
                .period(Duration.hours(1))
                .build();
        
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("Business Metrics - User Activity")
                .left(Arrays.asList(userRegMetric))
                .right(Arrays.asList(itemCreationMetric))
                .width(12)
                .height(6)
                .build()
        );
    }
    
    /**
     * Sets up security monitoring using log pattern analysis
     * FREE: Uses log metric filters to detect security patterns at no additional cost
     */
    private void setupSecurityMonitoring() {
        // Create security log group
        LogGroup securityLogGroup = LogGroup.Builder.create(this, "SecurityLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-security")
                .retention(RetentionDays.TWO_WEEKS) // Keep security logs longer
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                .build();
        
        // Failed authentication attempts
        MetricFilter failedAuthFilter = MetricFilter.Builder.create(this, "FailedAuthFilter")
                .logGroup(securityLogGroup)
                .filterPattern(FilterPattern.anyTerm("UNAUTHORIZED", "Invalid credentials", "Authentication failed", "401"))
                .metricNamespace("ToyApi/Security/" + environment)
                .metricName("FailedAuthentications")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Suspicious API usage patterns (high frequency from single source)
        MetricFilter suspiciousUsageFilter = MetricFilter.Builder.create(this, "SuspiciousUsageFilter")
                .logGroup(securityLogGroup)
                .filterPattern(FilterPattern.literal("[timestamp, requestId, level=\"WARN\", message=\"High frequency requests\", ...]"))
                .metricNamespace("ToyApi/Security/" + environment)
                .metricName("SuspiciousActivity")
                .metricValue("1")
                .defaultValue(0)
                .build();
        
        // Security alarms
        Metric failedAuthMetric = Metric.Builder.create()
                .namespace("ToyApi/Security/" + environment)
                .metricName("FailedAuthentications")
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        Alarm securityAlarm = Alarm.Builder.create(this, "SecurityAlarm")
                .alarmName(resourcePrefix + "-security-breach-attempt")
                .alarmDescription("Potential security breach detected")
                .metric(failedAuthMetric)
                .threshold(20) // 20 failed auths in 5 minutes
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        securityAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // Security dashboard widget
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("Security Monitoring - Authentication Failures")
                .left(Arrays.asList(failedAuthMetric))
                .width(12)
                .height(6)
                .build()
        );
    }
}