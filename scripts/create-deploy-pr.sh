#!/bin/bash

# ToyApi Deployment Pull Request Helper
# This script helps create pull requests for deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 ToyApi Deployment Pull Request Helper${NC}"
echo ""

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo -e "${RED}❌ Not in a git repository${NC}"
    exit 1
fi

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh) not found${NC}"
    echo "Please install it: https://cli.github.com/"
    exit 1
fi

# Get current branch
current_branch=$(git branch --show-current)
echo -e "${BLUE}Current branch: ${current_branch}${NC}"

# Check if there are uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${YELLOW}⚠️  You have uncommitted changes. Please commit them first.${NC}"
    git status --short
    exit 1
fi

# Push current branch
echo -e "${YELLOW}Pushing current branch to origin...${NC}"
git push origin "$current_branch"

# Create pull request
echo -e "${YELLOW}Creating pull request...${NC}"
gh pr create --title "Deploy: $current_branch" \
    --body "## Deployment Pull Request

**Branch**: \`$current_branch\`
**Target**: \`main\`

### Deployment Options
- **Staging**: Automatic deployment when PR is merged
- **Production**: Add \`deploy-prod\` label to this PR
- **Development**: Add \`deploy-dev\` label to this PR

### Pre-merge Checklist
- [ ] All tests pass
- [ ] Security scan passes
- [ ] Code review completed
- [ ] Ready for deployment

**Note**: Merging this PR will automatically deploy to staging. Add appropriate labels for other environments." \
    --base main \
    --head "$current_branch"

echo -e "${GREEN}✅ Pull request created successfully!${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. Review the pull request on GitHub"
echo "2. Add deployment labels if needed:"
echo "   - ${YELLOW}deploy-dev${NC} - Deploy to development"
echo "   - ${YELLOW}deploy-prod${NC} - Deploy to production"
echo "3. Merge the PR to deploy to staging"
echo ""
echo -e "${BLUE}View your pull request:${NC}"
gh pr view --web