#!/bin/bash
# Push CreditLens Docker image to AWS ECR
# Usage: ./infra/ecr-push.sh [aws-account-id] [region]

set -euo pipefail

ACCOUNT_ID=${1:-$(aws sts get-caller-identity --query Account --output text)}
REGION=${2:-ap-south-1}
REPO="creditlens"
IMAGE_TAG=$(git rev-parse --short HEAD)
ECR_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$REPO"

echo ">> Authenticating with ECR..."
aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

echo ">> Creating ECR repo (if absent)..."
aws ecr describe-repositories --repository-names "$REPO" --region "$REGION" 2>/dev/null || \
  aws ecr create-repository --repository-name "$REPO" --region "$REGION"

echo ">> Building Docker image..."
docker build -t "$REPO:$IMAGE_TAG" .

echo ">> Tagging: $ECR_URI:$IMAGE_TAG  +  :latest"
docker tag "$REPO:$IMAGE_TAG" "$ECR_URI:$IMAGE_TAG"
docker tag "$REPO:$IMAGE_TAG" "$ECR_URI:latest"

echo ">> Pushing..."
docker push "$ECR_URI:$IMAGE_TAG"
docker push "$ECR_URI:latest"

echo ""
echo "SUCCESS  →  $ECR_URI:$IMAGE_TAG"
echo ""
echo "Deploy to ECS:"
echo "  aws ecs update-service --cluster creditlens --service creditlens-svc \\"
echo "    --force-new-deployment --region $REGION"
