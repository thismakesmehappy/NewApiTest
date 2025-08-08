package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

/**
 * DatabaseStack - Manages DynamoDB tables and related database infrastructure
 */
public class DatabaseStack extends Stack {
    
    private final String environment;
    private final String resourcePrefix;
    private final ITable itemsTable;
    
    public DatabaseStack(final Construct scope, final String id, final StackProps props, final String environment) {
        super(scope, id, props);
        
        this.environment = environment;
        this.resourcePrefix = "toyapi-" + environment;
        
        // Create DynamoDB table for items storage
        this.itemsTable = createItemsTable();
    }
    
    /**
     * Creates DynamoDB table for storing items with proper configuration for the environment.
     */
    private ITable createItemsTable() {
        String tableName = resourcePrefix + "-items";
        
        // Create condition to determine if table should be created
        CfnCondition shouldCreateItemsTable = createResourceExistenceChecker(
            "ItemsTableExistenceChecker",
            "DYNAMODB_TABLE",
            tableName
        );
        
        Table.Builder tableBuilder = Table.Builder.create(this, "ItemsTable")
                .tableName(tableName)
                .partitionKey(Attribute.builder()
                        .name("PK")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("SK") 
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)  // Cost-effective for low traffic
                .pointInTimeRecovery(true);  // Enable backup

        // Set retention policy based on environment
        if (environment.equals("prod")) {
            tableBuilder.removalPolicy(RemovalPolicy.RETAIN);
        } else {
            tableBuilder.removalPolicy(RemovalPolicy.DESTROY);
        }

        Table table = tableBuilder.build();
        
        // Override logical ID to match existing CloudFormation state
        CfnTable cfnTable = (CfnTable) table.getNode().getDefaultChild();
        cfnTable.overrideLogicalId("ItemsTable5AAC2C46");
        
        // Apply the condition to only create if we should create (non-production)
        cfnTable.getCfnOptions().setCondition(shouldCreateItemsTable);

        // Add GSI for user-based queries
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("UserIndex")
                .partitionKey(Attribute.builder()
                        .name("userId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("createdAt")
                        .type(AttributeType.STRING)
                        .build())
                .build());

        // Return reference that works whether table was created or already existed
        return Table.fromTableName(this, "ItemsTableReference", tableName);
    }
    
    /**
     * Creates a CloudFormation condition that determines if a resource should be created.
     * For production, assumes retained resources exist. For non-production, creates them.
     * 
     * @param resourceId CDK construct ID for the condition
     * @param resourceType Type of resource (for documentation purposes)
     * @param resourceName Name of the resource (for documentation purposes)
     * @return CfnCondition that is false for production (don't create), true for non-production (create)
     */
    private CfnCondition createResourceExistenceChecker(String resourceId, String resourceType, String resourceName) {
        // Create a condition that is true when we should create the resource
        // Production: false (assume resource exists due to RetainPolicy)
        // Non-production: true (create the resource)
        return CfnCondition.Builder.create(this, resourceId + "ShouldCreate")
                .expression(Fn.conditionNot(Fn.conditionEquals(environment, "prod")))
                .build();
    }
    
    // Getters for other stacks
    public ITable getItemsTable() {
        return itemsTable;
    }
    
    public String getResourcePrefix() {
        return resourcePrefix;
    }
}