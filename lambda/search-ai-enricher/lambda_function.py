import json
import logging
import os
import re
import urllib.error
import urllib.request
from datetime import datetime, timezone

import boto3


LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)

SEARCH_ENRICHMENT_TARGET = os.getenv("SEARCH_ENRICHMENT_TARGET", "search-service").strip().lower()
BEDROCK_REGION = os.getenv("BEDROCK_REGION", "ap-northeast-2")
BEDROCK_MODEL_ID = os.getenv("BEDROCK_MODEL_ID", "anthropic.claude-3-haiku-20240307-v1:0")
SEARCH_INTERNAL_BASE_URL = os.getenv("SEARCH_INTERNAL_BASE_URL", "").rstrip("/")
SEARCH_INTERNAL_AUTH_HEADER = os.getenv("SEARCH_INTERNAL_AUTH_HEADER", "X-Gateway-Auth")
SEARCH_INTERNAL_AUTH_TOKEN = os.getenv("SEARCH_INTERNAL_AUTH_TOKEN", "")
SEARCH_ELASTICSEARCH_URL = os.getenv("SEARCH_ELASTICSEARCH_URL", "").rstrip("/")
SEARCH_INDEX_NAME = os.getenv("SEARCH_INDEX_NAME", "items")
BEDROCK_MAX_TOKENS = int(os.getenv("BEDROCK_MAX_TOKENS", "400"))
BEDROCK_TEMPERATURE = float(os.getenv("BEDROCK_TEMPERATURE", "0.2"))
AI_TAG_LIMIT = int(os.getenv("AI_TAG_LIMIT", "10"))
AI_KEYWORD_LIMIT = int(os.getenv("AI_KEYWORD_LIMIT", "15"))
AI_SUMMARY_MAX_CHARS = int(os.getenv("AI_SUMMARY_MAX_CHARS", "160"))

bedrock_runtime = boto3.client("bedrock-runtime", region_name=BEDROCK_REGION)


def handler(event, context):
    failures = []
    for record in event.get("Records", []):
        message_id = record.get("messageId", "unknown")
        try:
            task = json.loads(record.get("body") or "{}")
            process_task(task)
        except Exception as exc:  # noqa: BLE001
            LOGGER.exception("search ai enrichment failed. messageId=%s", message_id)
            failures.append({"itemIdentifier": message_id})

    return {"batchItemFailures": failures}


def process_task(task):
    item_id = task.get("itemId")
    source_hash = trim_to_none(task.get("sourceHash"))
    if not item_id or not source_hash:
        LOGGER.info("skip ai enrichment. invalid task payload. itemId=%s", item_id)
        return

    prompt = build_prompt(task)
    raw_result = invoke_bedrock(prompt)
    parsed = parse_model_output(raw_result)
    payload = {
        "sourceHash": source_hash,
        "model": BEDROCK_MODEL_ID,
        "status": "READY",
        "aiTags": normalize_values(parsed.get("aiTags"), AI_TAG_LIMIT),
        "aiKeywords": normalize_values(parsed.get("aiKeywords"), AI_KEYWORD_LIMIT),
        "aiSummary": trim_summary(parsed.get("aiSummary")),
    }
    patch_target(item_id, payload)
    LOGGER.info("search ai enrichment patched. itemId=%s", item_id)


def build_prompt(task):
    source = {
        "title": task.get("title"),
        "description": task.get("description"),
        "category": task.get("category"),
        "categoryPath": task.get("categoryPath") or [],
        "tags": task.get("tags") or [],
        "features": task.get("features") or [],
        "detailTitles": task.get("detailTitles") or [],
        "detailDescriptions": task.get("detailDescriptions") or [],
        "detailHighlights": task.get("detailHighlights") or [],
    }

    return (
        "You are generating search enrichment metadata for an ecommerce item.\n"
        "Return strict JSON only with keys aiTags, aiKeywords, aiSummary.\n"
        f"aiTags must contain at most {AI_TAG_LIMIT} short strings.\n"
        f"aiKeywords must contain at most {AI_KEYWORD_LIMIT} short strings.\n"
        f"aiSummary must be a single concise Korean sentence under {AI_SUMMARY_MAX_CHARS} characters.\n"
        "Do not invent facts that are not grounded in the input.\n"
        "Prefer shopper intent terms and usage context that help search.\n\n"
        f"INPUT:\n{json.dumps(source, ensure_ascii=False)}"
    )


def invoke_bedrock(prompt):
    request_body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": BEDROCK_MAX_TOKENS,
        "temperature": BEDROCK_TEMPERATURE,
        "messages": [
            {
                "role": "user",
                "content": [{"type": "text", "text": prompt}],
            }
        ],
    }
    response = bedrock_runtime.invoke_model(
        modelId=BEDROCK_MODEL_ID,
        contentType="application/json",
        accept="application/json",
        body=json.dumps(request_body),
    )
    payload = json.loads(response["body"].read())
    content = payload.get("content") or []
    if not content:
        raise RuntimeError("bedrock response content missing")
    return content[0].get("text", "")


def parse_model_output(raw_text):
    if not raw_text:
        return {"aiTags": [], "aiKeywords": [], "aiSummary": None}

    raw_text = raw_text.strip()
    fenced_match = re.search(r"```(?:json)?\s*(\{.*\})\s*```", raw_text, re.DOTALL)
    if fenced_match:
        raw_text = fenced_match.group(1)

    start = raw_text.find("{")
    end = raw_text.rfind("}")
    if start >= 0 and end > start:
        raw_text = raw_text[start : end + 1]

    parsed = json.loads(raw_text)
    return {
        "aiTags": parsed.get("aiTags") or [],
        "aiKeywords": parsed.get("aiKeywords") or [],
        "aiSummary": parsed.get("aiSummary"),
    }


def patch_target(item_id, payload):
    if SEARCH_ENRICHMENT_TARGET == "elasticsearch":
        patch_elasticsearch_document(item_id, payload)
        return
    patch_search_document(item_id, payload)


def patch_search_document(item_id, payload):
    if not SEARCH_INTERNAL_BASE_URL or not SEARCH_INTERNAL_AUTH_TOKEN:
        raise RuntimeError("search internal endpoint configuration missing")

    request = urllib.request.Request(
        url=f"{SEARCH_INTERNAL_BASE_URL}/internal/v1/search/items/{item_id}/enrichment",
        method="PUT",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            SEARCH_INTERNAL_AUTH_HEADER: SEARCH_INTERNAL_AUTH_TOKEN,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            if response.status // 100 != 2:
                raise RuntimeError(f"search enrichment patch failed. status={response.status}")
    except urllib.error.HTTPError as exc:
        response_body = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"search enrichment patch failed. status={exc.code}, body={response_body}") from exc


def patch_elasticsearch_document(item_id, payload):
    if not SEARCH_ELASTICSEARCH_URL:
        raise RuntimeError("search elasticsearch url missing")

    request_payload = {
        "doc": {
            "aiTags": payload.get("aiTags") or [],
            "aiKeywords": payload.get("aiKeywords") or [],
            "aiSummary": payload.get("aiSummary"),
            "aiSourceHash": payload.get("sourceHash"),
            "aiModel": payload.get("model"),
            "aiStatus": payload.get("status"),
            "aiEnrichedAt": current_timestamp_utc(),
        }
    }
    request = urllib.request.Request(
        url=f"{SEARCH_ELASTICSEARCH_URL}/{SEARCH_INDEX_NAME}/_update/{item_id}",
        method="POST",
        data=json.dumps(request_payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            if response.status // 100 != 2:
                raise RuntimeError(f"elasticsearch enrichment update failed. status={response.status}")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            LOGGER.info("skip ai enrichment patch. search document missing. itemId=%s", item_id)
            return
        response_body = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"elasticsearch enrichment update failed. status={exc.code}, body={response_body}") from exc


def normalize_values(values, limit):
    if not isinstance(values, list):
        return []

    normalized = []
    seen = set()
    for value in values:
        text = trim_to_none(value)
        if not text:
            continue
        key = text.lower()
        if key in seen:
            continue
        seen.add(key)
        normalized.append(text)
        if len(normalized) >= limit:
            break
    return normalized


def trim_summary(value):
    text = trim_to_none(value)
    if not text:
        return None
    return text[:AI_SUMMARY_MAX_CHARS]


def trim_to_none(value):
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def current_timestamp_utc():
    return datetime.now(timezone.utc).isoformat()
