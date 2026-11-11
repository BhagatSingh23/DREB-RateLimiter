# Phase 9 Publication Figures

This directory contains publication-ready figures for the DREB-RCA70 manuscript, along with the automated generation script.

## Figure Inventory

| Figure | Filename | Description | Source Dataset / Derivation |
|--------|----------|-------------|-----------------------------|
| **Figure 1** | `fig1_architecture_adaptive_ceiling.{pdf,svg,png}` | Dynamic ceiling suppression curve $C_{\text{eff}}(K) = \frac{450}{1 + 0.75 K}$, state transitions ($K=0, 1, 2, 4$), and continuous recovery trajectory. | Analytical derivation; Algorithm 1. |
| **Figure 2** | `fig2_pareto_frontiers.{pdf,svg,png}` | (a) Scope 1 ($c \le 700$) Pareto frontier with `static_db_070c` as sole member; (b) Scope 2 ($c = 1000$) Pareto frontier with `{rca70, tb_large}`, showing 100% FRR disqualification of baselines. | `research/comparison/phase5_baseline_comparison_raw.csv`, `PHASE5_4_1_FRONTIER_RECONCILIATION.md`. |
| **Figure 3** | `fig3_adversarial_farming_suppression.{pdf,svg,png}` | Adversarial burst admission over time (W04 periodic farming) and suppression breakdown across W04 (81.9%), W08 (56.7%), W10 (85.7%), and closed-loop credit-rate probing (93.1%). | `research/comparison/phase5_baseline_comparison_raw.csv`, `research/ablation/phase3_ablation_raw.csv`. |
| **Figure 4** | `fig4_recovery_trajectories.{pdf,svg,png}` | Abuse score recovery trajectories $K(t) = \max(0, K_0 - \lambda_{\text{rec}} t)$ from $K_0 \in \{1.0, 1.5, 2.0\}$, highlighting $\tau_{0.05} = 19.0\,\text{s}$ and $\tau_0 = 20.0\,\text{s}$. | `research/redis/PHASE7_3_1_G3_RECOVERY_DERIVATION.md`, Theorem 6. |
| **Figure 5** | `fig5_latency_hierarchy.{pdf,svg,png}` | Execution latency hierarchy (P50, P90, P99) across In-Memory Java (42 ns P50), Redis Lua Engine (0.082 ms P50), Async Network RTT (0.259 ms P50), and Sync `WAIT 1` RTT (0.348 ms P50, +95.1% P99 penalty). | `research/comparison/PHASE5_1_LATENCY_AUDIT.md`, `research/redis/PHASE7_2_END_TO_END_LATENCY.md`. |
| **Figure 6** | `fig6_single_event_failover_envelope.{pdf,svg,png}` | Single-event failover burst envelope ($2 C_{\max} = 2,000\,\text{tokens}$) compared against ideal local $G_1(T)$ and clock-tolerant $G_{1,\delta}(t)$ envelopes for $T \in [0, 50\,\text{ms}]$, highlighting $+704.3$ and $+694.3$ token excess. | `research/redis/PHASE7_3_3_SINGLE_EVENT_BOUND_VERIFICATION.md`, Theorem 3. |

## Regeneration Instructions

To regenerate all vector and raster figures from scratch:
```bash
python3 PHASE9_PUBLICATION_FIGURES/generate_figures.py
```
Requires Python 3.10+ with `matplotlib` and `numpy`.
