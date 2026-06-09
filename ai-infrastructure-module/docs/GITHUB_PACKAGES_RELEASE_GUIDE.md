# AI Fabric Framework GitHub Packages Release Guide

This guide releases the AI Fabric Framework as Maven packages in GitHub Packages and as a framework-only source archive attached to a GitHub Release.

It does not release Platform, Shopify Bridge, Partner UI, ProdUS, Coolify deployments, or production runtime services.

## Release Target

- Maven registry: `https://maven.pkg.github.com/mahmoudashraf/AI-Fabric-Framework`
- Maven group: `com.ai.fabric`
- Parent/BOM artifact: `ai-fabric-spring-boot-starter`
- Source asset name: `ai-fabric-framework-source-<version>.tar.gz`
- Release tag format: `ai-fabric-framework-v<version>`

Use a preview version for the first package release unless the framework API is intentionally being declared stable, for example:

```bash
0.1.0-preview
```

## What Gets Published

The GitHub Actions release workflow publishes the framework Maven reactor, excluding integration-test modules.

Included module families:

- core framework modules
- runtime module
- starter modules
- provider modules
- curated packs
- RAG/web/relationship/chat/behavior modules
- vector database modules
- migration, governance, indexing, PII, relay, action, connector, and registry modules

Excluded module family:

- `integration-Testing/*`

## Before Release

1. Confirm the repo is clean except for intentional release changes:

```bash
git status --short
```

2. Set the Maven version across the framework reactor:

```bash
mvn -f ai-infrastructure-module/pom.xml versions:set \
  -DnewVersion=0.1.0-preview \
  -DgenerateBackupPoms=false
```

3. Verify the publishable framework module set:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml \
  -pl ".,ai-infrastructure-core,ai-infrastructure-actions-connector,ai-infrastructure-actions-registry,ai-infrastructure-actions-registry-liquibase,ai-infrastructure-retrieval-connector,ai-infrastructure-data-sync,ai-infrastructure-relay,ai-infrastructure-generic-rest-connector,ai-fabric-runtime,ai-infrastructure-indexing,ai-infrastructure-pii,ai-infrastructure-governance,ai-fabric-starter,ai-fabric-provider-starter,curated/ai-curated-default,curated/ai-curated-commerce,curated/ai-curated-support,ai-infrastructure-rag,ai-infrastructure-web,providers/ai-infrastructure-provider-openai,providers/ai-infrastructure-provider-azure,providers/ai-infrastructure-provider-cohere,providers/ai-infrastructure-provider-anthropic,providers/ai-infrastructure-provider-gemini,providers/ai-infrastructure-onnx-starter,ai-infrastructure-relationship-query,ai-infrastructure-chat-session,ai-infrastructure-behavior,victor-databases/ai-infrastructure-vector-lucene,victor-databases/ai-infrastructure-vector-pinecone,ai-infrastructure-migration,victor-databases/ai-infrastructure-vector-memory,victor-databases/ai-infrastructure-vector-weaviate,victor-databases/ai-infrastructure-vector-qdrant,victor-databases/ai-infrastructure-vector-milvus" \
  -am \
  -DskipITs \
  verify
```

4. Commit the version and release metadata changes:

```bash
git add ai-infrastructure-module/pom.xml ai-infrastructure-module/**/pom.xml .github/workflows/ai-fabric-framework-github-packages-release.yml ai-infrastructure-module/docs/GITHUB_PACKAGES_RELEASE_GUIDE.md
git commit -m "Prepare AI Fabric Framework GitHub Packages release"
```

5. Tag and push:

```bash
git tag ai-fabric-framework-v0.1.0-preview
git push origin Platform-V10
git push origin ai-fabric-framework-v0.1.0-preview
```

6. Create a GitHub Release from that tag.

The `AI Fabric Framework GitHub Packages Release` workflow runs on the published release. It validates that the tag matches the Maven version, publishes packages to GitHub Packages, and uploads the framework-only source archive plus SHA-256 checksum to the release.

## Consuming From Maven

Consumers need GitHub Packages access. For private packages, local users should use a GitHub personal access token with package read access. CI can use a GitHub token with package read access.

Example `~/.m2/settings.xml` using environment variables:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_USERNAME}</username>
      <password>${env.GITHUB_PACKAGES_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Consumer `pom.xml` repository and BOM import:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/mahmoudashraf/AI-Fabric-Framework</url>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.ai.fabric</groupId>
      <artifactId>ai-fabric-spring-boot-starter</artifactId>
      <version>0.1.0-preview</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Example dependency set:

```xml
<dependencies>
  <dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-starter</artifactId>
  </dependency>

  <dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-provider-openai</artifactId>
  </dependency>

  <dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
  </dependency>

  <dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-vector-lucene</artifactId>
  </dependency>
</dependencies>
```

## Manual Local Deploy

If you need to deploy from a local machine instead of GitHub Actions, configure the same `github` server in `~/.m2/settings.xml`, then run:

```bash
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml \
  -pl ".,ai-infrastructure-core,ai-infrastructure-actions-connector,ai-infrastructure-actions-registry,ai-infrastructure-actions-registry-liquibase,ai-infrastructure-retrieval-connector,ai-infrastructure-data-sync,ai-infrastructure-relay,ai-infrastructure-generic-rest-connector,ai-fabric-runtime,ai-infrastructure-indexing,ai-infrastructure-pii,ai-infrastructure-governance,ai-fabric-starter,ai-fabric-provider-starter,curated/ai-curated-default,curated/ai-curated-commerce,curated/ai-curated-support,ai-infrastructure-rag,ai-infrastructure-web,providers/ai-infrastructure-provider-openai,providers/ai-infrastructure-provider-azure,providers/ai-infrastructure-provider-cohere,providers/ai-infrastructure-provider-anthropic,providers/ai-infrastructure-provider-gemini,providers/ai-infrastructure-onnx-starter,ai-infrastructure-relationship-query,ai-infrastructure-chat-session,ai-infrastructure-behavior,victor-databases/ai-infrastructure-vector-lucene,victor-databases/ai-infrastructure-vector-pinecone,ai-infrastructure-migration,victor-databases/ai-infrastructure-vector-memory,victor-databases/ai-infrastructure-vector-weaviate,victor-databases/ai-infrastructure-vector-qdrant,victor-databases/ai-infrastructure-vector-milvus" \
  -am \
  -DskipTests \
  deploy
```

Prefer GitHub Actions for the canonical release because it ties Maven packages, tag, release, source archive, and checksum to the same commit.

## Release Boundaries

- Do not include platform deployment credentials, customer runtime credentials, Coolify tokens, Shopify tokens, or private handoff files.
- Do not present this package as the managed LoomAI Platform. It is the reusable AI Fabric Framework.
- Do not publish a stable `1.0.0` package until API compatibility, docs, license posture, source/javadoc expectations, and support scope are intentionally declared stable.
