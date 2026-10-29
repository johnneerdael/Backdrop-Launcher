# Backdrop resource optimization

Owner direction: implement the proposed first pass (remove redundant fullscreen images/cache entries, visible artwork with bounded prefetch, pause hidden Home work, reuse native views/unchanged data). Aim for lower utilization than Projectivy despite its larger feature set. Routine implementation choices are delegated; preserve appearance initially. This is an acceptance requirement to measure, not a result that can be promised from feature counts.

Continue in the isolated `codex/native-home-benchmark` worktree. Improve both the current Flutter application and the engine-free native migration candidate. Keep production data/default Home intact. Use brief captures; reject foreground interference. Keep GPU graphics, resident/total process accounting and CPU time separate so moving allocations between buckets cannot count as an improvement.

## Approved approach

Flutter: draw hero images and gradients directly, share display-sized decode keys, bound initial app-artwork prefetch, and load remaining cards on demand. Pause Watch Next polling and queued work while hidden, invalidate stale responses, and resume only the selected/lookahead images. Preserve startup disk caching, artwork invalidation and remote focus behavior.

Native: retain cheap views, data and scroll/focus state across stop/start; clear heavy visual references and cancel requests without rebuilding the full tree on every return. Refresh data on source/provider/package changes or explicit scene changes. Deduplicate image requests and avoid decoding assets twice when the same image/size is already visible. Investigate the graphics allocation floor with short image-size ablations before selecting background decode budgets. Keep cards/text at their proper display size. Any reduced backdrop resolution or color depth must be explicit in results and checked visually.

Acceptance: release builds/tests/lint pass; unchanged content does not re-enumerate packages/rebuild Home on every return; hidden Home starts no refresh/image work; visible content and navigation remain usable. Comparative utilization remains open until equivalent content/settings and uncontested short captures demonstrate lower graphics, total memory and CPU time on defined workloads. Equal zero idle CPU is not evidence of lower CPU. Never silently remove features, lower whole-window resolution or shift work into another process to satisfy a counter.
