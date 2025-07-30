# Documentation plan

Let's figure out a way to document everything. The plan should include considerations as to how we distribute the documentation:
- Should be easily accessible (offline and online)
- Should be easy to read and consult
- Should be easy to update (either by AI or manually)
- Should consider accessibility (visual accessibility, reading level, screen readers)
- Should consider whether they should be a single entry point so everyone can see everything
  - Developers will likely want to see it all, but other audiences probably won't 

## Technical onboarding doc
- Audience: Developers who will contribute to the project or who need to use the project behind the scenes (consumers who will call the API via HTTP, but consumes who will use the package to call it programmatically)
- Goal: Learn how the system works and how to contribute to it
- Technical level: High
- Content:
  - Downloading and installing the package
  - Running the package locally
  - Testing the APIs locally and in stage and in prod
    -  How to test with CURL, HTTP client (insomnia or PostMan) and directly on the AWS console
    - Include definitions for clients and CURL requests
    - We should discourage testing in prod or sanbox a test user
  - Diagrams of the CD/CI pipeline
    - Include technical information, including why we chose this architecture, how to update, edit, and troubleshoot
    - Should be a format we can update later
  - Diagrams of the AWS architecture
  - Package structure
    - Include details of separations of concerns
  - How to add or edit the API contract
  - How to add or edit services
  - How to add or edit testing (unit testing and integration testing)
  - How to deploy
  - How to manage users
  - How to consume the package programmatically
  - API documentation
    - Contracts
    - Expected inputs and outputs
    - Error models
  - Best practices

## Front end development
- Audience: Consumers who will make API calls via web or apps
- Goal: Empower front end developers to use the API
- Content:
  - High-level architecture
  - API documentation
    - Contracts
    - Expected inputs and outputs
    - Error models
  - How to test the APIs
    - Via Curl and HTTP requests
    - Definitions for clients