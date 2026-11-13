package dev.oopproof.core;

import dev.oopproof.spec.FormalSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * AlphaProof-inspired UCB1 / Best-First proof search engine.
 * Searches over candidate concept-binding hypotheses and applies formal tactics
 * across Hygiene, Structure, Behavior, and Extensibility strata until either
 * all obligations reach Q.E.D. or the strongest refutation counterexample is isolated.
 */
public final class ProofSearchEngine {

    private static final double EXPLORATION_C = 0.35;

    private static final class SearchArm {
        final int index;
        final FormalSpec.ScoredBinding hypothesis;
        final List<ProofStep> evaluatedSteps = new ArrayList<>();
        int nextObligationIdx = 0;
        int provedCount = 0;
        int visits = 0;
        boolean terminated = false;
        boolean refuted = false;

        SearchArm(int index, FormalSpec.ScoredBinding hypothesis) {
            this.index = index;
            this.hypothesis = hypothesis;
        }

        double meanValue(int totalObligations) {
            if (visits == 0) {
                return hypothesis.priorScore();
            }
            double progress = (double) provedCount / Math.max(1, totalObligations);
            return 0.3 * hypothesis.priorScore() + 0.7 * progress;
        }

        double ucbScore(int totalVisits, int totalObligations) {
            double q = meanValue(totalObligations);
            double u = EXPLORATION_C * hypothesis.priorScore()
                    * Math.sqrt(Math.log(totalVisits + 2.0) / (visits + 1.0));
            return q + u;
        }
    }

    public ProofCertificate prove(FormalSpec spec, WorkspaceAnalyzer.CodebaseModel model) {
        List<FormalSpec.Obligation> obligations = spec.obligations();
        List<FormalSpec.ScoredBinding> hypotheses = spec.proposeBindings(model);
        if (hypotheses.isEmpty()) {
            hypotheses = List.of(new FormalSpec.ScoredBinding(new BindingEnv(), 0.5, "default empty binding"));
        }

        List<SearchArm> arms = new ArrayList<>();
        for (int i = 0; i < Math.min(hypotheses.size(), 24); i++) {
            arms.add(new SearchArm(i, hypotheses.get(i)));
        }

        int totalVisits = 0;
        int nodesExpanded = 0;
        int prunedBranches = 0;
        SearchArm winningArm = null;

        PriorityQueue<SearchArm> frontier = new PriorityQueue<>(
                Comparator.comparingDouble((SearchArm a) -> -a.ucbScore(1, obligations.size()))
                        .thenComparingInt(a -> a.index));
        frontier.addAll(arms);

        while (!frontier.isEmpty()) {
            SearchArm arm = frontier.poll();
            if (arm.terminated) {
                continue;
            }
            if (arm.nextObligationIdx >= obligations.size()) {
                if (!arm.refuted && arm.provedCount == obligations.size()) {
                    winningArm = arm;
                    break;
                }
                arm.terminated = true;
                continue;
            }

            FormalSpec.Obligation ob = obligations.get(arm.nextObligationIdx);
            String stepId = "step-" + (arm.nextObligationIdx + 1) + "-" + ob.id();
            ProofStep step = ob.tactic().apply(stepId, model, arm.hypothesis.env());
            arm.evaluatedSteps.add(step);
            arm.nextObligationIdx++;
            arm.visits++;
            totalVisits++;
            nodesExpanded++;

            if (step.status() == ProofStatus.QED) {
                arm.provedCount++;
                if (arm.nextObligationIdx == obligations.size()) {
                    winningArm = arm;
                    break;
                }
                frontier.add(arm);
            } else {
                arm.refuted = true;
                if (ob.stopOnRefutation() && !model.compiledCleanly()) {
                    // Fatal compile failure affects all hypotheses identically
                    winningArm = null;
                    arm.terminated = true;
                    break;
                }
                if (arms.size() > 1 && arm.nextObligationIdx <= 3 && ob.stopOnRefutation()) {
                    // Prune mismatched binding hypothesis early
                    arm.terminated = true;
                    prunedBranches++;
                } else {
                    frontier.add(arm);
                }
            }
        }

        // Select winning arm or best near-miss arm for clear diagnostic report
        SearchArm selected = winningArm;
        if (selected == null) {
            selected = arms.stream()
                    .max(Comparator.comparingInt((SearchArm a) -> a.provedCount)
                            .thenComparingDouble(a -> a.hypothesis.priorScore()))
                    .orElse(arms.get(0));
            // Finish evaluating remaining non-fatal obligations on the best near-miss arm
            // (unless compilation failed, in which case compile/AST dependent tactics cannot run)
            if (model.compiledCleanly()) {
                while (selected.nextObligationIdx < obligations.size()) {
                    FormalSpec.Obligation ob = obligations.get(selected.nextObligationIdx);
                    String stepId = "step-" + (selected.nextObligationIdx + 1) + "-" + ob.id();
                    ProofStep step = ob.tactic().apply(stepId, model, selected.hypothesis.env());
                    selected.evaluatedSteps.add(step);
                    if (step.status() == ProofStatus.QED) {
                        selected.provedCount++;
                    }
                    selected.nextObligationIdx++;
                    nodesExpanded++;
                }
            }
        }

        boolean allQed = selected.evaluatedSteps.size() == obligations.size()
                && selected.evaluatedSteps.stream().allMatch(s -> s.status() == ProofStatus.QED);

        ProofStatus rootStatus = allQed
                ? ProofStatus.QED
                : (selected.evaluatedSteps.stream().anyMatch(s -> s.status() == ProofStatus.REFUTED)
                        ? ProofStatus.REFUTED
                        : ProofStatus.INCOMPLETE);

        long qedCount = selected.evaluatedSteps.stream().filter(s -> s.status() == ProofStatus.QED).count();
        String rootSummary = allQed
                ? "All " + obligations.size() + " formal obligations proved (Q.E.D.)."
                : "Discharged " + qedCount + "/" + selected.evaluatedSteps.size()
                        + " obligations; theorem " + rootStatus + ".";

        Map<String, Object> rootEvidence = new LinkedHashMap<>();
        rootEvidence.put("totalObligations", obligations.size());
        rootEvidence.put("provedObligations", qedCount);
        rootEvidence.put("bindingRationale", selected.hypothesis.rationale());

        ProofStep rootStep = new ProofStep(
                "root-" + spec.id(),
                spec.id(),
                "THEOREM",
                "ProofSearchEngine.UCB1",
                spec.title(),
                rootStatus,
                rootSummary,
                rootEvidence,
                List.of("candidateFiles=" + model.javaFiles().size()),
                selected.evaluatedSteps);

        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("hypothesesGenerated", hypotheses.size());
        telemetry.put("hypothesesEvaluated", arms.size());
        telemetry.put("nodesExpanded", nodesExpanded);
        telemetry.put("prunedBranches", prunedBranches);
        telemetry.put("winningPriorScore", selected.hypothesis.priorScore());
        telemetry.put("winningRationale", selected.hypothesis.rationale());

        return new ProofCertificate(
                spec.id(),
                spec.title(),
                model.rootDir().toString(),
                model.sourceSha256Manifest(),
                selected.hypothesis.env().toDisplayMap(),
                telemetry,
                rootStep);
    }
}
