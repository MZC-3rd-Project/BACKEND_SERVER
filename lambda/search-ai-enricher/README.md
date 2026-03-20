# Search AI Enricher Lambda

SQS 트리거로 상품 검색 원문을 받아 Bedrock으로 `aiTags`, `aiKeywords`, `aiSummary`를 생성한 뒤,
`search-service` 내부 enrichment API로 patch 하는 Python Lambda입니다.

## Input

SQS message body JSON:

```json
{
  "itemId": 293209250560360448,
  "triggerType": "ITEM_UPDATED",
  "sourceHash": "sha256-...",
  "title": "구장 관련 상품 목록",
  "description": "응원용 굿즈와 캘린더 소개",
  "category": "굿즈",
  "categoryPath": ["스포츠", "구장 굿즈"],
  "tags": ["구장", "응원"],
  "features": ["데스크", "한정판"],
  "detailTitles": ["상세 제목"],
  "detailDescriptions": ["상세 설명"],
  "detailHighlights": ["상세 하이라이트"],
  "requestedAt": "2026-03-20T05:10:00Z"
}
```

## Required environment variables

- `BEDROCK_REGION`
- `BEDROCK_MODEL_ID`
- `SEARCH_INTERNAL_BASE_URL`
- `SEARCH_INTERNAL_AUTH_TOKEN`

## Optional environment variables

- `SEARCH_INTERNAL_AUTH_HEADER` default: `X-Gateway-Auth`
- `BEDROCK_MAX_TOKENS` default: `400`
- `BEDROCK_TEMPERATURE` default: `0.2`
- `AI_TAG_LIMIT` default: `10`
- `AI_KEYWORD_LIMIT` default: `15`
- `AI_SUMMARY_MAX_CHARS` default: `160`

## Handler

- `lambda_function.handler`
