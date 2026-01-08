#!/usr/bin/env node
/**
 * Generate Provider Summary from Registry
 * Reads providers-registry.yml and generates a summary report
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const PROJECT_ROOT = path.resolve(__dirname, '../..');
const REGISTRY_FILE = path.join(
  PROJECT_ROOT,
  'ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml'
);

function loadRegistry() {
  try {
    const fileContents = fs.readFileSync(REGISTRY_FILE, 'utf8');
    return yaml.load(fileContents);
  } catch (error) {
    console.error(`Error loading registry: ${error.message}`);
    process.exit(1);
  }
}

function generateSummary() {
  const registry = loadRegistry();
  const providers = registry.providers;

  console.log('# Provider Registry Summary\n');
  console.log('Generated from: `providers-registry.yml`\n');

  // LLM Providers
  console.log('## LLM Providers\n');
  const llmProviders = providers.llm || {};
  Object.keys(llmProviders).forEach(name => {
    const provider = llmProviders[name];
    console.log(`### ${provider.displayName || name}`);
    console.log(`- **Name**: \`${name}\``);
    console.log(`- **Description**: ${provider.description || 'N/A'}`);
    console.log(`- **Default Model**: ${provider.defaultModel || 'N/A'}`);
    console.log(`- **API Key Env Var**: ${provider.apiKeyEnvVar || 'N/A'}`);
    console.log(`- **Required**: ${provider.required ? 'Yes' : 'No'}`);
    console.log(`- **Enabled**: ${provider.enabled ? 'Yes' : 'No'}`);
    if (provider.additionalEnvVars && provider.additionalEnvVars.length > 0) {
      console.log(`- **Additional Env Vars**: ${provider.additionalEnvVars.join(', ')}`);
    }
    console.log('');
  });

  // Embedding Providers
  console.log('## Embedding Providers\n');
  const embeddingProviders = providers.embedding || {};
  Object.keys(embeddingProviders).forEach(name => {
    const provider = embeddingProviders[name];
    console.log(`### ${provider.displayName || name}`);
    console.log(`- **Name**: \`${name}\``);
    console.log(`- **Description**: ${provider.description || 'N/A'}`);
    console.log(`- **Default Model**: ${provider.defaultModel || 'N/A'}`);
    if (provider.dimensions) {
      console.log(`- **Dimensions**: ${provider.dimensions}`);
    }
    console.log(`- **API Key Env Var**: ${provider.apiKeyEnvVar || 'N/A'}`);
    console.log(`- **Required**: ${provider.required ? 'Yes' : 'No'}`);
    console.log(`- **Enabled**: ${provider.enabled ? 'Yes' : 'No'}`);
    if (provider.additionalEnvVars && provider.additionalEnvVars.length > 0) {
      console.log(`- **Additional Env Vars**: ${provider.additionalEnvVars.join(', ')}`);
    }
    console.log('');
  });

  // Summary Statistics
  console.log('## Summary Statistics\n');
  console.log(`- **Total LLM Providers**: ${Object.keys(llmProviders).length}`);
  console.log(`- **Total Embedding Providers**: ${Object.keys(embeddingProviders).length}`);
  console.log(`- **Enabled LLM Providers**: ${Object.values(llmProviders).filter(p => p.enabled !== false).length}`);
  console.log(`- **Enabled Embedding Providers**: ${Object.values(embeddingProviders).filter(p => p.enabled !== false).length}`);
}

// Check if js-yaml is available
try {
  require.resolve('js-yaml');
  generateSummary();
} catch (error) {
  console.error('Error: js-yaml not found. Install with: npm install js-yaml');
  console.error('Or use the bash script: validate-provider-registry.sh');
  process.exit(1);
}
