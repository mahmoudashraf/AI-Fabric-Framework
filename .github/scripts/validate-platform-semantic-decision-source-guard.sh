#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
allowlist="${repo_root}/.github/platform-semantic-decision-source-guard-allowlist.tsv"

guarded_roots=(
  "Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/thinker/service"
  "ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java"
  "max-mode-widget/src/requestSemantics.ts"
  "max-mode-widget/src/hooks/useChatFlow.ts"
  "max-mode-widget/src/hooks/useAttachmentsController.ts"
  "max-mode-widget/src/hooks/useMaxModeController.ts"
)

for root in "${guarded_roots[@]}"; do
  if [[ ! -e "${repo_root}/${root}" ]]; then
    echo "Platform semantic-decision source guard root is missing: ${root}" >&2
    exit 1
  fi
done
if [[ ! -f "${allowlist}" ]]; then
  echo "Platform semantic-decision source guard allowlist is missing: ${allowlist}" >&2
  exit 1
fi

cd "${repo_root}"
forbidden_identifiers='detectsPromptInjection|detectsWriteIntent|classifyIssue\(|applyAttachmentFocus|infer(VectorSpace|Mode|Position|Intent|Action|Category)From(Query|Prompt|Message|Text)|queryMentions(Type|Domain|Source)|semanticKeywordFallback'
forbidden_output="$(rg -n --no-heading "${forbidden_identifiers}" "${guarded_roots[@]}" || true)"
if [[ -n "${forbidden_output}" ]]; then
  echo "Forbidden Platform semantic-substitution identifiers found:" >&2
  echo "${forbidden_output}" >&2
  exit 1
fi

java_risk='(query|prompt|message|utterance|original|text).{0,120}(contains\(|matches\(|regionMatches\(|startsWith\(|endsWith\(|split\(|Pattern\.compile)|((contains\(|matches\(|regionMatches\(|startsWith\(|endsWith\(|split\(|Pattern\.compile).{0,120}(query|prompt|message|utterance|original|text))'
typescript_risk='(query|prompt|message|utterance|original|text).{0,120}(\.includes\(|\.match\(|\.test\(|\.startsWith\(|\.endsWith\(|\.split\()|((\.includes\(|\.match\(|\.test\(|\.startsWith\(|\.endsWith\(|\.split\().{0,120}(query|prompt|message|utterance|original|text))'
candidates="$({ rg -n --no-heading -i "${java_risk}" "${guarded_roots[@]}" || true; rg -n --no-heading -i "${typescript_risk}" "${guarded_roots[@]}" || true; } | sort -u)"

violations=()
while IFS= read -r candidate; do
  [[ -z "${candidate}" ]] && continue
  path="${candidate%%:*}"
  remainder="${candidate#*:}"
  line_number="${remainder%%:*}"
  line_text="${remainder#*:}"
  reviewed=false

  while IFS=$'\t' read -r allowed_path marker owner reason; do
    [[ -z "${allowed_path}" || "${allowed_path}" == \#* ]] && continue
    if [[ "${path}" == "${allowed_path}" && "${line_text}" == *"${marker}"* ]]; then
      if [[ -z "${owner}" || -z "${reason}" ]]; then
        echo "Invalid Platform source-guard allowlist entry for ${allowed_path}." >&2
        exit 1
      fi
      reviewed=true
      break
    fi
  done < "${allowlist}"

  if [[ "${reviewed}" != true ]]; then
    violations+=("${path}:${line_number}:${line_text}")
  fi
done <<< "${candidates}"

if (( ${#violations[@]} > 0 )); then
  echo "Unreviewed text matching in Platform semantic-decision paths:" >&2
  printf '  %s\n' "${violations[@]}" >&2
  echo "Remove semantic substitution or add a narrow reviewed entry with owner and reason." >&2
  exit 1
fi

echo "Platform semantic-decision source guard passed."
