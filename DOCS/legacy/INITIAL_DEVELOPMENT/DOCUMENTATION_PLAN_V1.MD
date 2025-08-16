# Documentation plan
- Written by: Bernardo
- Reviewed by: Bernardo
- Last updated: 08/03/25
- Status: Draft

Let's figure out a way to document everything. The plan should include considerations as to how we distribute the documentation:
- Should be easily accessible (offline and online)
- Should be easy to read and consult
- Should be easy to update (either by AI or manually)
- Should consider accessibility (visual accessibility, reading level, screen readers)
- Should consider whether they should be a single entry point so everyone can see everything
  - Developers will likely want to see it all, but other audiences probably won't 
- Should consider what information should be in the git's README, what should we linked in the git's README, and what doesn't need to be easily accesible (but still accessible)
- Should consider how to distribute the content
  - Should it be a wiki?
  - Should it be a website?
  - Is tehre an out of the box solution or should it be custom>
  - How can we update the content
  - Can we access it offline?
  - this is a planning document; review where in the overall file structure the actual documentation should go

- Parallel to producing new documentation, evaluate all existing doumentaiton and evaluate the code and infrastructure and update all existing documentation to make sure it is up to date. Include the date of the latest update for all documents, including the ones we are packaging now.

- All code should be properly documented and if there are any possible refactors (rename, reorganize, etc) to improve code legibility, annotate with TODO tasks. Code comments should be limited to where they are required for complex business logic; outside of Javadocs, we should minimize documentation

## Retrsopect
- Context: you are a technical project manager who oversaw the entire process. You have ten years of exprience, icluding some experience coding and writing technical documentation. You are great at writin and documenting
- Audience Developers and me
- Goal: Document the project
- Content:
  - Come up with a comprehensive retrospect of V1 of the project
- 
## Technical onboarding doc
- Context: you are a principal engineer with a strong sense of technical documentation. you understand code, infrastructure, and what makes good documentation. you put yourself in the place of junior and mid-level engineers and can easily adapt how you explain the work to educate these less-experienced engineers. You have strong expertise in API design and AWS cloud architecture
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
  - Custom URL routing
    - Document that it has been implemented but behind the feature flag
  - Best practices
  - feature flags
  - versioning (include maintanance and best pracrices)
  - environmental variables
  - modifying the pipeline features
  - UAT
  - creeating metrics and monitoring them
  - crednentialing
  - Write a granular guide to code, packages, how to call each resource (lamba, ddb, redis, etc) via code
  - Include references to code and online documentation when appropriate

## Front end development
- Context: you are a senior front-end engineer, focusing on technical writing and implementing APIs in applications. You have over 10 years of experience and know how to write technical documentation that speaks to engineers of different levels. You understand the ins and outs of API design and usage. You write documetns that are accessible, yet technically commplete.
- Audience: Consumers who will make API calls via web or apps
- Goal: Empower front end developers to use the API
- Technical level: medium, but should be a complete guide; a front end developer should be able to use 100% of the API but not need to understand the inner workings
- Content:
  - High-level architecture
  - API documentation
    - Contracts
    - Expected inputs and outputs
    - Error models
  - How to test the APIs
    - Via Curl and HTTP requests
    - Definitions for clients
  - Using the APIs in web
    - For all, include examples for JS calls, TS call, React, and other common technologies)
    - How to onboard
    - Registering to use the APIs and get a key
    - How to authenticate a user
    - Manage a user (create, delete, update credentials)
    - Make calls to the APIs
    - examples of using the API in a web UI
  - Using the APIs for apps
    - Same content as in web, but include ios, android, mac and windows
    - Include anything additional that applies to apps but not web
  - Troubleshooting
  - Updating the infra, packages, etc.. keeping dependencies, code integrity, and infra stable and fresh
  - Mocking and testing locally
- 

## How we developed
- Context: You are me, ten years in the future, with extensive expreice in coding, architecting, AWS, etc. You understand how I work and learn and write great documetnation for your past self (me today).
- Audience: Me
- Goal: Understand everything we did together; I should be able to understand everything granularly and to reproduce the whole project manually
- Content:
  - Granular explanations of everything in the package
    - languages, why we used them, how each decision was made
    - modeling, services, deployment
  - Granular explanation of the architecture
    - architecture
    - each individual element (compute, api, deployment, lambdas)
    - Monitoring stack
  - Security
    - What is the level of security
    - What are the layers of data security
    - Areas of vulnerability and solutions
    - Best practices
  - Granular explanation of the process we followed step by step
    - Give me a retrospect of the process
    - What were some of our difficulties? How did we solve them?
    - What did we learn?
    - What compromises did we make? 
    - what improvements did we make over time?
    - how did the project evolve from the initial specs and why?
  - At the end of this, I should be able to learn about AWS architecture, API architecture, understand and reproduce the whole process manually and I should be able to reproduce the whole process with you
  - AW guide
    - Give me a high-level, but ationably guide to AWS develoopment
    - How to create and manage accounts
    - The diffrent technlogies and why you use the, (lambdas, compute, routing, database, IAM, AI, mointoring, etc); include which are free and paid, and add references to online documentation (give me enough info to understand the tech and start using it, but reference online docs for deep dive)
  - Make it a deep explanation, but with language that is easy to understand; should be a good balance of technical and layman

## AI Guide
- Context: You are Claude.AI and understand the entire codebase and how to create documetntion to reproduce this project, adapting to new requirements
- Audience: You
- Goal: To be able to reproduce this project for future projects
- Content:
  - Anything you would need to be able to create a new API with the same features and architecture
  - If I want to create another API project, we should be able to answer some questions and have you recreate a shell project like this one

## Thought Leadership
- Audience: People who could hire me
- Goal: Talk about what I did to demonstrate my competence and land a job
- Content:
  - Create a series of LinkedIn posts and other content 



## API routing setup
I used the following to setup the API routing; there was a mix of manual setup and CDK deployment:

1. Register Your Domain
   Make sure your custom domain is registered, either using AWS Route 53 or another domain registrar.
2. Create/Import an ACM SSL Certificate
   Navigate to AWS Certificate Manager (ACM).
   Request or import an SSL certificate for your domain (e.g., api.example.com). This certificate must be in the same AWS region as your API Gateway API.
   Validate the certificate (usually via DNS validation using a CNAME record in Route 53).
3. Set Up a Custom Domain in API Gateway
   Open the API Gateway console.
   Go to "Custom Domain Names".
   Click "Create" and enter your domain name (e.g., api.example.com).
   Attach the ACM certificate you just created.
   Choose the regional endpoint type (recommended for most use cases).
   Once the domain is created, you'll receive a target domain name (API Gateway URL).
4. Map Your API to the Custom Domain
   In API Gateway, go to the custom domain you just created.
   Use "API Mappings" to associate your API and its stage with this custom domain and set the base path mapping (like /v1 or / for root).
5. Create a DNS Record in Route 53
   Go to the Route 53 console and find the hosted zone for your domain.
   If using Route 53, create a new record:
   Record type: A (Alias)
   Name: The subdomain (e.g., api)
   Alias to: The API Gateway regional domain name you received earlier
   Region: Select the AWS region of your API
   If using another DNS provider, use a CNAME record pointing to the regional API Gateway domain.
6. Test Your Custom Domain
   Once the DNS propagates, you can now access your API using your custom URL, e.g., https://api.example.com/myapi.

I did steps 1 and 2 manually, the rest hapened via CDK. There is an alternative for you to give me NS records to add to the domain. Document this too, but include a note that this gives full control over the domain to route 53