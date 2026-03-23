import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

function envString(name, fallback = '') {
    const value = __ENV[name];
    if (value === undefined || value === null || value === '') {
        return fallback;
    }
    return value;
}

function envInt(name, fallback) {
    const raw = envString(name);
    if (raw === '') {
        return fallback;
    }

    const parsed = parseInt(raw, 10);
    if (Number.isNaN(parsed)) {
        throw new Error(`${name} must be an integer, received: ${raw}`);
    }
    return parsed;
}

function envBool(name, fallback = false) {
    const raw = envString(name);
    if (raw === '') {
        return fallback;
    }
    return ['1', 'true', 'yes', 'on'].includes(raw.toLowerCase());
}

function parseCsv(raw) {
    return raw
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function parseStages(raw) {
    return parseCsv(raw).map((entry) => {
        const [duration, target] = entry.split(':').map((item) => item.trim());
        if (!duration || !target) {
            throw new Error(`BURST_STAGES entry must be duration:target, received: ${entry}`);
        }

        const parsedTarget = parseInt(target, 10);
        if (Number.isNaN(parsedTarget)) {
            throw new Error(`BURST_STAGES target must be an integer, received: ${entry}`);
        }

        return {
            duration,
            target: parsedTarget,
        };
    });
}

function parseJsonHeaders(raw) {
    if (!raw) {
        return {};
    }

    try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            throw new Error('must be a JSON object');
        }
        return parsed;
    } catch (error) {
        throw new Error(`EXTRA_HEADERS_JSON must be a JSON object: ${error.message}`);
    }
}

function normalizeBaseUrl(raw) {
    if (!raw) {
        throw new Error('BASE_URL is required');
    }
    return raw.replace(/\/+$/, '');
}

function headerValue(response, name) {
    const target = name.toLowerCase();
    for (const [key, value] of Object.entries(response.headers || {})) {
        if (key.toLowerCase() === target) {
            return Array.isArray(value) ? value[0] : value;
        }
    }
    return '';
}

function pathFor(paths) {
    const iteration = exec.scenario.iterationInTest;
    return paths[iteration % paths.length];
}

const baseUrl = normalizeBaseUrl(envString('BASE_URL'));
const selectedScenarioNames = new Set(parseCsv(envString('SCENARIOS', 'small,large,burst')));
const smallPaths = parseCsv(envString('SMALL_PATHS', '/actuator/health'));
const largePaths = parseCsv(envString('LARGE_PATHS', smallPaths.join(',')));
const requestPauseSeconds = envInt('REQUEST_PAUSE_MS', 250) / 1000;
const timeout = envString('HTTP_TIMEOUT', '30s');
const acceptEncoding = envString('ACCEPT_ENCODING', 'gzip');
const extraHeaders = parseJsonHeaders(envString('EXTRA_HEADERS_JSON'));
const authHeaderName = envString('AUTH_HEADER_NAME');
const authHeaderValue = envString('AUTH_HEADER_VALUE');
const bearerToken = envString('BEARER_TOKEN');

const requestSuccessRate = new Rate('edge_http_success_rate');
const status5xxRate = new Rate('edge_status_5xx_rate');
const compressedResponseRate = new Rate('edge_compressed_response_rate');
const responseBodyBytes = new Trend('edge_response_body_bytes');
const contentLengthBytes = new Trend('edge_content_length_bytes');
const requestCounter = new Counter('edge_request_count');
const smallDuration = new Trend('edge_small_req_duration');
const largeDuration = new Trend('edge_large_req_duration');
const burstDuration = new Trend('edge_burst_req_duration');

function buildScenarios() {
    const scenarios = {};

    if (selectedScenarioNames.has('small')) {
        scenarios.small = {
            executor: 'constant-arrival-rate',
            rate: envInt('SMALL_RATE', 40),
            timeUnit: '1s',
            duration: envString('SMALL_DURATION', '2m'),
            preAllocatedVUs: envInt('SMALL_PRE_ALLOCATED_VUS', 20),
            maxVUs: envInt('SMALL_MAX_VUS', 80),
            tags: {
                traffic_profile: 'small',
            },
        };
    }

    if (selectedScenarioNames.has('large')) {
        scenarios.large = {
            executor: 'constant-arrival-rate',
            rate: envInt('LARGE_RATE', 12),
            timeUnit: '1s',
            duration: envString('LARGE_DURATION', '2m'),
            preAllocatedVUs: envInt('LARGE_PRE_ALLOCATED_VUS', 10),
            maxVUs: envInt('LARGE_MAX_VUS', 40),
            tags: {
                traffic_profile: 'large',
            },
        };
    }

    if (selectedScenarioNames.has('burst')) {
        scenarios.burst = {
            executor: 'ramping-arrival-rate',
            startRate: envInt('BURST_START_RATE', 15),
            timeUnit: '1s',
            preAllocatedVUs: envInt('BURST_PRE_ALLOCATED_VUS', 20),
            maxVUs: envInt('BURST_MAX_VUS', 120),
            stages: parseStages(envString('BURST_STAGES', '30s:15,1m:50,30s:80,30s:15')),
            tags: {
                traffic_profile: 'burst',
            },
        };
    }

    if (Object.keys(scenarios).length === 0) {
        throw new Error('SCENARIOS must include at least one of: small, large, burst');
    }

    return scenarios;
}

export const options = {
    insecureSkipTLSVerify: envBool('INSECURE_SKIP_TLS_VERIFY', false),
    noConnectionReuse: envBool('NO_CONNECTION_REUSE', false),
    noVUConnectionReuse: envBool('NO_VU_CONNECTION_REUSE', false),
    summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
    scenarios: buildScenarios(),
    thresholds: {
        http_req_failed: ['rate<0.05'],
        edge_status_5xx_rate: ['rate<0.01'],
        edge_http_success_rate: ['rate>0.95'],
    },
};

function requestHeaders() {
    const headers = {
        Accept: 'application/json',
        ...extraHeaders,
    };

    if (acceptEncoding) {
        headers['Accept-Encoding'] = acceptEncoding;
    }

    if (bearerToken) {
        headers.Authorization = `Bearer ${bearerToken}`;
    }

    if (authHeaderName && authHeaderValue) {
        headers[authHeaderName] = authHeaderValue;
    }

    return headers;
}

function recordMetrics(response, groupName) {
    const tags = {
        traffic_profile: groupName,
    };

    const ok = check(response, {
        'status is below 500': (res) => res.status < 500,
        'status is not unauthorized': (res) => res.status !== 401 && res.status !== 403,
    });

    requestCounter.add(1, tags);
    requestSuccessRate.add(ok, tags);
    status5xxRate.add(response.status >= 500, tags);

    const contentEncoding = String(headerValue(response, 'content-encoding') || '').toLowerCase();
    const compressed = /gzip|br|deflate/.test(contentEncoding);
    compressedResponseRate.add(compressed, tags);

    const contentLength = parseInt(headerValue(response, 'content-length') || '0', 10);
    if (!Number.isNaN(contentLength) && contentLength > 0) {
        contentLengthBytes.add(contentLength, tags);
    }

    const bodySize = response.body ? response.body.length : 0;
    responseBodyBytes.add(bodySize, tags);

    if (groupName === 'small') {
        smallDuration.add(response.timings.duration, tags);
    } else if (groupName === 'large') {
        largeDuration.add(response.timings.duration, tags);
    } else {
        burstDuration.add(response.timings.duration, tags);
    }
}

function sendRequest(groupName, paths) {
    const path = pathFor(paths);
    const response = http.get(`${baseUrl}${path}`, {
        headers: requestHeaders(),
        timeout,
        tags: {
            traffic_profile: groupName,
            endpoint: path,
        },
    });

    recordMetrics(response, groupName);

    if (requestPauseSeconds > 0 && groupName !== 'burst') {
        sleep(requestPauseSeconds);
    }
}

export default function () {
    switch (exec.scenario.name) {
        case 'small':
            sendRequest('small', smallPaths);
            break;
        case 'large':
            sendRequest('large', largePaths);
            break;
        case 'burst':
            sendRequest('burst', smallPaths);
            break;
        default:
            throw new Error(`Unsupported scenario: ${exec.scenario.name}`);
    }
}

function metricValue(data, metricName, key) {
    const metric = data.metrics[metricName];
    if (!metric || !metric.values) {
        return null;
    }
    return metric.values[key] ?? null;
}

function formatNumber(value, digits = 2, suffix = '') {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return 'n/a';
    }
    return `${Number(value).toFixed(digits)}${suffix}`;
}

export function handleSummary(data) {
    const httpReqFailedRate = metricValue(data, 'http_req_failed', 'rate');
    const httpReqDurationP95 = metricValue(data, 'http_req_duration', 'p(95)');
    const httpReqDurationP99 = metricValue(data, 'http_req_duration', 'p(99)');
    const httpReqConnectingAvg = metricValue(data, 'http_req_connecting', 'avg');
    const dataReceivedRate = metricValue(data, 'data_received', 'rate');
    const compressedRate = metricValue(data, 'edge_compressed_response_rate', 'rate');
    const smallReqDurationP95 = metricValue(data, 'edge_small_req_duration', 'p(95)');
    const largeReqDurationP95 = metricValue(data, 'edge_large_req_duration', 'p(95)');
    const burstReqDurationP95 = metricValue(data, 'edge_burst_req_duration', 'p(95)');

    const lines = [
        '=== Nginx PR Benchmark Summary ===',
        `scenarios: ${Array.from(selectedScenarioNames).join(', ')}`,
        `base_url: ${baseUrl}`,
        `http_req_failed: ${formatNumber(httpReqFailedRate === null ? null : httpReqFailedRate * 100, 2, '%')}`,
        `http_req_duration p95: ${formatNumber(httpReqDurationP95, 2, 'ms')}`,
        `http_req_duration p99: ${formatNumber(httpReqDurationP99, 2, 'ms')}`,
        `http_req_connecting avg: ${formatNumber(httpReqConnectingAvg, 2, 'ms')}`,
        `data_received rate: ${formatNumber(dataReceivedRate, 2, ' B/s')}`,
        `compressed_response_rate: ${formatNumber(compressedRate === null ? null : compressedRate * 100, 2, '%')}`,
        `small_req_duration p95: ${formatNumber(smallReqDurationP95, 2, 'ms')}`,
        `large_req_duration p95: ${formatNumber(largeReqDurationP95, 2, 'ms')}`,
        `burst_req_duration p95: ${formatNumber(burstReqDurationP95, 2, 'ms')}`,
    ];

    return {
        stdout: `${lines.join('\n')}\n`,
    };
}
