## **Contributing Guidelines**

### Getting Started
1. Fork the repository
2. Clone your fork locally
3. Create a feature branch
4. Make your changes
5. Test your changes
6. Submit a pull request

### Code Standards
- Follow existing code style
- Write meaningful commit messages
- Include tests for new features
- Update documentation as needed

### Community Guidelines
- Be respectful and inclusive
- Provide constructive feedback
- Help others learn and grow
- Follow the project's Code of Conduct



## **Branch Strategy**

### Git Flow Based Branch Strategy

#### Main Branches
- **`main`** (or `master`): Production-ready code
- **`develop`**: Integration branch for development
- **`feature/*`**: New feature development
- **`hotfix/*`**: Critical bug fixes
- **`release/*`**: Release preparation

#### Branch Naming Convention

```
feature/issue-number-brief-description
hotfix/issue-number-brief-description
release/version-number
```

**Examples:**
```
feature/123-user-authentication
hotfix/456-login-bug-fix
release/1.2.0
```

#### Workflow

1. **Feature Development**
   ```bash
   # Create feature branch from develop
   git checkout develop
   git pull origin develop
   git checkout -b feature/123-user-authentication
   
   # After development, create PR to develop
   ```

2. **Hotfix**
   ```bash
   # Create hotfix branch from main
   git checkout main
   git pull origin main
   git checkout -b hotfix/456-critical-bug
   
   # After fix, create PRs to both main and develop
   ```

3. **Release**
   ```bash
   # Create release branch from develop
   git checkout develop
   git pull origin develop
   git checkout -b release/1.2.0
   
   # After release preparation, create PR to main
   ```

---

## **Pull Request Guidelines**

### PR Title Format

```
type(scope): brief description

Types:
- feat: new feature
- fix: bug fix
- docs: documentation update
- style: code formatting
- refactor: code refactoring
- test: adding/updating tests
- chore: miscellaneous tasks
```

**Examples:**
- `feat(auth): add social login functionality`
- `fix(api): resolve user info retrieval bug`
- `docs(readme): update installation guide`

### PR Description Guidelines

#### 1. Summary of Changes
- Clearly describe what was changed
- Explain why this change was necessary

#### 2. Testing Instructions
- Provide methods to verify the changes
- Include screenshots or GIFs for UI changes

#### 3. Related Issues
- Link related issues: `Closes #123`, `Fixes #456`

#### 4. Checklist
- [ ] Code follows the project's style guidelines
- [ ] Self-review completed
- [ ] Appropriate tests added
- [ ] Documentation updated (if needed)

---

## Pull Request Template

```markdown
## Summary of Changes
<!-- Briefly describe what this PR changes -->

## Type of Change
<!-- Please check the type of change your PR introduces -->
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement
- [ ] Test addition

## Related Issues
<!-- Link any related issues -->
Closes #issue-number

## Testing Instructions
<!-- Describe how to test this change -->
1. 
2. 
3. 

## Screenshots (if applicable)
<!-- Add before/after screenshots for UI changes -->

## Checklist
<!-- Check off completed items -->
- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes

## Additional Notes
<!-- Any additional information for reviewers -->
```

---

## Code Review Guidelines

### For Reviewers

#### Review Checklist
1. **Functionality**: Does the code work as intended?
2. **Readability**: Is the code easy to understand?
3. **Performance**: Are there any performance concerns?
4. **Security**: Are there any security vulnerabilities?
5. **Testing**: Are appropriate tests included?

#### Feedback Guidelines
- Provide **specific and constructive** feedback
- Include **positive feedback** for good parts
- Use **suggestion format**: "What do you think about...?"
- Provide **code examples** when possible

### Review Priority
1. **Critical**: Bugs, security issues, performance problems
2. **Major**: Design issues, readability problems
3. **Minor**: Style, naming conventions

### Review Completion Criteria
- [ ] All Critical/Major issues resolved
- [ ] Tests passing
- [ ] Documentation updated
- [ ] At least one approval from maintainer

---

## Commit Message Convention

### Format
```
type(scope): subject

body

footer
```

### Types
- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **chore**: Changes to the build process or auxiliary tools

### Examples
```
feat(auth): add OAuth2 integration

Add support for Google and GitHub OAuth2 providers.
This allows users to sign in with their existing accounts.

Closes #123
```

---

