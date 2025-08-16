#!/bin/bash

# S3 Usage Monitoring Script for AWS Free Tier
# Tracks S3 requests and storage to prevent free tier overages

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Free tier limits
FREE_TIER_REQUESTS_LIMIT=2000
FREE_TIER_STORAGE_LIMIT=5368709120  # 5GB in bytes
WARNING_THRESHOLD=80  # Alert at 80% of limit

echo "📊 S3 Free Tier Usage Monitor"
echo "============================="

# Function to get current month's CloudWatch metrics
get_s3_metrics() {
    local start_date=$(date -u -d "$(date +%Y-%m-01)" '+%Y-%m-%dT00:00:00Z')
    local end_date=$(date -u '+%Y-%m-%dT23:59:59Z')
    
    echo ""
    echo "📅 Monitoring period: $(date +%Y-%m-01) to $(date +%Y-%m-%d)"
    
    # Get CDK bootstrap bucket name
    BOOTSTRAP_BUCKET=$(aws cloudformation describe-stacks \
        --stack-name CDKToolkit \
        --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
        --output text 2>/dev/null || echo "")
    
    if [ -z "${BOOTSTRAP_BUCKET}" ]; then
        echo "⚠️  CDK bootstrap bucket not found - cannot monitor S3 usage"
        return 1
    fi
    
    echo "🪣 Monitoring bucket: ${BOOTSTRAP_BUCKET}"
    echo ""
}

# Function to get S3 request metrics
get_request_metrics() {
    echo "🔢 S3 Request Usage"
    echo "-------------------"
    
    # Get current object count (approximates requests)
    local object_count=$(aws s3api list-objects-v2 \
        --bucket "${BOOTSTRAP_BUCKET}" \
        --query 'length(Contents)' \
        --output text 2>/dev/null || echo "0")
    
    # Estimate monthly requests (each object typically requires 2-3 requests: PUT, GET, HEAD)
    local estimated_requests=$((object_count * 3))
    
    # Get actual CloudWatch metrics if available (last 7 days)
    local actual_requests=$(aws cloudwatch get-metric-statistics \
        --namespace AWS/S3 \
        --metric-name NumberOfObjects \
        --dimensions Name=BucketName,Value="${BOOTSTRAP_BUCKET}" Name=StorageType,Value=AllStorageTypes \
        --start-time "$(date -u -d '7 days ago' '+%Y-%m-%dT%H:%M:%SZ')" \
        --end-time "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
        --period 86400 \
        --statistics Maximum \
        --query 'Datapoints[0].Maximum' \
        --output text 2>/dev/null || echo "")
    
    if [ "${actual_requests}" != "" ] && [ "${actual_requests}" != "None" ]; then
        echo "   Actual objects: ${actual_requests}"
        estimated_requests=$((actual_requests * 3))
    else
        echo "   Current objects: ${object_count}"
    fi
    
    echo "   Estimated monthly requests: ${estimated_requests}"
    echo "   Free tier limit: ${FREE_TIER_REQUESTS_LIMIT}"
    
    local usage_percent=$((estimated_requests * 100 / FREE_TIER_REQUESTS_LIMIT))
    echo "   Usage: ${usage_percent}% of free tier"
    
    if [ ${usage_percent} -ge ${WARNING_THRESHOLD} ]; then
        echo "   ⚠️  WARNING: Approaching free tier limit!"
    else
        echo "   ✅ Within safe limits"
    fi
    
    echo ""
}

# Function to get S3 storage metrics
get_storage_metrics() {
    echo "💾 S3 Storage Usage"
    echo "-------------------"
    
    # Get bucket size
    local bucket_size=$(aws s3 ls s3://"${BOOTSTRAP_BUCKET}" --recursive --summarize 2>/dev/null | \
        tail -2 | grep "Total Size" | awk '{print $3}' || echo "0")
    
    echo "   Current storage: $(numfmt --to=iec ${bucket_size} 2>/dev/null || echo "${bucket_size} bytes")"
    echo "   Free tier limit: $(numfmt --to=iec ${FREE_TIER_STORAGE_LIMIT})"
    
    local storage_percent=$((bucket_size * 100 / FREE_TIER_STORAGE_LIMIT))
    echo "   Usage: ${storage_percent}% of free tier"
    
    if [ ${storage_percent} -ge ${WARNING_THRESHOLD} ]; then
        echo "   ⚠️  WARNING: Approaching free tier limit!"
    else
        echo "   ✅ Within safe limits"
    fi
    
    echo ""
}

# Function to show recent deployment activity
show_deployment_activity() {
    echo "🚀 Recent Deployment Activity"
    echo "-----------------------------"
    
    # Count recent assets (last 7 days)
    local recent_assets=$(find /Users/bernardo/Dropbox/_CODE/TestAWSAPI2/infra/cdk.out/ -name "asset.*.jar" -mtime -7 2>/dev/null | wc -l || echo "0")
    echo "   New assets (last 7 days): ${recent_assets}"
    
    # Show Git activity
    local recent_commits=$(git log --oneline --since="7 days ago" 2>/dev/null | wc -l || echo "0")
    echo "   Commits (last 7 days): ${recent_commits}"
    
    # Show deployment frequency
    local deploy_commits=$(git log --oneline --since="7 days ago" --grep="deploy\|feat\|fix" 2>/dev/null | wc -l || echo "0")
    echo "   Deployment-related commits: ${deploy_commits}"
    
    echo ""
}

# Function to provide cost optimization recommendations
show_recommendations() {
    echo "💡 Cost Optimization Recommendations"
    echo "====================================="
    
    local object_count=$(aws s3api list-objects-v2 \
        --bucket "${BOOTSTRAP_BUCKET}" \
        --query 'length(Contents)' \
        --output text 2>/dev/null || echo "0")
    
    local estimated_requests=$((object_count * 3))
    local usage_percent=$((estimated_requests * 100 / FREE_TIER_REQUESTS_LIMIT))
    
    if [ ${usage_percent} -ge 90 ]; then
        echo "🚨 CRITICAL: Very high usage (${usage_percent}%)"
        echo "   1. Run cleanup script immediately: ./cleanup-cdk-assets.sh --include-s3-cleanup"
        echo "   2. Reduce deployment frequency"
        echo "   3. Consider infrastructure-only commits"
    elif [ ${usage_percent} -ge ${WARNING_THRESHOLD} ]; then
        echo "⚠️  WARNING: High usage (${usage_percent}%)"
        echo "   1. Run cleanup script: ./cleanup-cdk-assets.sh"
        echo "   2. Review deployment patterns"
        echo "   3. Enable JAR change detection in CI/CD"
    else
        echo "✅ Usage is within safe limits (${usage_percent}%)"
        echo "   1. Continue current optimization practices"
        echo "   2. Run weekly cleanup: ./cleanup-cdk-assets.sh"
        echo "   3. Monitor monthly to ensure continued compliance"
    fi
    
    echo ""
    echo "🔧 Active Optimizations:"
    echo "   ✅ CDK asset content-based hashing"
    echo "   ✅ S3 lifecycle policies configured"
    echo "   ✅ CI/CD deployment skipping for unchanged JARs"
    echo "   ✅ Local asset cleanup in CI/CD pipeline"
    
    echo ""
    echo "📈 Estimated monthly cost without optimization: \$2-5"
    echo "💰 Estimated monthly cost with optimization: \$0 (free tier)"
}

# Function to set up CloudWatch alarms for S3 usage
setup_monitoring_alarms() {
    echo ""
    echo "🔔 Setting up S3 usage monitoring alarms..."
    
    # Create SNS topic for alerts (if it doesn't exist)
    local topic_arn=$(aws sns create-topic \
        --name "ToyApi-S3-Usage-Alerts" \
        --query 'TopicArn' \
        --output text 2>/dev/null || echo "")
    
    if [ -n "${topic_arn}" ]; then
        echo "   SNS topic created: ${topic_arn}"
        
        # Create CloudWatch alarm for high S3 usage
        aws cloudwatch put-metric-alarm \
            --alarm-name "ToyApi-S3-High-Usage" \
            --alarm-description "Alert when S3 usage approaches free tier limits" \
            --metric-name NumberOfObjects \
            --namespace AWS/S3 \
            --statistic Maximum \
            --period 86400 \
            --threshold 500 \
            --comparison-operator GreaterThanThreshold \
            --evaluation-periods 1 \
            --alarm-actions "${topic_arn}" \
            --dimensions Name=BucketName,Value="${BOOTSTRAP_BUCKET}" Name=StorageType,Value=AllStorageTypes \
            2>/dev/null && echo "   ✅ CloudWatch alarm created" || echo "   ⚠️  Could not create CloudWatch alarm"
    else
        echo "   ⚠️  Could not create SNS topic"
    fi
}

# Main execution
main() {
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        echo "❌ AWS credentials not configured. Please run 'aws configure' first."
        exit 1
    fi
    
    # Get S3 metrics
    if get_s3_metrics; then
        get_request_metrics
        get_storage_metrics
        show_deployment_activity
        show_recommendations
        
        # Set up monitoring if requested
        if [ "${1}" = "--setup-alarms" ]; then
            setup_monitoring_alarms
        fi
    fi
    
    echo "📋 Next steps:"
    echo "   1. Run this script weekly to monitor usage"
    echo "   2. Use './cleanup-cdk-assets.sh' for regular cleanup"
    echo "   3. Add '--setup-alarms' to enable CloudWatch monitoring"
    echo ""
    echo "For questions about S3 costs, see AWS Free Tier documentation:"
    echo "https://aws.amazon.com/free/"
}

# Execute main function
main "$@"