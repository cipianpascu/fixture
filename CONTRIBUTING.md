# Contributing to Backend Gateway

Thank you for your interest in contributing to Backend Gateway! This document provides guidelines and instructions for contributing.

## Code of Conduct

Please be respectful and constructive in all interactions with other contributors and maintainers.

## Getting Started

### Prerequisites
- Java 21
- Maven 3.6+
- Docker (optional, for integration tests)
- PostgreSQL (for fixture mode development)
- Git

### Setting Up Development Environment

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/your-username/backend-gateway.git
   cd backend-gateway
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

4. **Start the application locally**
   ```bash
   # Routing mode
   mvn spring-boot:run -Dspring-boot.run.profiles=routing

   # Fixture mode
   mvn spring-boot:run -Dspring-boot.run.profiles=fixture
   ```

## Development Guidelines

### Code Style
- Follow standard Java conventions
- Use meaningful variable and method names
- Keep methods focused and concise
- Maximum line length: 120 characters
- Use Lombok annotations to reduce boilerplate
- Add JavaDoc comments for public APIs

### Package Structure
```
com.agent.gateway/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── dto/            # Data Transfer Objects
├── entity/         # JPA entities
├── exception/      # Custom exceptions and handlers
├── model/          # Enums and domain models
├── repository/     # JPA repositories
└── service/        # Business logic
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `BackendConfigService`)
- **Methods**: camelCase (e.g., `findMatchingResponse`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_ATTEMPTS`)
- **Variables**: camelCase (e.g., `backendName`)
- **Packages**: lowercase (e.g., `com.agent.gateway.service`)

### Testing
- Write unit tests for all service layer methods
- Use Mockito for mocking dependencies
- Aim for >80% code coverage
- Test both success and failure scenarios
- Use descriptive test method names: `test{MethodName}_{Scenario}`

Example:
```java
@Test
void testFindMatchingResponse_Success() {
    // Given
    // When
    // Then
}
```

### Logging
- Use SLF4J with Lombok's `@Slf4j`
- Log levels:
  - **ERROR**: System errors, exceptions
  - **WARN**: Recoverable issues, deprecated usage
  - **INFO**: Important business events
  - **DEBUG**: Detailed diagnostic information
- Include context in log messages:
  ```java
  log.info("Processing request for backend: {}", backendName);
  ```

## Making Changes

### Branching Strategy
- `main`: Production-ready code
- `develop`: Integration branch for features
- Feature branches: `feature/description`
- Bug fixes: `bugfix/description`
- Hotfixes: `hotfix/description`

### Workflow

1. **Create a branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write clean, well-documented code
   - Add tests for new functionality
   - Update documentation if needed

3. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add support for new security type"
   ```

### Commit Message Format
Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(mock): add support for regex path matching
fix(proxy): handle timeout exceptions properly
docs(readme): update installation instructions
test(service): add tests for MockService
```

4. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Create a Pull Request**
   - Provide a clear description of changes
   - Reference any related issues
   - Ensure all tests pass
   - Request review from maintainers

## Pull Request Guidelines

### Before Submitting
- [ ] Code compiles without errors
- [ ] All tests pass: `mvn test`
- [ ] New code has tests
- [ ] Documentation is updated
- [ ] Code follows project style guidelines
- [ ] Commit messages follow conventions
- [ ] No unnecessary dependencies added

### PR Description Template
```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe how you tested your changes.

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No breaking changes (or documented)
```

## Reporting Issues

### Bug Reports
Include:
- Clear title and description
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, Java version, etc.)
- Relevant logs or error messages
- Screenshots if applicable

### Feature Requests
Include:
- Clear use case description
- Proposed solution or approach
- Any alternatives considered
- Willingness to contribute implementation

## Development Tips

### Hot Reload
Use Spring Boot DevTools for faster development:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Debugging
- Use logging instead of System.out.println
- Enable DEBUG level for specific packages in `application.yml`
- Use breakpoints in your IDE
- Review Actuator endpoints for runtime information

### Database Changes
- Test with both H2 and PostgreSQL
- Use Liquibase or Flyway for migrations (if adding)
- Ensure backward compatibility

### Performance Considerations
- Use appropriate JPA fetch types
- Implement pagination for large result sets
- Consider caching for frequently accessed data
- Profile code for performance bottlenecks

## Release Process

Releases are managed by maintainers:
1. Update version in `pom.xml`
2. Update `CHANGELOG.md`
3. Create release tag
4. Build and publish artifacts
5. Update documentation

## Getting Help

- **Documentation**: Check README.md and QUICKSTART.md
- **Issues**: Search existing issues for similar problems
- **Discussions**: Use GitHub Discussions for questions
- **Contact**: Create an issue for specific problems

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

---

Thank you for contributing to Backend Gateway! 🚀
