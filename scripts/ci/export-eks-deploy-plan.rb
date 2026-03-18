#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"
require "pathname"
require "time"
require "yaml"
require "fileutils"

ROOT_DIR = File.expand_path("../..", __dir__)

options = {
  catalog_path: File.join(ROOT_DIR, "deploy/catalog/runtime-services.yaml"),
  values_directory: File.join(ROOT_DIR, "deploy/helm/environments/dev"),
  output_path: File.join(ROOT_DIR, "build-output/eks-deploy-plan.json")
}

OptionParser.new do |parser|
  parser.on("--output PATH") { |value| options[:output_path] = File.expand_path(value, ROOT_DIR) }
  parser.on("--catalog PATH") { |value| options[:catalog_path] = File.expand_path(value, ROOT_DIR) }
  parser.on("--values-dir PATH") { |value| options[:values_directory] = File.expand_path(value, ROOT_DIR) }
end.parse!(ARGV)

def load_yaml(path)
  data = YAML.load_file(path)
  data.is_a?(Hash) ? data : {}
end

catalog = load_yaml(options[:catalog_path])
services = catalog.dig("spec", "services") || []
namespace_default = catalog.dig("spec", "namespace")

catalog_keys = services.map { |service| service.fetch("key") }
targets = ENV.fetch("DEPLOY_TARGETS", "__FULL__").split(",").map(&:strip).reject(&:empty?)
full_deploy = targets.empty? || targets == ["__FULL__"]
unknown_targets = full_deploy ? [] : targets - catalog_keys

abort("Unknown deploy target(s): #{unknown_targets.join(', ')}") unless unknown_targets.empty?

selected_services =
  if full_deploy
    services
  else
    services.select { |service| targets.include?(service.fetch("key")) }
  end

aws_region = ENV["AWS_DEFAULT_REGION"] || ENV["AWS_REGION"]
account_id = ENV["ACCOUNT_ID"] || ENV["AWS_ACCOUNT_ID"]
image_tag = ENV["SHORT_TAG"] || ENV["IMAGE_TAG"] || "latest"
image_tag_mode = ENV.fetch("IMAGE_TAG_MODE", "source")

abort("Unsupported IMAGE_TAG_MODE: #{image_tag_mode}") unless %w[source values].include?(image_tag_mode)

plan_services = selected_services.map do |service|
  key = service.fetch("key")
  build_type = service.dig("build", "type") || "gradle"
  values_path = File.join(options[:values_directory], "#{key}.yaml")
  abort("Missing values file for #{key}: #{values_path}") unless File.exist?(values_path)

  values = load_yaml(values_path)
  release_name = service.dig("deployment", "releaseName") || key
  rollout_name = values["fullnameOverride"].to_s.strip
  rollout_name = release_name if rollout_name.empty?

  image_repository =
    if build_type == "external-image"
      values.dig("image", "repository").to_s.strip.tap do |value|
        abort("Missing image.repository for external-image service #{key}") if value.empty?
      end
    else
      abort("Missing AWS region for build plan generation") if aws_region.to_s.empty?
      abort("Missing account id for build plan generation") if account_id.to_s.empty?

      repository = service.dig("image", "ecrRepository").to_s.strip
      abort("Missing image.ecrRepository for #{key}") if repository.empty?

      "#{account_id}.dkr.ecr.#{aws_region}.amazonaws.com/#{repository}"
    end

  resolved_image_tag =
    if build_type == "external-image" || image_tag_mode == "values"
      values.dig("image", "tag").to_s.strip.empty? ? "latest" : values.dig("image", "tag").to_s.strip
    else
      image_tag
    end

  {
    "key" => key,
    "buildType" => build_type,
    "gradleModule" => service.dig("build", "gradleModule"),
    "namespace" => service.dig("deployment", "namespace") || namespace_default,
    "releaseName" => release_name,
    "rolloutName" => rollout_name,
    "rolloutWave" => service.dig("deployment", "rolloutWave") || 999,
    "serviceName" => service.dig("deployment", "serviceName"),
    "valuesFile" => Pathname.new(values_path).relative_path_from(Pathname.new(ROOT_DIR)).to_s,
    "imageRepository" => image_repository,
    "imageTag" => resolved_image_tag
  }
end

catalog_order = catalog_keys.each_with_index.to_h
plan_services.sort_by! do |service|
  [
    service.fetch("rolloutWave"),
    catalog_order.fetch(service.fetch("key"), catalog_order.length)
  ]
end

plan = {
  "environment" => catalog.dig("spec", "environment"),
  "namespace" => namespace_default,
  "fullDeploy" => full_deploy,
  "selectedTargets" => full_deploy ? catalog_keys : targets,
  "generatedAt" => Time.now.utc.iso8601,
  "services" => plan_services
}

output_dir = File.dirname(options[:output_path])
FileUtils.mkdir_p(output_dir)
File.write(options[:output_path], "#{JSON.pretty_generate(plan)}\n")

warn("[INFO] Generated EKS deploy plan: #{options[:output_path]}")
warn("[INFO] Selected services: #{plan_services.map { |service| service.fetch('key') }.join(', ')}")
