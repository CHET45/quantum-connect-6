# Final prep, night 2026-11-05/06 — status (updated by the agent as it goes)

Base for the final (agreed with the team 20:10): rc8 code + dynamic rules + g6f tables = `night/wt-dyn`, branch
rc9-dynrules. Proven at k=6: 400/400 fixed-node games identical to rc8; real time 15x30 51.5% of 200 vs rc8.
the team (23:40): work alone overnight; several versions for different configurations plus a universal one; no
time to train in the morning.

Done:
- 33eec46 g6f tables restored; jar `qc-bot/dist/night/exp/final-base.jar` (sha256 ecb1baf9...).
- f080a91 switches `Announced.java` (k, blast, cooldown, call limit, game clock, warm-up), `night/tools/final-build.ps1`
  (one command: set values, build, all tests, sanity match, stage jar, -Deliver to OneDrive), `docs/final-day.md`.
- cffee9e rules inference (k only up from long lines that did not end the game; cooldown up from refused own
  specials, down from opponent specials; reset each game), one generic table per k in Weights, `--k` for SelfPlayMain
  and FeatureSpecDump. All 107 tests pass at the core values; sanity 97% vs tactics, 0 blunders.
- final-build.ps1 `-Tables all|none|4,5`: tables for other k come from `night/wt-dyn/weights-k/eval-generic-kK.qcw`.

Running (started 23:57): `night/tools/ktrain.py --ks 5,4,7,8 --gen1` — per k: spec, self-play with hand eval
(300k positions x 6 sizes, 8 threads), CPU fit (no GPU tonight: NVIDIA driver error 14:38 during the g8s fit), gate vs
hand at fixed nodes, then gen1 (self-play with the gen0 table, refit, gate vs gen0). Log `night/logs/ktrain/ktrain.log`,
data `qc-bot/data/night/evalk/`, models `qc-bot/data/night/evalk/models/kK-gN/eval.qcw`. Restartable (skips done stages).

Next: copy the better table per k to `night/wt-dyn/weights-k/`, build the universal jar (all tables) and one jar per k
(K set, own table), check k=6 identity of the universal jar, real-time timing check, README + sha256 to OneDrive
`qc-bot/final/`.

Results:
- 00:25 k5-g0 vs hand (fixed 20k nodes, 150 pairs): 8x12 72.5% (+168), 15x22 79.7% (+237). Copied to weights-k.
- 00:45 k4-g0 vs hand: 8x12 91.0% (+402), 15x22 85.3% (+306). Copied to weights-k.
- 01:12 universal-k6-try (k6 + k4,k5 tables, inference) vs rc8 real time 15x30: 51% + 53% = 52% of 200, 0 timeouts, p50 72-74 vs 72-75 ms. No regression.
- 01:09 k7-g0 fit ran on the CPU: budget saw ShellExperienceHost.exe on the GPU; added it to WINDOWS_DESKTOP in qctrain/budget.py.
- 01:15 k7-g0 vs hand: 15x11 51.0% (47% draws), 15x22 64.8% (+106). Copied to weights-k.
- 01:21 inference in real play: jar told k=5 in the core-2.0 engine (k=6) vs rc8, 15x30: 45% of 40, 0 timeouts, 0 invalid moves (it raises k from the first 5-line that did not win).
- 01:29 k8-g0 fit killed 6x by gpu-watch.ps1 (its own desktop list lacked ShellExperienceHost.exe; no driver error). Fixed the list, restarted the watchdog, k8 runs as a second ktrain instance (--ks 8 --gen1), GPU fit since 01:30. Main ktrain moved on to gen1 for 5,4,7.
- 01:39 k8-g0 vs hand: 15x11 40.2% (-69), 15x22 33.0% (-123): NOT shipped (no overfit: train~val, spearman 0.69-0.86). k8 gen1 stopped. Trying k8-ns (--no-stage, 20 epochs). Without a k8 table the bot uses the hand eval at k=8.
- 01:51 k8-ns vs hand: 15x11 47.5% (-17), 15x22 47.2% (-20): not shipped either. k=8 plays with the hand eval. 
- 01:52 building the jar matrix (k6,k4,k5,k7,k8; tables k4,k5,k7 gen0) and delivering it to OneDrive qc-bot/final as an interim set.
- 02:15 interim set delivered to OneDrive qc-bot/final: k6 95e902f8, k4 00063a10, k5 30ca7967, k7 5d13c3c2, k8 ea4deea6 (1.81 MB each; tables k4,k5,k7 gen0), README.md + SHA256SUMS.txt.
- 02:15 k5-g1 vs hand: 8x12 80.8% (+250), 15x22 84.7% (+297); vs k5-g0: 8x12 53.0% (+21), 15x22 66.0% (+115). k5-g1 copied to weights-k (jars to be rebuilt).
- 02:43 WPS Office (promecefpluginhost.exe) started on the GPU; gpu-watch stopped the k4-g1 fit, driver logged id 153 at the same second (killing a CUDA process logs it, see memory), gpu-stop flag set. Fits continue on the CPU (budget resumes from checkpoint); no more GPU tonight. Fits no longer pinned to E-cores.
- 03:02 k4-g1 vs hand: 8x12 93.0% (+449), 15x22 87.3% (+335); vs k4-g0: 8x12 61.3% (+80), 15x22 64.0% (+100). k4-g1 copied to weights-k.
- 03:28 k5-g2 vs hand: 8x12 75.2% (+192), 15x22 86.0% (+315); vs k5-g1: 8x12 53.8% (+27), 15x22 60.0% (+70). k5-g2 copied to weights-k.
- 03:50 k4-g2 vs hand: 8x12 93.3% (+459), 15x22 80.0% (+241); vs k4-g1: 8x12 56.0% (+42), 15x22 39.7% (-73). Keep k4-g1 (wide boards matter more).
- 04:12 k7-g1 vs hand: 15x11 56.2% (+43), 15x22 65.2% (+109); vs k7-g0: 15x11 58.2% (+57), 15x22 56.5% (+45). k7-g1 copied to weights-k.
- 04:40 k5-g3 vs hand: 8x12 81.2% (+254), 15x22 91.7% (+417); vs k5-g2: 8x12 66.2% (+117), 15x22 64.7% (+105). k5-g3 copied to weights-k. Starting k5 round 4.
- 05:04 k7-g2 vs hand: 15x11 65.5% (+111), 15x22 67.2% (+124); vs k7-g1: 15x11 49.2% (-6), 15x22 64.7% (+105). k7-g2 copied to weights-k. Starting k7 round 3.
- 05:28 k5-g4 vs hand: 8x12 85.2% (+304), 15x22 91.3% (+409); vs k5-g3: 8x12 58.8% (+62), 15x22 65.0% (+108). k5-g4 copied. Building matrix 2 (k4-g1, k5-g4, k7-g2), k5 round 5 started.
- 05:54 k7-g3 vs hand: 15x11 75.5% (+196), 15x22 84.7% (+297); vs k7-g2: 15x11 56.2% (+43), 15x22 62.8% (+91). Adopted (staged as .next until matrix 2 finishes). k7 round 4 started. Final matrix no later than 07:00.
- 06:17 k5-g5 vs hand: 8x12 83.2% (+278), 15x22 91.7% (+417); vs k5-g4: 8x12 56.7% (+47), 15x22 57.3% (+51). k5-g5 and k7-g3 now in weights-k.
- 05:29-06:15 matrix 2 hung: the Gradle daemon did not return after the k6 tests finished (05:35); killed it and the matrix. OneDrive still holds the 02:15 set. Final matrix after the k7 round-4 gates (~06:45).
- 06:41 k7-g4 vs hand: 15x11 70.2% (+149), 15x22 84.3% (+292); vs k7-g3: 15x11 52.2% (+15), 15x22 58.5% (+60). k7-g4 adopted. Training stopped. Final tables: k4-g1, k5-g5, k7-g4 (k8: hand). Final matrix started 06:42.

FINAL (07:06): OneDrive qc-bot/final holds the final set (all tables k4-g1, k5-g5, k7-g4; k8 hand eval):
  k6 44843930..., k4 845b5048..., k5 355e5edf..., k7 743541b7..., k8 0e48cbe1... (SHA256SUMS.txt, README.md).
  Each: 109 tests pass, sanity vs tactics 0 blunders. Branch rc9-dynrules at 3b22585 (tables in weights-k/).
  Leftovers: night/logs/gpu-stop flag still present (GPU fits fall back to CPU until it is removed).
- 07:16 final k6 jar vs rc8 real time 15x30: 50% + 48% = 49% of 100, 0 timeouts, 0 invalid. Even with rc8, as intended.
