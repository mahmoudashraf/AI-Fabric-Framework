#!/usr/bin/env ruby

require "json"
require "yaml"

deployment_root = File.expand_path("..", __dir__)
profile_path = ARGV.fetch(0, File.join(__dir__, "staging-profile.json"))
profile = JSON.parse(File.read(profile_path))

payload = {
  "actionsConfig" => YAML.safe_load(File.read(File.join(deployment_root, "runtime", "ai-actions.yml")), aliases: true),
  "entityConfig" => YAML.safe_load(File.read(File.join(deployment_root, "runtime", "ai-entity-config.yml")), aliases: true),
  "routingConfig" => YAML.safe_load(File.read(File.join(deployment_root, "connector", "actions-routing.yml")), aliases: true)
}.merge(profile)

puts JSON.pretty_generate(payload)
