package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.logs.LogGroup;
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
        setupErrorMonitoring();
        setupPerformanceMonitoring();
        setupCostMonitoring();
        
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
     * Sets up API Gateway monitoring and alerts
     */
    private void setupApiGatewayMonitoring() {
        String apiName = resourcePrefix + "-api";
        
        // API Gateway metrics
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
        
        // Add widgets to dashboard
        dashboard.addWidgets(
            GraphWidget.Builder.create()
                .title("API Gateway - Request Volume")
                .left(Arrays.asList(apiCallsMetric))
                .width(12)
                .height(6)
                .build(),
                
            GraphWidget.Builder.create()
                .title("API Gateway - Latency")
                .left(Arrays.asList(apiLatencyMetric))
                .width(12)
                .height(6)
                .build(),
                
            GraphWidget.Builder.create()
                .title("API Gateway - Errors")
                .left(Arrays.asList(apiErrorsMetric, apiServerErrorsMetric))
                .width(12)
                .height(6)
                .build()
        );
        
        // High latency alarm
        Alarm highLatencyAlarm = Alarm.Builder.create(this, "HighLatencyAlarm")
                .alarmName(resourcePrefix + "-high-latency")
                .alarmDescription("API Gateway latency is too high")
                .metric(apiLatencyMetric)
                .threshold(2000) // 2 seconds
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
                
        highLatencyAlarm.addAlarmAction(new SnsAction(alertTopic));
        
        // High error rate alarm
        Alarm highErrorRateAlarm = Alarm.Builder.create(this, "HighErrorRateAlarm")
                .alarmName(resourcePrefix + "-high-error-rate")
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
}