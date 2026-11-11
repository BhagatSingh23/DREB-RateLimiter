#!/usr/bin/env python3
"""
Publication Figure Generator for DREB-RCA70 Manuscript
Generates vector figures (PDF and SVG) with consistent styling.
"""

import os
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# Configure publication matplotlib style
plt.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['DejaVu Serif', 'Times New Roman', 'Palatino'],
    'font.size': 10,
    'axes.labelsize': 11,
    'axes.titlesize': 12,
    'legend.fontsize': 9,
    'xtick.labelsize': 9,
    'ytick.labelsize': 9,
    'figure.titlesize': 13,
    'text.usetex': False,
    'figure.autolayout': True,
    'lines.linewidth': 1.8,
    'lines.markersize': 6,
    'axes.grid': True,
    'grid.alpha': 0.3,
    'grid.linestyle': '--',
    'figure.dpi': 300
})

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

def save_fig(fig, name):
    pdf_path = os.path.join(OUT_DIR, f"{name}.pdf")
    svg_path = os.path.join(OUT_DIR, f"{name}.svg")
    png_path = os.path.join(OUT_DIR, f"{name}.png")
    fig.savefig(pdf_path, bbox_inches='tight')
    fig.savefig(svg_path, bbox_inches='tight')
    fig.savefig(png_path, bbox_inches='tight', dpi=300)
    plt.close(fig)
    print(f"Saved: {name}.pdf, {name}.svg, {name}.png")

# -----------------------------------------------------------------------------
# Figure 1: Architecture & Adaptive Ceiling Hysteresis Loop
# -----------------------------------------------------------------------------
def plot_fig1():
    fig, ax1 = plt.subplots(figsize=(6.5, 4.0))
    K = np.linspace(0, 4.0, 500)
    baseCap = 450.0
    gamma = 0.75
    C_eff = baseCap / (1.0 + gamma * K)

    line1, = ax1.plot(K, C_eff, color='#1f77b4', lw=2.2, label=r'Adaptive Burst Ceiling $C_{\mathrm{eff}}(K)$')
    ax1.set_xlabel('Abuse Score $K$')
    ax1.set_ylabel(r'Effective Burst Ceiling $C_{\mathrm{eff}}$ (tokens)', color='#1f77b4')
    ax1.tick_params(axis='y', labelcolor='#1f77b4')
    ax1.set_ylim(80, 500)

    # Key state annotations
    states = [
        (0.0, 450.0, r'Quiescent Baseline' + '\n' + r'$K=0, C_{\mathrm{eff}}=450$'),
        (1.0, 257.14, r'Single Burst ($K=1.0$)' + '\n' + r'$C_{\mathrm{eff}}=257.14$'),
        (2.0, 180.0, r'Repeated Burst ($K=2.0$)' + '\n' + r'$C_{\mathrm{eff}}=180.0$'),
        (4.0, 112.5, r'Saturated ($K=4.0$)' + '\n' + r'$C_{\mathrm{eff}}=112.5$')
    ]
    for k_val, c_val, label in states:
        ax1.scatter([k_val], [c_val], color='#d62728', zorder=5, s=35)
        offset_y = 15 if k_val < 2.0 else -30
        offset_x = 0.08 if k_val < 3.5 else -0.8
        ax1.annotate(label, xy=(k_val, c_val), xytext=(k_val + offset_x, c_val + offset_y),
                     arrowprops=dict(arrowstyle="->", color='#d62728', lw=1.0),
                     fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#fff2f2", edgecolor="#d62728", alpha=0.9))

    # Add recovery arrow
    ax1.annotate('', xy=(0.1, 440), xytext=(2.0, 200),
                 arrowprops=dict(arrowstyle="->", connectionstyle="arc3,rad=-0.25",
                                color="#2ca02c", lw=2.0, linestyle=":"))
    ax1.text(0.9, 360, r'Continuous Linear Recovery ($\lambda_{\mathrm{rec}} = 0.05\,\mathrm{s}^{-1}$)',
             color='#2ca02c', fontsize=8.5, rotation=-22, weight='bold')

    plt.title('DREB-RCA70 Rest-Conditioned Burst Ceiling Dynamics', pad=12)
    save_fig(fig, 'fig1_architecture_adaptive_ceiling')

# -----------------------------------------------------------------------------
# Figure 2: Scope 1 and Scope 2 Operational Pareto Frontiers
# -----------------------------------------------------------------------------
def plot_fig2():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(9.5, 4.2))

    # Scope 1: c <= 700
    # X-axis: Request FRR (%), Y-axis: Adversarial Farming Rate (tokens/unit)
    policies_s1 = [
        ('static_db_070c', 0.0, 0.4517, '#2ca02c', 'o', 'Static Dual Bucket\n(Sole Frontier Member)'),
        ('tb_standard', 0.0, 0.4517, '#7f7f7f', 's', 'Token Bucket (700)'),
        ('gcra', 0.0, 0.4517, '#bcbd22', '^', 'GCRA (700)'),
        ('rca70', 0.0, 0.0817, '#1f77b4', 'D', 'DREB-RCA70\n(Suppression Leader)'),
        ('tb_large', 0.0, 1.0000, '#d62728', 'X', 'Token Bucket (1000)')
    ]
    for name, frr, farm, color, marker, label in policies_s1:
        ax1.scatter(frr, farm, color=color, marker=marker, s=80, label=name, zorder=5)

    ax1.annotate('Static Dual Bucket\n(Dominates Scope 1)', xy=(0.0, 0.4517), xytext=(0.5, 0.55),
                 arrowprops=dict(arrowstyle="->", color='#2ca02c'), fontsize=8,
                 bbox=dict(boxstyle="round,pad=0.2", facecolor="#eafaf1", edgecolor="#2ca02c"))
    ax1.annotate('DREB-RCA70\n(81.9% Farming Reduction)', xy=(0.0, 0.0817), xytext=(0.5, 0.18),
                 arrowprops=dict(arrowstyle="->", color='#1f77b4'), fontsize=8,
                 bbox=dict(boxstyle="round,pad=0.2", facecolor="#ebf5fb", edgecolor="#1f77b4"))

    ax1.set_title(r'(a) Scope 1: Small Bursts ($c \leq 700$)', fontsize=10.5)
    ax1.set_xlabel('Request False Rejection Rate (FRR %)')
    ax1.set_ylabel('Adversarial Farming Throughput (tok/unit)')
    ax1.set_xlim(-0.2, 5.0)
    ax1.set_ylim(0.0, 1.1)

    # Scope 2: c = 1000
    # tb_standard, static_db_070c, gcra have 100% Request FRR!
    policies_s2_disq = [
        ('static_db_070c', 100.0, 0.4517, '#7f7f7f', 'o'),
        ('tb_standard', 100.0, 0.4517, '#7f7f7f', 's'),
        ('gcra', 100.0, 0.4517, '#7f7f7f', '^')
    ]
    for name, frr, farm, color, marker in policies_s2_disq:
        ax2.scatter(frr, farm, color=color, marker=marker, s=60, alpha=0.5, zorder=3)

    ax2.annotate('Disqualified Policies\n(100% FRR for $c=1000$)', xy=(100.0, 0.4517), xytext=(55, 0.65),
                 arrowprops=dict(arrowstyle="->", color='#7f7f7f', lw=1.2), fontsize=8,
                 bbox=dict(boxstyle="round,pad=0.2", facecolor="#f2f2f2", edgecolor="#7f7f7f"))

    # Eligible frontier members: rca70 and tb_large
    ax2.scatter([0.0], [0.0817], color='#1f77b4', marker='D', s=100, zorder=6, label='DREB-RCA70 (Frontier)')
    ax2.scatter([0.0], [1.0000], color='#d62728', marker='X', s=100, zorder=6, label='tb_large (Frontier)')

    # Draw frontier line
    ax2.plot([0.0, 0.0], [0.0817, 1.0000], color='#1f77b4', lw=2.2, linestyle='--', label='Pareto Frontier')
    ax2.annotate('DREB-RCA70\n(0% FRR, 81.9% Suppression)', xy=(0.0, 0.0817), xytext=(12, 0.15),
                 arrowprops=dict(arrowstyle="->", color='#1f77b4'), fontsize=8,
                 bbox=dict(boxstyle="round,pad=0.2", facecolor="#ebf5fb", edgecolor="#1f77b4"))
    ax2.annotate('Token Bucket Large\n(0% FRR, 0% Suppression)', xy=(0.0, 1.0000), xytext=(12, 0.90),
                 arrowprops=dict(arrowstyle="->", color='#d62728'), fontsize=8,
                 bbox=dict(boxstyle="round,pad=0.2", facecolor="#fdebd0", edgecolor="#d62728"))

    ax2.set_title('(b) Scope 2: Large Bursts ($c = 1000$)', fontsize=10.5)
    ax2.set_xlabel('Request False Rejection Rate (FRR %)')
    ax2.set_ylabel('Adversarial Farming Throughput (tok/unit)')
    ax2.set_xlim(-5.0, 110.0)
    ax2.set_ylim(0.0, 1.1)

    save_fig(fig, 'fig2_pareto_frontiers')

# -----------------------------------------------------------------------------
# Figure 3: Adversarial Farming Suppression Over Time (W04, W08, W10)
# -----------------------------------------------------------------------------
def plot_fig3():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(9.5, 4.0))

    t = np.linspace(0, 30, 300)
    # W04: pulses every 1 second
    # Token Bucket admits full burst each pulse
    tb_w04_admitted = 0.4517 * t
    rca70_w04_admitted = 0.0817 * t

    ax1.plot(t, tb_w04_admitted, color='#d62728', lw=2.0, label='Token Bucket (Baseline)')
    ax1.plot(t, rca70_w04_admitted, color='#1f77b4', lw=2.2, label='DREB-RCA70 (Ours)')
    ax1.fill_between(t, rca70_w04_admitted, tb_w04_admitted, color='#1f77b4', alpha=0.15, label='Suppressed Volume (81.9%)')

    ax1.set_title('W04: Periodic Burst Farming', fontsize=10.5)
    ax1.set_xlabel('Observation Time (s)')
    ax1.set_ylabel('Cumulative Admitted Tokens / Scaled Unit')
    ax1.legend(loc='upper left', fontsize=8.5)

    # Bar chart for suppression across W04, W08, W10, and Closed-Loop
    workloads = ['W04\n(Periodic)', 'W08\n(Adaptive)', 'W10\n(Multi-Client)', 'Closed-Loop\nCredit Rate']
    suppressions = [81.9, 56.7, 85.7, 93.1]
    colors = ['#1f77b4', '#3498db', '#2980b9', '#1b4f72']

    bars = ax2.bar(workloads, suppressions, color=colors, width=0.55, edgecolor='black', linewidth=0.8, zorder=3)
    ax2.set_ylabel('Adversarial Farming Suppression (%)')
    ax2.set_ylim(0, 105)
    ax2.set_title('Suppression Efficiency Across Attack Suites', fontsize=10.5)

    for bar, val in zip(bars, suppressions):
        height = bar.get_height()
        ax2.annotate(f"{val:.1f}%",
                     xy=(bar.get_x() + bar.get_width() / 2, height),
                     xytext=(0, 3), textcoords="offset points",
                     ha='center', va='bottom', fontsize=9, weight='bold')

    save_fig(fig, 'fig3_adversarial_farming_suppression')

# -----------------------------------------------------------------------------
# Figure 4: Abuse Score Recovery Trajectories & Ceilings
# -----------------------------------------------------------------------------
def plot_fig4():
    fig, ax1 = plt.subplots(figsize=(6.5, 4.0))

    t = np.linspace(0, 25, 500)
    lambda_rec = 0.05
    k0_levels = [1.0, 1.5, 2.0]
    colors = ['#1f77b4', '#ff7f0e', '#2ca02c']

    for k0, col in zip(k0_levels, colors):
        k_t = np.maximum(0.0, k0 - lambda_rec * t)
        ax1.plot(t, k_t, color=col, lw=2.0, label=f'$K_0 = {k0}$')

    # Milestone: K0=1.0 reaches epsilon=0.05 at 19.0 s
    ax1.scatter([19.0], [0.05], color='#d62728', s=45, zorder=5)
    ax1.annotate(r'$\tau_{0.05} = 19.0\,\mathrm{s}$' + '\n($K=0.05$ threshold)',
                 xy=(19.0, 0.05), xytext=(12.0, 0.45),
                 arrowprops=dict(arrowstyle="->", color='#d62728', lw=1.2),
                 fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#fff2f2", edgecolor="#d62728"))

    ax1.scatter([20.0], [0.0], color='#2ca02c', s=45, zorder=5)
    ax1.annotate(r'$\tau_0 = 20.0\,\mathrm{s}$ ($K=0$ zero)',
                 xy=(20.0, 0.0), xytext=(17.5, 0.22),
                 arrowprops=dict(arrowstyle="->", color='#2ca02c', lw=1.2),
                 fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#eafaf1", edgecolor="#2ca02c"))

    ax1.set_xlabel('Quiescent Elapsed Time (s)')
    ax1.set_ylabel('Abuse Score $K(t)$')
    ax1.set_ylim(-0.05, 2.2)
    ax1.legend(loc='upper right', fontsize=8.5)
    plt.title(r'Continuous Linear Abuse Recovery ($K(t) = \max(0, K_0 - \lambda_{\mathrm{rec}} t)$)', pad=12)

    save_fig(fig, 'fig4_recovery_trajectories')

# -----------------------------------------------------------------------------
# Figure 5: Latency Distributions Across Four Measurement Scopes
# -----------------------------------------------------------------------------
def plot_fig5():
    fig, ax = plt.subplots(figsize=(7.5, 4.2))

    scopes = [
        'Scope A:\nIn-Memory Java',
        'Scope B:\nRedis Lua Engine',
        'Scope C:\nAsync Network RTT',
        'Scope D:\nSync WAIT 1 RTT'
    ]
    p50_us = [0.042, 82.0, 259.0, 348.0]
    p90_us = [0.061, 145.0, 412.0, 720.0]
    p99_us = [0.083, 310.0, 667.0, 1301.0]

    x = np.arange(len(scopes))
    width = 0.25

    rects1 = ax.bar(x - width, p50_us, width, label='P50 Latency', color='#2ca02c', edgecolor='black', linewidth=0.7)
    rects2 = ax.bar(x, p90_us, width, label='P90 Latency', color='#1f77b4', edgecolor='black', linewidth=0.7)
    rects3 = ax.bar(x + width, p99_us, width, label='P99 Latency', color='#d62728', edgecolor='black', linewidth=0.7)

    ax.set_yscale('log')
    ax.set_ylabel(r'Latency ($\mu$s, Log Scale)')
    ax.set_title('Execution Latency Across Four Measurement Scopes', pad=12)
    ax.set_xticks(x)
    ax.set_xticklabels(scopes, fontsize=9)
    ax.legend(loc='upper left', fontsize=9)
    ax.set_ylim(0.01, 3000.0)

    # Annotate WAIT 1 penalty
    ax.annotate('+95.1% P99 Penalty\n(Replication Durability)',
                xy=(3 + width, 1301.0), xytext=(2.2, 1400.0),
                arrowprops=dict(arrowstyle="->", color='#d62728', lw=1.2),
                fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#fff2f2", edgecolor="#d62728"))

    # Annotate Java nanosecond dispatch
    ax.annotate('42 ns P50\n(Zero Allocations)',
                xy=(0 - width, 0.042), xytext=(-0.35, 0.3),
                arrowprops=dict(arrowstyle="->", color='#2ca02c', lw=1.2),
                fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#eafaf1", edgecolor="#2ca02c"))

    save_fig(fig, 'fig5_latency_hierarchy')

# -----------------------------------------------------------------------------
# Figure 6: Single-Event Burst Over-Admission Envelope
# -----------------------------------------------------------------------------
def plot_fig6():
    fig, ax = plt.subplots(figsize=(7.0, 4.2))

    t_ms = np.linspace(0, 50, 500)
    t_s = t_ms / 1000.0

    # Local G1: S_max + R * T
    S_max = 1285.7143
    R = 1000.0
    G1 = S_max + R * t_s

    # Physical G1_delta: 1295.7143 + R * T (delta = 5 ms)
    G1_delta = (S_max + 2 * R * 0.005) + R * t_s

    # Single-Event Max Burst: 2 * C_max = 2000.0
    single_event_cap = np.full_like(t_ms, 2000.0)

    ax.plot(t_ms, G1, color='#2ca02c', lw=2.0, linestyle='-', label=r'Ideal Local $G_1(T) = 1285.7 + 1000 \cdot T$')
    ax.plot(t_ms, G1_delta, color='#1f77b4', lw=2.0, linestyle='--', label=r'Physical $G_{1,\delta}(t) = 1295.7 + 1000 \cdot t$ ($\pm 5\,\mathrm{ms}$)')
    ax.plot(t_ms, single_event_cap, color='#d62728', lw=2.2, linestyle='-.', label=r'Single-Event Failover Envelope ($2 C_{\max} = 2000$)')

    # Shaded excess at T = 10 ms
    ax.vlines(10.0, 1295.7143, 2000.0, color='#d62728', linestyle=':', lw=1.5)
    ax.scatter([10.0], [2000.0], color='#d62728', s=45, zorder=5)
    ax.annotate(r'Excess vs $G_1$: $+704.3\,\mathrm{tokens}$' + '\n' + r'Excess vs $G_{1,\delta}$: $+694.3\,\mathrm{tokens}$',
                xy=(10.0, 2000.0), xytext=(15.0, 1750.0),
                arrowprops=dict(arrowstyle="->", color='#d62728', lw=1.2),
                fontsize=8.5, bbox=dict(boxstyle="round,pad=0.2", facecolor="#fff2f2", edgecolor="#d62728"))

    ax.set_xlabel('Observation Window $T$ (milliseconds)')
    ax.set_ylabel('Admitted Tokens Envelope')
    ax.set_xlim(0, 50)
    ax.set_ylim(1100, 2200)
    ax.legend(loc='lower right', fontsize=8.5)
    plt.title(r'Single-Event Failover Over-Admission Envelope ($T \in [0, 50\,\mathrm{ms}]$)', pad=12)

    save_fig(fig, 'fig6_single_event_failover_envelope')

if __name__ == '__main__':
    print(f"Generating publication figures in {OUT_DIR}...")
    plot_fig1()
    plot_fig2()
    plot_fig3()
    plot_fig4()
    plot_fig5()
    plot_fig6()
    print("All 6 publication figures generated successfully.")
