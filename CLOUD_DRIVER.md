# Running the training loop on GCloud (not the laptop)

This project shares its "always-on driver" infrastructure with the BC22
sister project (`battlecode22-vibe`) — see that project's `CLOUD_DRIVER.md`
for the original setup rationale. **No new driver VM was created for this
project.** The existing `claude-driver` VM already runs this very Claude
Code session, with this repo checked out alongside the BC22 one.

## Architecture

```
  claude-driver (e2-small, always on, ~$12/mo, SHARED across both projects)
    └─ runs `claude` (this session)  ──►  /Users/terryvanbelle/projects/vibe_bc26 (this repo, checked out
                                           locally under this name; the GitHub repo itself is `battlecode26-vibe`)
                                           /Users/terryvanbelle/projects/vibe (BC22, sibling)
                                           ~/.claude/... memory (shared, both projects' memories coexist)
    └─ gcloud (VM service account, cloud-platform scope, project editor role)
         starts/stops ──►  battlecode-dev (e2-standard-8, on only during gauntlets,
                            ALSO SHARED — has both ~/battlecode22-scaffold and
                            ~/battlecode26-vibe checkouts, jdk8 and jdk21 side by side)
```

## Day to day

Same VM, same session mechanics as the BC22 project's `CLOUD_DRIVER.md`
describes (tmux, tmux attach/detach, stop/start commands). The only thing
specific to this project is which repo directory the session is working in
and which JDK/checkout on `battlecode-dev` its scripts target — both already
wired into `tools/gauntlet.sh` and `tools/vm-match.sh` here (`~/jdk21`,
`~/battlecode26-vibe`).

To resume work on this project specifically in a fresh session on the
driver:

```
cd /Users/terryvanbelle/projects/vibe_bc26
claude
```

Then: "Follow the steps in TRAINING_ALGORITHM.md to keep improving the
Battlecode 2026 bot." `TRAINING_LOG.md` is the running record — a fresh
session picks up mid-iteration from it.

## Notes / caveats specific to sharing the driver + `battlecode-dev`

- **`battlecode-dev` disk is shared.** Both projects' Gradle caches, JDKs,
  and per-run match/log files accumulate on the same 20 GB-class disk. Keep
  an eye on `df -h /` there if builds start failing mysteriously; old
  `run*.log`/`match-*.log` files from either project's history are safe to
  prune if space gets tight.
- **Stopping `battlecode-dev` affects both projects.** Don't stop it out
  from under an in-flight Gauntlet run for the *other* project. Check
  `gcloud compute instances describe battlecode-dev --zone=us-west1-b
  --format='value(status)'` and whether a `gauntlet_run.sh`/
  `battlecode.server` process is alive on it (`pgrep`) before stopping.
- **`git` push creds on the driver** are already configured (via `gh auth`)
  from the BC22 project's setup and cover this repo too, since both are
  under the same `terryvanbelle` GitHub account.
