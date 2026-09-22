#!/usr/bin/env bash
# Retrieve upstream source/text/resource files from a blobless clone in
# size-aware batches. Large binary assets are excluded because the environment
# proxy blocks large pack transfers and they are gitignored / downloaded at build time.
#
# Skipped (large / build-time): .tzst .tar(.xxx) .obb .so .mp4 .zip .apk .bin .exe .dll .dat .sf2
# Kept (small source/text/resource): .java .h .c .cpp .xml .png .json .txt .md .py .gradle
#                                   .properties .yml .sh .glsl .icp .s .pass1 .pro .clang-format .LICENSE ...
set -u
cd "C:/Users/Doro/wl-upstream" || exit 1

GIT="git -c http.schannelCheckRevoke=false"
rm -f .git/index.lock

# Include list: everything except large binaries.
grep -Eiv '\.(tzst|tar(\.[a-z0-9]+)?|obb|so|mp4|zip|apk|bin|exe|dll|dat|sf2)$' /tmp/allfiles.txt > /tmp/sources.txt

# Todo: sources not already checked out (git ls-files = currently on disk+index).
comm -23 <(sort /tmp/sources.txt) <(git ls-files | sort) > /tmp/todo.txt
total=$(wc -l < /tmp/todo.txt)
echo "already present: $(git ls-files | wc -l) | to retrieve: $total" | tee /tmp/seed_progress.log

BATCH=10
ok=0
fail=0
declare -a batch
try_one() {  # returns 0 if fetched ok (single attempt; small packs rarely fail)
  if $GIT checkout HEAD -- "$1" >>/tmp/seed_progress.log 2>&1; then return 0; fi
  sleep 0.5
  $GIT checkout HEAD -- "$1" >>/tmp/seed_progress.log 2>&1
}
flush() {
  if [ ${#batch[@]} -eq 0 ]; then return; fi
  if $GIT checkout HEAD -- "${batch[@]}" >>/tmp/seed_progress.log 2>&1; then
    ok=$((ok + ${#batch[@]}))
  else
    for bf in "${batch[@]}"; do
      if try_one "$bf"; then ok=$((ok + 1)); else fail=$((fail + 1)); echo "FAIL: $bf" >>/tmp/seed_progress.log; fi
    done
  fi
  batch=()
}
while IFS= read -r f; do
  [ -z "$f" ] && continue
  batch+=("$f")
  if [ ${#batch[@]} -ge $BATCH ]; then flush; fi
done < /tmp/todo.txt
flush

echo "DONE ok=$ok fail=$fail" | tee -a /tmp/seed_progress.log
