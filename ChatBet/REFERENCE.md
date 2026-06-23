# ChatBet REFERENCE.md

## Workflow Reliability Improvements (Updated for Blocking Pushes)

To achieve <1/100 failure rate and address propagation delays:

The `github___push_files` tool is blocking on the push acceptance.

To handle GitHub propagation (the main cause of "fake" commits in responses):

**Mandatory Blocking Verification After Every Push:**

After `github___push_files` succeeds:

1. Call `bash` with a **polling loop** to actively wait until the commit is visible:

```bash
SHA=<the returned sha>
REPO=ViraXVespa/GrokSandbox
for i in {1..60}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://api.github.com/repos/$REPO/commits/$SHA" || echo "000")
  if [ "$STATUS" = "200" ]; then
    echo "Commit $SHA is live and visible."
    break
  fi
  echo "Waiting for GitHub propagation... attempt $i (status: $STATUS)"
  sleep 2
done
```

This makes the verification actively block until the commit is confirmed live via API (much better than fixed sleep).

2. Then call `browse_page` on the commit URL with instructions to confirm it's valid and matches the pushed content.

Only then report the real SHA.

This polling + verification makes the process truly blocking until the commit is usable, eliminating the "push didn't work" issue.

**Small Commits Rule (still mandatory):**
Break complex changes into small sequential commits. Verify each fully (including the blocking poll) before the next.

## Test Cycle Update

This is a test edit for the full test cycle as requested. Status: Test cycle completed successfully using real tools.

## Quick Status
- Module structure fixed
- XP tracking delegation improved
- Java 11 compliant
- Goal calculation logic updated for accurate "to goal" when already past percentage.
- Pickpocketing tracking (attempts, successes, consumables) restored in module.
- Chat message based tracking restored (strings tabled for now).

Refer to this doc for architecture and workflow.

## Workflow Note
Using raw GitHub reads + direct push tools + blocking polling verification to ensure near-100% reliable pushes. All pushes now use active blocking until the commit is live.