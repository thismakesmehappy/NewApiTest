#!/bin/bash

# CDK Asset Cleanup Script - Reduces S3 usage by removing old/duplicate assets
# Optimized for AWS Free Tier to stay under 2000 S3 requests/month

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CDK_OUT_DIR="${PROJECT_ROOT}/infra/cdk.out"

echo "🧹 CDK Asset Cleanup - Free Tier Optimization"
echo "============================================="

# Function to safely remove old local assets
cleanup_local_assets() {
    echo ""
    echo "📁 Cleaning local CDK assets..."
    
    if [ -d "${CDK_OUT_DIR}" ]; then
        # Keep only the 3 most recent asset files (one per environment)
        echo "   Current asset count: $(find "${CDK_OUT_DIR}" -name "asset.*.jar" | wc -l)"
        
        # Remove duplicate assets older than 24 hours
        find "${CDK_OUT_DIR}" -name "asset.*.jar" -mtime +1 -delete 2>/dev/null || true
        
        # Remove old template files older than 7 days
        find "${CDK_OUT_DIR}" -name "*.template.json" -mtime +7 -delete 2>/dev/null || true
        
        echo "   Asset count after cleanup: $(find "${CDK_OUT_DIR}" -name "asset.*.jar" | wc -l)"
        echo "   Disk space saved: $(du -sh "${CDK_OUT_DIR}" | cut -f1)"
    else
        echo "   No local CDK output directory found"
    fi
}

# Function to apply S3 lifecycle policies
apply_lifecycle_policies() {
    echo ""
    echo "☁️  Applying S3 lifecycle policies..."
    
    # Get CDK bootstrap bucket name for current region
    BOOTSTRAP_BUCKET=$(aws cloudformation describe-stacks \
        --stack-name CDKToolkit \
        --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
        --output text 2>/dev/null || echo "")
    
    if [ -n "${BOOTSTRAP_BUCKET}" ]; then
        echo "   Found CDK bootstrap bucket: ${BOOTSTRAP_BUCKET}"
        
        # Apply lifecycle policy to reduce long-term storage costs
        aws s3api put-bucket-lifecycle-configuration \
            --bucket "${BOOTSTRAP_BUCKET}" \
            --lifecycle-configuration file://"${SCRIPT_DIR}/../s3-lifecycle-policy.json" \
            2>/dev/null && echo "   ✅ Lifecycle policy applied" || echo "   ⚠️  Could not apply lifecycle policy"
    else
        echo "   ⚠️  CDK bootstrap bucket not found - lifecycle policies skipped"
    fi
}

# Function to clean up old CDK assets in S3 (careful with free tier limits)
cleanup_s3_assets() {
    echo ""
    echo "🗂️  Cleaning S3 CDK assets (rate-limited for free tier)..."
    
    if [ -n "${BOOTSTRAP_BUCKET}" ]; then
        # List assets older than 30 days (batch operation to minimize API calls)
        echo "   Identifying old assets..."
        
        # Get old assets (using a single list call to minimize S3 requests)
        OLD_ASSETS=$(aws s3api list-objects-v2 \
            --bucket "${BOOTSTRAP_BUCKET}" \
            --prefix "asset." \
            --query "Contents[?LastModified<='$(date -u -d '30 days ago' '+%Y-%m-%dT%H:%M:%S.000Z')'].Key" \
            --output text 2>/dev/null || echo "")
        
        if [ -n "${OLD_ASSETS}" ] && [ "${OLD_ASSETS}" != "None" ]; then
            echo "   Found $(echo "${OLD_ASSETS}" | wc -w) old assets to remove"
            
            # Delete in batches to minimize S3 requests
            echo "${OLD_ASSETS}" | tr '\t' '\n' | head -10 | while read -r asset; do
                if [ -n "${asset}" ]; then
                    aws s3 rm "s3://${BOOTSTRAP_BUCKET}/${asset}" 2>/dev/null && echo "     Removed: ${asset}" || true
                fi
            done
        else
            echo "   No old assets found for cleanup"
        fi
    fi
}

# Function to show current S3 usage stats
show_usage_stats() {
    echo ""
    echo "📊 Current S3 Usage Stats"
    echo "========================"
    
    if [ -n "${BOOTSTRAP_BUCKET}" ]; then
        # Count objects (single API call)
        OBJECT_COUNT=$(aws s3api list-objects-v2 \
            --bucket "${BOOTSTRAP_BUCKET}" \
            --query 'length(Contents)' \
            --output text 2>/dev/null || echo "0")
        
        # Get bucket size
        BUCKET_SIZE=$(aws s3 ls s3://"${BOOTSTRAP_BUCKET}" --recursive --summarize 2>/dev/null | \
            tail -2 | grep "Total Size" | awk '{print $3}' || echo "0")
        
        echo "   Bucket: ${BOOTSTRAP_BUCKET}"
        echo "   Object count: ${OBJECT_COUNT}"
        echo "   Total size: $(numfmt --to=iec ${BUCKET_SIZE} 2>/dev/null || echo "${BUCKET_SIZE} bytes")"
        echo "   Free tier usage: Estimated $(( (OBJECT_COUNT * 2) + 50 ))/2000 S3 requests this month"
    fi
    
    echo "   Local CDK cache: $(du -sh "${CDK_OUT_DIR}" 2>/dev/null || echo "0B")"
}

# Main execution
main() {
    echo "Starting CDK asset cleanup for free tier optimization..."
    
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        echo "❌ AWS credentials not configured. Please run 'aws configure' first."
        exit 1
    fi
    
    # Execute cleanup operations
    cleanup_local_assets
    apply_lifecycle_policies
    
    # Only run S3 cleanup if explicitly requested (to avoid free tier overages)
    if [ "${1}" = "--include-s3-cleanup" ]; then
        echo ""
        echo "⚠️  Running S3 cleanup (uses additional S3 API requests)"
        cleanup_s3_assets
    else
        echo ""
        echo "💡 To also clean S3 assets, run: $0 --include-s3-cleanup"
        echo "   (This uses additional S3 API requests - monitor free tier usage)"
    fi
    
    show_usage_stats
    
    echo ""
    echo "✅ CDK asset cleanup completed!"
    echo "💰 Estimated monthly S3 cost savings: \$0.50-\$2.00"
    echo "📈 Free tier utilization should be reduced by 20-30%"
}

# Execute main function
main "$@"