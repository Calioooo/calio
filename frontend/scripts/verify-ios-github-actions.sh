#!/usr/bin/env bash
set -euo pipefail

WORKFLOW="ios-ci.yml"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

git push origin "$BRANCH"

gh workflow run "$WORKFLOW" --ref "$BRANCH"

RUN_ID="$(
  gh run list \
    --workflow "$WORKFLOW" \
    --branch "$BRANCH" \
    --limit 1 \
    --json databaseId \
    --jq '.[0].databaseId'
)"

if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
  echo "Failed to find GitHub Actions run for workflow=$WORKFLOW branch=$BRANCH"
  exit 1
fi

gh run watch "$RUN_ID" --exit-status
