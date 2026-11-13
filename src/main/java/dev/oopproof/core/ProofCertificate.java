package dev.oopproof.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Self-contained formal proof certificate emitted by ProofSearchEngine and
 * verified independently by the ProofKernel (Trusted Computing Base).
 */
public final class ProofCertificate {

    private final String formatVersion;
    private final String specId;
    private final String specTitle;
    private final String targetPath;
    private final String timestampUtc;
    private final Map<String, String> sourceSha256Manifest;
    private final Map<String, String> discoveredBindings;
    private final Map<String, Object> searchTelemetry;
    private final ProofStep rootStep;
    private final String certificateHash;

    public ProofCertificate(
            String specId,
            String specTitle,
            String targetPath,
            Map<String, String> sourceSha256Manifest,
            Map<String, String> discoveredBindings,
            Map<String, Object> searchTelemetry,
            ProofStep rootStep) {
        this("oopproof-java/1.0", specId, specTitle, targetPath, Instant.now().toString(),
                sourceSha256Manifest, discoveredBindings, searchTelemetry, rootStep, null);
    }

    private ProofCertificate(
            String formatVersion,
            String specId,
            String specTitle,
            String targetPath,
            String timestampUtc,
            Map<String, String> sourceSha256Manifest,
            Map<String, String> discoveredBindings,
            Map<String, Object> searchTelemetry,
            ProofStep rootStep,
            String existingHash) {
        this.formatVersion = Objects.requireNonNull(formatVersion);
        this.specId = Objects.requireNonNull(specId);
        this.specTitle = Objects.requireNonNull(specTitle);
        this.targetPath = Objects.requireNonNull(targetPath);
        this.timestampUtc = Objects.requireNonNull(timestampUtc);
        this.sourceSha256Manifest = Collections.unmodifiableMap(new LinkedHashMap<>(sourceSha256Manifest));
        this.discoveredBindings = Collections.unmodifiableMap(new LinkedHashMap<>(discoveredBindings));
        this.searchTelemetry = Collections.unmodifiableMap(new LinkedHashMap<>(searchTelemetry));
        this.rootStep = Objects.requireNonNull(rootStep);
        this.certificateHash = existingHash != null ? existingHash : computeCertificateHash();
    }

    public String computeCertificateHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(formatVersion).append('|')
                .append(specId).append('|')
                .append(specTitle).append('|')
                .append(timestampUtc).append('|')
                .append(JsonUtil.stringify(sourceSha256Manifest)).append('|')
                .append(JsonUtil.stringify(discoveredBindings)).append('|')
                .append(rootStep.stepHash());
        return ProofStep.sha256Hex(sb.toString());
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", formatVersion);
        m.put("specId", specId);
        m.put("specTitle", specTitle);
        m.put("targetPath", targetPath);
        m.put("timestampUtc", timestampUtc);
        m.put("sourceSha256Manifest", sourceSha256Manifest);
        m.put("discoveredBindings", discoveredBindings);
        m.put("searchTelemetry", searchTelemetry);
        m.put("rootStep", rootStep.toMap());
        m.put("certificateHash", certificateHash);
        return JsonUtil.stringify(m);
    }

    @SuppressWarnings("unchecked")
    public static ProofCertificate fromJson(String json) {
        Map<String, Object> m = JsonUtil.parseObject(json);
        Map<String, String> manifest = new LinkedHashMap<>();
        if (m.get("sourceSha256Manifest") instanceof Map<?, ?> rawM) {
            for (Map.Entry<?, ?> e : rawM.entrySet()) {
                manifest.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Map<String, String> bindings = new LinkedHashMap<>();
        if (m.get("discoveredBindings") instanceof Map<?, ?> rawB) {
            for (Map.Entry<?, ?> e : rawB.entrySet()) {
                bindings.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Map<String, Object> telemetry = m.get("searchTelemetry") instanceof Map<?, ?> rawT
                ? new LinkedHashMap<>((Map<String, Object>) rawT)
                : Map.of();
        ProofStep root = ProofStep.fromMap((Map<String, Object>) m.get("rootStep"));
        return new ProofCertificate(
                (String) m.get("formatVersion"),
                (String) m.get("specId"),
                (String) m.get("specTitle"),
                (String) m.get("targetPath"),
                (String) m.get("timestampUtc"),
                manifest,
                bindings,
                telemetry,
                root,
                (String) m.get("certificateHash"));
    }

    public String toLeanStyleTrace() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- =====================================================================\n");
        sb.append("-- OOPProof-Java Formal Verification Certificate\n");
        sb.append("-- Theorem : ").append(specId).append(" (").append(specTitle).append(")\n");
        sb.append("-- Verdict : ").append(rootStep.status()).append('\n');
        sb.append("-- CertSHA : ").append(certificateHash).append('\n');
        sb.append("-- =====================================================================\n");
        if (!discoveredBindings.isEmpty()) {
            sb.append("bindings\n");
            for (Map.Entry<String, String> e : discoveredBindings.entrySet()) {
                sb.append("  ").append(e.getKey()).append(" := ").append(e.getValue()).append('\n');
            }
        }
        sb.append('\n');
        renderStep(sb, rootStep, 0);
        return sb.toString();
    }

    private static void renderStep(StringBuilder sb, ProofStep step, int depth) {
        String pad = "  ".repeat(depth);
        String badge = switch (step.status()) {
            case QED -> "[Q.E.D.]";
            case REFUTED -> "[REFUTED]";
            case INCOMPLETE -> "[INCOMPLETE]";
        };
        sb.append(pad).append(badge).append(" lemma ").append(step.lemmaName())
                .append(" : ").append(step.proposition()).append('\n');
        sb.append(pad).append("  by tactic ").append(step.tacticName())
                .append(" (stratum=").append(step.stratum()).append(")\n");
        sb.append(pad).append("  -- ").append(step.summary()).append('\n');
        for (String w : step.witnesses()) {
            sb.append(pad).append("  witness: ").append(w).append('\n');
        }
        for (ProofStep child : step.children()) {
            renderStep(sb, child, depth + 1);
        }
    }

    public String formatVersion() { return formatVersion; }
    public String specId() { return specId; }
    public String specTitle() { return specTitle; }
    public String targetPath() { return targetPath; }
    public String timestampUtc() { return timestampUtc; }
    public Map<String, String> sourceSha256Manifest() { return sourceSha256Manifest; }
    public Map<String, String> discoveredBindings() { return discoveredBindings; }
    public Map<String, Object> searchTelemetry() { return searchTelemetry; }
    public ProofStep rootStep() { return rootStep; }
    public String certificateHash() { return certificateHash; }
}
