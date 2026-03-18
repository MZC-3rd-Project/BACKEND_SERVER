#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   AWS_PROFILE=mzc AWS_REGION=ap-northeast-2 ./scripts/setup-dev-cicd.sh

AWS_PROFILE="${AWS_PROFILE:-mzc}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ACCOUNT_ID="${ACCOUNT_ID:-682405977339}"

CONNECTION_ID="${CONNECTION_ID:-6cdbf218-483a-4e49-9c74-dd6fe84dbd2b}"
REPO_FULL_NAME="${REPO_FULL_NAME:-MZC-3rd-Project/BACKEND_SERVER}"
BRANCH_NAME="${BRANCH_NAME:-develop}"

# Reuse existing bucket by default (avoid bucket-create permission/network issues).
ARTIFACT_BUCKET="${ARTIFACT_BUCKET:-donmoa-terraform-state}"
PIPELINE_NAME="${PIPELINE_NAME:-donmoa-dev-eks-deploy}"
CODEBUILD_BUILD_PROJECT_NAME="${CODEBUILD_BUILD_PROJECT_NAME:-donmoa-dev-eks-build}"
CODEBUILD_DEPLOY_PROJECT_NAME="${CODEBUILD_DEPLOY_PROJECT_NAME:-donmoa-dev-eks-helm-deploy}"
EKS_CLUSTER_NAME="${EKS_CLUSTER_NAME:-donmoa-dev-eks}"
INSTALL_EKS_PLATFORM_ADDONS="${INSTALL_EKS_PLATFORM_ADDONS:-false}"
START_PIPELINE_EXECUTION="${START_PIPELINE_EXECUTION:-true}"

GITHUB_ROLE_NAME="${GITHUB_ROLE_NAME:-donmoa-dev-github-actions-deploy-role}"
CODEPIPELINE_ROLE_NAME="${CODEPIPELINE_ROLE_NAME:-donmoa-dev-codepipeline-role}"
CODEBUILD_ROLE_NAME="${CODEBUILD_ROLE_NAME:-donmoa-dev-codebuild-role}"

CONNECTION_ARN="arn:aws:codeconnections:${AWS_REGION}:${ACCOUNT_ID}:connection/${CONNECTION_ID}"
TMP_DIR="/tmp/donmoa-dev-cicd"
mkdir -p "${TMP_DIR}"

aws_cmd() {
  AWS_PROFILE="${AWS_PROFILE}" AWS_REGION="${AWS_REGION}" \
  aws --cli-connect-timeout 5 --cli-read-timeout 15 "$@"
}

retry() {
  local attempts="$1"
  shift
  local n=1
  until "$@"; do
    if [ "$n" -ge "$attempts" ]; then
      echo "[ERROR] failed after ${attempts} attempts: $*" >&2
      return 1
    fi
    local sleep_sec=$((n * 2))
    echo "[WARN] retry ${n}/${attempts} in ${sleep_sec}s: $*" >&2
    sleep "${sleep_sec}"
    n=$((n + 1))
  done
}

retry_out() {
  local attempts="$1"
  shift
  local n=1
  local out
  while true; do
    if out=$("$@" 2>/tmp/retry_out_err.log); then
      printf '%s' "${out}"
      return 0
    fi
    if [ "$n" -ge "$attempts" ]; then
      echo "[ERROR] failed after ${attempts} attempts: $*" >&2
      cat /tmp/retry_out_err.log >&2 || true
      return 1
    fi
    local sleep_sec=$((n * 2))
    echo "[WARN] retry ${n}/${attempts} in ${sleep_sec}s: $*" >&2
    sleep "${sleep_sec}"
    n=$((n + 1))
  done
}

echo "[INFO] writing IAM policy files"
cat > "${TMP_DIR}/github-oidc-trust.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Federated":"arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"},"Action":"sts:AssumeRoleWithWebIdentity","Condition":{"StringEquals":{"token.actions.githubusercontent.com:aud":"sts.amazonaws.com"},"StringLike":{"token.actions.githubusercontent.com:sub":["repo:${REPO_FULL_NAME}:ref:refs/heads/${BRANCH_NAME}"]}}}]}
EOF

cat > "${TMP_DIR}/github-role-policy.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"CodePipelineStartAndReadDev","Effect":"Allow","Action":["codepipeline:StartPipelineExecution","codepipeline:GetPipeline","codepipeline:GetPipelineExecution","codepipeline:GetPipelineState","codepipeline:ListPipelineExecutions"],"Resource":"arn:aws:codepipeline:${AWS_REGION}:${ACCOUNT_ID}:donmoa-dev-*"}]}
EOF

cat > "${TMP_DIR}/codepipeline-trust.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"codepipeline.amazonaws.com"},"Action":"sts:AssumeRole"}]}
EOF

cat > "${TMP_DIR}/codepipeline-policy.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"UseCodeConnection","Effect":"Allow","Action":["codestar-connections:UseConnection","codeconnections:UseConnection"],"Resource":"${CONNECTION_ARN}"},{"Sid":"ArtifactBucketAccess","Effect":"Allow","Action":["s3:GetObject","s3:GetObjectVersion","s3:GetBucketVersioning","s3:PutObject","s3:PutObjectAcl"],"Resource":["arn:aws:s3:::${ARTIFACT_BUCKET}","arn:aws:s3:::${ARTIFACT_BUCKET}/*"]},{"Sid":"CodeBuildInvoke","Effect":"Allow","Action":["codebuild:BatchGetBuilds","codebuild:StartBuild"],"Resource":["arn:aws:codebuild:${AWS_REGION}:${ACCOUNT_ID}:project/${CODEBUILD_BUILD_PROJECT_NAME}","arn:aws:codebuild:${AWS_REGION}:${ACCOUNT_ID}:project/${CODEBUILD_DEPLOY_PROJECT_NAME}"]}]}
EOF

cat > "${TMP_DIR}/codebuild-trust.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"codebuild.amazonaws.com"},"Action":"sts:AssumeRole"}]}
EOF

cat > "${TMP_DIR}/codebuild-policy.json" <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"CloudWatchLogs","Effect":"Allow","Action":["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],"Resource":"*"},{"Sid":"ArtifactBucketAccess","Effect":"Allow","Action":["s3:GetObject","s3:GetObjectVersion","s3:PutObject"],"Resource":["arn:aws:s3:::${ARTIFACT_BUCKET}","arn:aws:s3:::${ARTIFACT_BUCKET}/*"]},{"Sid":"EcrPushPull","Effect":"Allow","Action":["ecr:GetAuthorizationToken","ecr:BatchCheckLayerAvailability","ecr:CompleteLayerUpload","ecr:InitiateLayerUpload","ecr:UploadLayerPart","ecr:PutImage","ecr:BatchGetImage","ecr:GetDownloadUrlForLayer"],"Resource":"*"},{"Sid":"EksDescribe","Effect":"Allow","Action":["eks:DescribeCluster"],"Resource":"arn:aws:eks:${AWS_REGION}:${ACCOUNT_ID}:cluster/${EKS_CLUSTER_NAME}"},{"Sid":"StsRead","Effect":"Allow","Action":["sts:GetCallerIdentity"],"Resource":"*"}]}
EOF

echo "[INFO] ensuring IAM roles"
if retry 6 aws_cmd iam get-role --role-name "${GITHUB_ROLE_NAME}" >/dev/null 2>&1; then
  retry 6 aws_cmd iam update-assume-role-policy --role-name "${GITHUB_ROLE_NAME}" --policy-document "file://${TMP_DIR}/github-oidc-trust.json" >/dev/null
else
  retry 6 aws_cmd iam create-role --role-name "${GITHUB_ROLE_NAME}" --assume-role-policy-document "file://${TMP_DIR}/github-oidc-trust.json" >/dev/null
fi
retry 6 aws_cmd iam put-role-policy --role-name "${GITHUB_ROLE_NAME}" --policy-name "donmoa-dev-github-actions-deploy-inline" --policy-document "file://${TMP_DIR}/github-role-policy.json" >/dev/null

if retry 6 aws_cmd iam get-role --role-name "${CODEPIPELINE_ROLE_NAME}" >/dev/null 2>&1; then
  retry 6 aws_cmd iam update-assume-role-policy --role-name "${CODEPIPELINE_ROLE_NAME}" --policy-document "file://${TMP_DIR}/codepipeline-trust.json" >/dev/null
else
  retry 6 aws_cmd iam create-role --role-name "${CODEPIPELINE_ROLE_NAME}" --assume-role-policy-document "file://${TMP_DIR}/codepipeline-trust.json" >/dev/null
fi
retry 6 aws_cmd iam put-role-policy --role-name "${CODEPIPELINE_ROLE_NAME}" --policy-name "donmoa-dev-codepipeline-inline" --policy-document "file://${TMP_DIR}/codepipeline-policy.json" >/dev/null

if retry 6 aws_cmd iam get-role --role-name "${CODEBUILD_ROLE_NAME}" >/dev/null 2>&1; then
  retry 6 aws_cmd iam update-assume-role-policy --role-name "${CODEBUILD_ROLE_NAME}" --policy-document "file://${TMP_DIR}/codebuild-trust.json" >/dev/null
else
  retry 6 aws_cmd iam create-role --role-name "${CODEBUILD_ROLE_NAME}" --assume-role-policy-document "file://${TMP_DIR}/codebuild-trust.json" >/dev/null
fi
retry 6 aws_cmd iam put-role-policy --role-name "${CODEBUILD_ROLE_NAME}" --policy-name "donmoa-dev-codebuild-inline" --policy-document "file://${TMP_DIR}/codebuild-policy.json" >/dev/null

CODEBUILD_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${CODEBUILD_ROLE_NAME}"
CODEPIPELINE_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${CODEPIPELINE_ROLE_NAME}"
GITHUB_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${GITHUB_ROLE_NAME}"

echo "[INFO] ensuring EKS CodeBuild access in aws-auth"
if ! kubectl get configmap aws-auth -n kube-system >/dev/null 2>&1; then
  aws_cmd eks update-kubeconfig --name "${EKS_CLUSTER_NAME}" --region "${AWS_REGION}" >/dev/null
fi
CODEBUILD_ROLE_ARN="${CODEBUILD_ROLE_ARN}" ruby - <<'RUBY' > "${TMP_DIR}/aws-auth-patch.yaml"
require "yaml"

role_arn = ENV.fetch("CODEBUILD_ROLE_ARN")
username = "codebuild:{{SessionName}}"
groups = ["system:masters"]

data = YAML.load(`kubectl get configmap aws-auth -n kube-system -o yaml`)
map_roles_text = data.dig("data", "mapRoles").to_s
map_roles = map_roles_text.strip.empty? ? [] : (YAML.load(map_roles_text) || [])

unless map_roles.any? { |item| item["rolearn"] == role_arn }
  map_roles << {
    "rolearn" => role_arn,
    "username" => username,
    "groups" => groups
  }
end

data["data"] ||= {}
data["data"]["mapRoles"] = map_roles.to_yaml(line_width: -1).sub(/\A---\s*\n/, "").rstrip
puts YAML.dump(data)
RUBY
kubectl apply -f "${TMP_DIR}/aws-auth-patch.yaml" >/dev/null

echo "[INFO] ensuring EKS CodeBuild projects"
cat > "${TMP_DIR}/codebuild-build-project.json" <<EOF
{"name":"${CODEBUILD_BUILD_PROJECT_NAME}","description":"donmoa dev EKS image build","source":{"type":"CODEPIPELINE","buildspec":"buildspecs/dev-eks-build.yml"},"artifacts":{"type":"CODEPIPELINE"},"environment":{"type":"LINUX_CONTAINER","image":"aws/codebuild/standard:7.0","computeType":"BUILD_GENERAL1_LARGE","privilegedMode":true,"environmentVariables":[{"name":"AWS_DEFAULT_REGION","value":"${AWS_REGION}","type":"PLAINTEXT"}]},"serviceRole":"${CODEBUILD_ROLE_ARN}","timeoutInMinutes":120,"queuedTimeoutInMinutes":60,"cache":{"type":"LOCAL","modes":["LOCAL_DOCKER_LAYER_CACHE","LOCAL_SOURCE_CACHE"]},"logsConfig":{"cloudWatchLogs":{"status":"ENABLED","groupName":"/aws/codebuild/${CODEBUILD_BUILD_PROJECT_NAME}","streamName":"build-log"}},"tags":[{"key":"Project","value":"donmoa"},{"key":"Environment","value":"dev"},{"key":"ManagedBy","value":"manual-cli"}]}
EOF
cat > "${TMP_DIR}/codebuild-deploy-project.json" <<EOF
{"name":"${CODEBUILD_DEPLOY_PROJECT_NAME}","description":"donmoa dev EKS helm deploy","source":{"type":"CODEPIPELINE","buildspec":"buildspecs/dev-eks-deploy.yml"},"artifacts":{"type":"CODEPIPELINE"},"environment":{"type":"LINUX_CONTAINER","image":"aws/codebuild/standard:7.0","computeType":"BUILD_GENERAL1_MEDIUM","privilegedMode":false,"environmentVariables":[{"name":"AWS_DEFAULT_REGION","value":"${AWS_REGION}","type":"PLAINTEXT"},{"name":"EKS_CLUSTER_NAME","value":"${EKS_CLUSTER_NAME}","type":"PLAINTEXT"},{"name":"INSTALL_EKS_PLATFORM_ADDONS","value":"${INSTALL_EKS_PLATFORM_ADDONS}","type":"PLAINTEXT"}]},"serviceRole":"${CODEBUILD_ROLE_ARN}","timeoutInMinutes":120,"queuedTimeoutInMinutes":60,"logsConfig":{"cloudWatchLogs":{"status":"ENABLED","groupName":"/aws/codebuild/${CODEBUILD_DEPLOY_PROJECT_NAME}","streamName":"deploy-log"}},"tags":[{"key":"Project","value":"donmoa"},{"key":"Environment","value":"dev"},{"key":"ManagedBy","value":"manual-cli"}]}
EOF
build_project_count="$(retry_out 6 aws_cmd codebuild batch-get-projects --names "${CODEBUILD_BUILD_PROJECT_NAME}" --query 'projects | length(@)' --output text)"
if [ "${build_project_count}" = "0" ]; then
  retry 6 aws_cmd codebuild create-project --cli-input-json "file://${TMP_DIR}/codebuild-build-project.json" >/dev/null
else
  retry 6 aws_cmd codebuild update-project --cli-input-json "file://${TMP_DIR}/codebuild-build-project.json" >/dev/null
fi
deploy_project_count="$(retry_out 6 aws_cmd codebuild batch-get-projects --names "${CODEBUILD_DEPLOY_PROJECT_NAME}" --query 'projects | length(@)' --output text)"
if [ "${deploy_project_count}" = "0" ]; then
  retry 6 aws_cmd codebuild create-project --cli-input-json "file://${TMP_DIR}/codebuild-deploy-project.json" >/dev/null
else
  retry 6 aws_cmd codebuild update-project --cli-input-json "file://${TMP_DIR}/codebuild-deploy-project.json" >/dev/null
fi

echo "[INFO] ensuring CodePipeline"
cat > "${TMP_DIR}/pipeline.json" <<EOF
{
  "pipeline": {
    "name": "${PIPELINE_NAME}",
    "roleArn": "${CODEPIPELINE_ROLE_ARN}",
    "artifactStore": {
      "type": "S3",
      "location": "${ARTIFACT_BUCKET}"
    },
    "variables": [
      {
        "name": "deployTargets",
        "defaultValue": "__FULL__",
        "description": "Comma-separated service keys for selective deploy"
      },
      {
        "name": "deployMode",
        "defaultValue": "build-and-deploy",
        "description": "build-and-deploy or deploy-only"
      }
    ],
    "stages": [
      {
        "name": "Source",
        "actions": [
          {
            "name": "SourceFromGitHub",
            "actionTypeId": {
              "category": "Source",
              "owner": "AWS",
              "provider": "CodeStarSourceConnection",
              "version": "1"
            },
            "runOrder": 1,
            "configuration": {
              "ConnectionArn": "${CONNECTION_ARN}",
              "FullRepositoryId": "${REPO_FULL_NAME}",
              "BranchName": "${BRANCH_NAME}",
              "OutputArtifactFormat": "CODE_ZIP",
              "DetectChanges": "false"
            },
            "outputArtifacts": [
              {
                "name": "SourceArtifact"
              }
            ],
            "inputArtifacts": [],
            "region": "${AWS_REGION}"
          }
        ]
      },
      {
        "name": "Build",
        "actions": [
          {
            "name": "BuildImages",
            "actionTypeId": {
              "category": "Build",
              "owner": "AWS",
              "provider": "CodeBuild",
              "version": "1"
            },
            "runOrder": 1,
            "configuration": {
              "ProjectName": "${CODEBUILD_BUILD_PROJECT_NAME}",
              "EnvironmentVariables": "[{\"name\":\"DEPLOY_TARGETS\",\"value\":\"#{variables.deployTargets}\",\"type\":\"PLAINTEXT\"},{\"name\":\"DEPLOY_MODE\",\"value\":\"#{variables.deployMode}\",\"type\":\"PLAINTEXT\"}]"
            },
            "inputArtifacts": [
              {
                "name": "SourceArtifact"
              }
            ],
            "outputArtifacts": [
              {
                "name": "BuildArtifact"
              }
            ],
            "region": "${AWS_REGION}"
          }
        ]
      },
      {
        "name": "Deploy",
        "actions": [
          {
            "name": "DeployToEks",
            "actionTypeId": {
              "category": "Build",
              "owner": "AWS",
              "provider": "CodeBuild",
              "version": "1"
            },
            "runOrder": 1,
            "configuration": {
              "ProjectName": "${CODEBUILD_DEPLOY_PROJECT_NAME}"
            },
            "inputArtifacts": [
              {
                "name": "BuildArtifact"
              }
            ],
            "outputArtifacts": [],
            "region": "${AWS_REGION}"
          }
        ]
      }
    ],
    "version": 1,
    "executionMode": "QUEUED",
    "pipelineType": "V2"
  }
}
EOF
if retry 6 aws_cmd codepipeline get-pipeline --name "${PIPELINE_NAME}" >/dev/null 2>&1; then
  retry 6 aws_cmd codepipeline update-pipeline --cli-input-json "file://${TMP_DIR}/pipeline.json" >/dev/null
else
  retry 6 aws_cmd codepipeline create-pipeline --cli-input-json "file://${TMP_DIR}/pipeline.json" >/dev/null
fi

echo "[INFO] applying GitHub actions vars/secrets"
gh variable set AWS_REGION --body "${AWS_REGION}"
gh variable set AWS_CODEPIPELINE_DEV_NAME --body "${PIPELINE_NAME}"
gh variable set AWS_CODEPIPELINE_PROD_NAME --body "disabled"
gh variable set AWS_PROD_CD_ENABLED --body "false"
gh variable set AWS_CODEPIPELINE_DEV_SERVICE_MAP --body "$(cat deploy/catalog/github-actions-dev-service-map.example.json)"
gh secret set AWS_DEPLOY_ROLE_ARN --body "${GITHUB_ROLE_ARN}"

EXEC_ID=""
if [ "${START_PIPELINE_EXECUTION}" = "true" ]; then
  echo "[INFO] starting first pipeline execution"
  EXEC_ID="$(retry_out 6 aws_cmd codepipeline start-pipeline-execution --name "${PIPELINE_NAME}" --query pipelineExecutionId --output text)"
else
  echo "[INFO] skipping initial pipeline execution"
fi

echo "===== DEV CI/CD SETUP COMPLETE ====="
echo "pipeline=${PIPELINE_NAME}"
echo "execution=${EXEC_ID}"
echo "codebuild_build=${CODEBUILD_BUILD_PROJECT_NAME}"
echo "codebuild_deploy=${CODEBUILD_DEPLOY_PROJECT_NAME}"
echo "role=${GITHUB_ROLE_ARN}"
if [ -n "${EXEC_ID}" ]; then
  echo "console=https://${AWS_REGION}.console.aws.amazon.com/codesuite/codepipeline/pipelines/${PIPELINE_NAME}/executions/${EXEC_ID}/timeline?region=${AWS_REGION}"
fi
