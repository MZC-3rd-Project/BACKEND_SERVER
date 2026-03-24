#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"

options = {
  baseline_label: "baseline",
  candidate_label: "candidate"
}

OptionParser.new do |parser|
  parser.banner = "Usage: ruby deploy/performance/summarize-k6-results.rb --baseline BASELINE.json --candidate CANDIDATE.json"

  parser.on("--baseline PATH", "k6 summary-export JSON for the baseline run") do |value|
    options[:baseline_path] = value
  end

  parser.on("--candidate PATH", "k6 summary-export JSON for the candidate run") do |value|
    options[:candidate_path] = value
  end

  parser.on("--baseline-label LABEL", "Display label for the baseline column") do |value|
    options[:baseline_label] = value
  end

  parser.on("--candidate-label LABEL", "Display label for the candidate column") do |value|
    options[:candidate_label] = value
  end
end.parse!(ARGV)

abort("Missing --baseline") unless options[:baseline_path]
abort("Missing --candidate") unless options[:candidate_path]

def load_summary(path)
  JSON.parse(File.read(path))
rescue Errno::ENOENT
  abort("Summary file not found: #{path}")
rescue JSON::ParserError => e
  abort("Invalid JSON in #{path}: #{e.message}")
end

def metric_value(summary, metric_name, key)
  metric = summary.fetch("metrics", {}).fetch(metric_name, {})
  return metric.fetch(key, nil) if metric.key?(key)

  metric.fetch("values", {}).fetch(key, nil)
end

def format_value(value, unit: nil, precision: 2, percentage: false)
  return "n/a" if value.nil?

  formatted =
    if percentage
      format("%.#{precision}f%%", value * 100.0)
    elsif value.is_a?(Numeric)
      format("%.#{precision}f", value)
    else
      value.to_s
    end

  unit ? "#{formatted} #{unit}" : formatted
end

def format_delta(baseline, candidate, percentage: false, precision: 2, lower_is_better: true)
  return "n/a" if baseline.nil? || candidate.nil?

  if baseline.to_f.zero?
    absolute = candidate - baseline
    sign = absolute.positive? ? "+" : ""
    return percentage ? "#{sign}#{format("%.#{precision}f", absolute * 100.0)}pp" : "#{sign}#{format("%.#{precision}f", absolute)}"
  end

  delta = ((candidate - baseline) / baseline.to_f) * 100.0
  sign = delta.positive? ? "+" : ""
  trend = if delta.zero?
            "same"
          elsif (delta.negative? && lower_is_better) || (delta.positive? && !lower_is_better)
            "better"
          else
            "worse"
          end

  "#{sign}#{format("%.#{precision}f", delta)}% (#{trend})"
end

baseline = load_summary(options[:baseline_path])
candidate = load_summary(options[:candidate_path])

rows = [
  {
    name: "requests",
    metric: "http_reqs",
    key: "count",
    unit: nil,
    precision: 0,
    lower_is_better: false
  },
  {
    name: "failure rate",
    metric: "http_req_failed",
    key: "value",
    percentage: true,
    precision: 2,
    lower_is_better: true
  },
  {
    name: "p95 latency",
    metric: "http_req_duration",
    key: "p(95)",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "p99 latency",
    metric: "http_req_duration",
    key: "p(99)",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "avg connecting time",
    metric: "http_req_connecting",
    key: "avg",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "data received rate",
    metric: "data_received",
    key: "rate",
    unit: "B/s",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "compressed response rate",
    metric: "edge_compressed_response_rate",
    key: "value",
    percentage: true,
    precision: 2,
    lower_is_better: false
  },
  {
    name: "small p95 latency",
    metric: "edge_small_req_duration",
    key: "p(95)",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "large p95 latency",
    metric: "edge_large_req_duration",
    key: "p(95)",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  },
  {
    name: "burst p95 latency",
    metric: "edge_burst_req_duration",
    key: "p(95)",
    unit: "ms",
    precision: 2,
    lower_is_better: true
  }
]

puts "# k6 Comparison"
puts
puts "| Metric | #{options[:baseline_label]} | #{options[:candidate_label]} | Delta |"
puts "| --- | ---: | ---: | ---: |"

rows.each do |row|
  baseline_value = metric_value(baseline, row.fetch(:metric), row.fetch(:key))
  candidate_value = metric_value(candidate, row.fetch(:metric), row.fetch(:key))

  cells = [
    row.fetch(:name),
    format_value(baseline_value, unit: row[:unit], precision: row.fetch(:precision, 2), percentage: row.fetch(:percentage, false)),
    format_value(candidate_value, unit: row[:unit], precision: row.fetch(:precision, 2), percentage: row.fetch(:percentage, false)),
    format_delta(
      baseline_value,
      candidate_value,
      percentage: row.fetch(:percentage, false),
      precision: row.fetch(:precision, 2),
      lower_is_better: row.fetch(:lower_is_better, true)
    )
  ]

  puts "| #{cells.join(' | ')} |"
end

puts
puts "Generated from:"
puts "- baseline: #{options[:baseline_path]}"
puts "- candidate: #{options[:candidate_path]}"
