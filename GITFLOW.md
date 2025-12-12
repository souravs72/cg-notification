# GitFlow Workflow Guide

This project follows the GitFlow branching model for organized development and releases.

## Branch Structure

### Main Branches

- **`main`**: Production-ready code. Always deployable.
- **`develop`**: Integration branch for features. Latest development changes.

### Supporting Branches

- **`feature/*`**: New features branching off from `develop`
- **`release/*`**: Release preparation branches from `develop`
- **`hotfix/*`**: Critical bug fixes directly from `main`

## Workflow

### Feature Development

1. Create feature branch from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

2. Develop and commit changes:
   ```bash
   git add .
   git commit -m "feat: add new feature"
   ```

3. Push feature branch:
   ```bash
   git push origin feature/your-feature-name
   ```

4. Create Pull Request to `develop`
5. After review and merge, delete feature branch

### Release Process

1. Create release branch from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b release/v1.0.0
   ```

2. Finalize release (version bumps, changelog, etc.)
3. Create Pull Request to `main` and `develop`
4. Merge to `main` and tag:
   ```bash
   git checkout main
   git merge release/v1.0.0
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin main --tags
   ```
5. Merge back to `develop`
6. Delete release branch

### Hotfix Process

1. Create hotfix branch from `main`:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b hotfix/critical-bug-fix
   ```

2. Fix the issue and commit:
   ```bash
   git add .
   git commit -m "fix: critical bug fix"
   ```

3. Create Pull Request to `main`
4. Merge to `main` and tag:
   ```bash
   git checkout main
   git merge hotfix/critical-bug-fix
   git tag -a v1.0.1 -m "Hotfix version 1.0.1"
   git push origin main --tags
   ```
5. Merge back to `develop`
6. Delete hotfix branch

## Commit Message Convention

Follow conventional commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Code style changes (formatting, etc.)
- `refactor:` Code refactoring
- `test:` Adding or updating tests
- `chore:` Maintenance tasks

## Branch Protection Rules

- `main`: Requires PR review, status checks must pass
- `develop`: Requires PR review, status checks must pass
- Feature branches: No restrictions

## GitHub Secrets

The following secrets are stored in GitHub Secrets (Settings → Secrets and variables → Actions):

- `SENDGRID_API_KEY`: SendGrid API key for email notifications
- `WASENDER_API_KEY`: WASender API key for WhatsApp notifications
- `DB_PASSWORD`: Database password for production
- `DB_USERNAME`: Database username for production

These secrets are used in CI/CD pipelines and should never be committed to the repository.

