# CloudFront OAC + S3 Private Policy

## 목표
- S3 버킷은 private로 유지한다.
- 조회는 CloudFront 경로만 허용한다.
- 직접 S3 URL 접근은 차단한다.

## 구성 기준

### 1) S3 퍼블릭 접근 차단
- S3 Bucket `Block Public Access` 4개 옵션 모두 `ON`
- Bucket ACL 사용 안 함(Object Ownership: Bucket owner enforced 권장)

### 2) CloudFront Origin Access Control(OAC)
- Origin: `team2-donmoa-media-raw` S3 bucket
- Origin Access: OAC 사용
- Signing behavior: `Sign requests (always)`

### 3) S3 Bucket Policy 예시
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipalReadOnly",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::team2-donmoa-media-raw/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::<ACCOUNT_ID>:distribution/<DISTRIBUTION_ID>"
        }
      }
    }
  ]
}
```

## 운영 검증 시나리오

### 시나리오 A: 직접 S3 URL 차단 확인
1. `https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/<key>` 호출
2. `403 AccessDenied` 확인

### 시나리오 B: CloudFront URL 허용 확인
1. `https://<distribution-domain>/<key>` 호출
2. `200` + 정상 컨텐츠 확인

### 시나리오 C: OAC 우회 차단 확인
1. 임의 IAM principal로 `GetObject` 호출
2. CloudFront `SourceArn` 조건 미충족으로 실패 확인
