# ToyApi CI/CD Pipeline

This directory contains the GitHub Actions workflows for the ToyApi project, providing automated testing, security scanning, and deployment capabilities.

## 🚀 Workflows Overview

### 1. CI/CD Pipeline (`ci-cd.yml`)
**Triggers**: Push to `main` or `develop`, Pull Requests to `main`

**Jobs**:
- **test**: Builds code, runs unit tests, uploads artifacts
- **security-scan**: Runs OWASP dependency check for vulnerabilities
- **deploy-dev**: Deploys to development environment (on `develop` branch)
- **deploy-staging**: Deploys to staging environment (on `main` branch)
- **deploy-production**: Deploys to production environment (after staging success)
- **notify**: Sends deployment status notifications

### 2. Pull Request Validation (`pull-request.yml`)
**Triggers**: Pull Requests to `main` or `develop`

**Features**:
- Code formatting validation
- Unit test execution
- CDK synthesis validation
- Secret detection
- OpenAPI spec validation
- Automated PR comments with validation status
- Security analysis for main branch PRs

### 3. Release Management (`release.yml`)
**Triggers**: Version tags (`v*`), Manual workflow dispatch

**Capabilities**:
- Automated release creation with changelog
- Manual environment deployment
- Emergency rollback procedures
- Release artifact management

### 4. Dependency Updates (`dependabot.yml`)
**Features**:
- Weekly dependency updates for Maven packages
- GitHub Actions updates
- Grouped updates by component (AWS SDK, Jackson, testing)
- Automatic reviewer assignment

## 🔧 Setup Requirements

### GitHub Secrets
Configure these secrets in your GitHub repository settings:

```
AWS_ACCESS_KEY_ID=<your-aws-access-key>
AWS_SECRET_ACCESS_KEY=<your-aws-secret-key>
```

### GitHub Environments
Create these environments in GitHub with appropriate protection rules:

1. **development**
   - No special restrictions
   - Used for feature branch testing

2. **staging**  
   - Require reviewers: 1 person
   - Restrict deployments to `main` branch

3. **production**
   - Require reviewers: 2 people
   - Restrict deployments to `main` branch
   - Add deployment delay: 5 minutes

## 🔒 Security Features

### OWASP Dependency Check
- Scans all Maven dependencies for known vulnerabilities
- Fails build on CVSS scores ≥ 7.0
- Customizable suppressions via `owasp-suppressions.xml`
- Weekly security reports

### Secret Detection
- Basic secret scanning in PR validation
- Checks for AWS credentials, passwords, API keys
- Prevents accidental secret commits

### Branch Protection
Recommended branch protection rules:

**main branch**:
- Require PR reviews (2 reviewers)
- Require status checks: `test`, `security-scan`
- Require up-to-date branches
- Restrict pushes to admins only

**develop branch**:
- Require PR reviews (1 reviewer)  
- Require status checks: `test`
- Allow force pushes from admins

## 📊 Deployment Strategy

```
Feature Branch → develop → staging → production
                    ↓        ↓         ↓
                  dev env  stage env  prod env
```

### Automatic Deployments
- `develop` branch → Development environment
- `main` branch → Staging environment → Production environment

### Manual Deployments
- Use workflow dispatch for ad-hoc deployments
- Support for version-specific deployments
- Emergency rollback capabilities

## 🚨 Emergency Procedures

### Rollback Production
1. Go to Actions tab in GitHub
2. Select "Release Management" workflow
3. Click "Run workflow"
4. Set environment to "production"
5. Set version to "rollback-<reason>"
6. Confirm deployment

### Hotfix Deployment
1. Create hotfix branch from main
2. Make critical fixes
3. Create PR to main
4. After merge, production deployment will trigger automatically

## 📈 Monitoring & Alerts

### Build Status
- All workflow results are visible in GitHub Actions tab
- Failed builds automatically fail PR status checks
- Deployment logs uploaded as artifacts

### Security Alerts
- Dependabot creates PRs for security updates
- OWASP reports uploaded as artifacts
- High-severity vulnerabilities fail builds

## 🔄 Maintenance

### Weekly Tasks
- Review Dependabot PRs
- Check security scan reports
- Update workflow dependencies

### Monthly Tasks  
- Review deployment metrics
- Update environment configurations
- Audit access controls

## 📝 Customization

### Adding New Environments
1. Create environment in GitHub settings
2. Add deployment script in `infra/scripts/`
3. Add new job in `ci-cd.yml`
4. Update environment protection rules

### Modifying Security Thresholds
Edit `pom.xml` and `owasp-suppressions.xml`:
- Change CVSS threshold in `failBuildOnCVSS`
- Add suppressions for false positives
- Configure notification settings

### Custom Notifications
Extend the `notify` job in `ci-cd.yml`:
- Add Slack/Teams integration
- Configure email notifications
- Add custom deployment reports

---

For more information, see the main project documentation in the root directory.