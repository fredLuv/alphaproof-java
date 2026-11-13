package dev.oopproof.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable node in a formal proof tree. Each step records the lemma statement,
 * tactic applied, verification status, concrete evidence / counterexample, and a
 * SHA-256 Merkle hash chaining its content to all child proof steps.
 */
public final class ProofStep {

    private final String id;
    private final String lemmaName;
    private final String stratum;
    private final String tacticName;
    private final String proposition;
    private final ProofStatus status;
    private final String summary;
    private final Map<String, Object> evidence;
    private final List<String> witnesses;
    private final List<ProofStep> children;
    private final String stepHash;

    public ProofStep(
            String id,
            String lemmaName,
            String stratum,
            String tacticName,
            String proposition,
            ProofStatus status,
            String summary,
            Map<String, Object> evidence,
            List<String> witnesses,
            List<ProofStep> children) {
        this.id = Objects.requireNonNull(id);
        this.lemmaName = Objects.requireNonNull(lemmaName);
        this.stratum = Objects.requireNonNull(stratum);
        this.tacticName = Objects.requireNonNull(tacticName);
        this.proposition = Objects.requireNonNull(proposition);
        this.status = Objects.requireNonNull(status);
        this.summary = Objects.requireNonNull(summary);
        this.evidence = evidence == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        this.witnesses = witnesses == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(witnesses));
        this.children = children == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(children));
        this.stepHash = computeHash();
    }

    private ProofStep(
            String id,
            String lemmaName,
            String stratum,
            String tacticName,
            String proposition,
            ProofStatus status,
            String summary,
            Map<String, Object> evidence,
            List<String> witnesses,
            List<ProofStep> children,
            String stepHash) {
        this.id = id;
        this.lemmaName = lemmaName;
        this.stratum = stratum;
        this.tacticName = tacticName;
        this.proposition = proposition;
        this.status = status;
        this.summary = summary;
        this.evidence = evidence;
        this.witnesses = witnesses;
        this.children = children;
        this.stepHash = stepHash;
    }

    public String computeHash() {
        StringBuilder canonical = new StringBuilder();
        canonical.append(id).append('|')
                .append(lemmaName).append('|')
                .append(stratum).append('|')
                .append(tacticName).append('|')
                .append(proposition).append('|')
                .append(status.name()).append('|')
                .append(summary).append('|')
                .append(JsonUtil.stringify(evidence)).append('|')
                .append(String.join(",", witnesses)).append('|');
        for (ProofStep child : children) {
            canonical.append(child.stepHash()).append(';');
        }
        return sha256Hex(canonical.toString());
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("lemmaName", lemmaName);
        m.put("stratum", stratum);
        m.put("tacticName", tacticName);
        m.put("proposition", proposition);
        m.put("status", status.name());
        m.put("summary", summary);
        m.put("evidence", evidence);
        m.put("witnesses", witnesses);
        List<Map<String, Object>> childMaps = new ArrayList<>();
        for (ProofStep c : children) {
            childMaps.add(c.toMap());
        }
        m.put("children", childMaps);
        m.put("stepHash", stepHash);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ProofStep fromMap(Map<String, Object> m) {
        String id = (String) m.get("id");
        String lemmaName = (String) m.get("lemmaName");
        String stratum = (String) m.get("stratum");
        String tacticName = (String) m.get("tacticName");
        String proposition = (String) m.get("proposition");
        ProofStatus status = ProofStatus.valueOf((String) m.get("status"));
        String summary = (String) m.get("summary");
        Map<String, Object> evidence = m.get("evidence") instanceof Map<?, ?> raw
                ? Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) raw))
                : Map.of();
        List<String> witnesses = new ArrayList<>();
        if (m.get("witnesses") instanceof List<?> rawList) {
            for (Object o : rawList) {
                witnesses.add(String.valueOf(o));
            }
        }
        List<ProofStep> children = new ArrayList<>();
        if (m.get("children") instanceof List<?> rawChildren) {
            for (Object c : rawChildren) {
                children.add(fromMap((Map<String, Object>) c));
            }
        }
        String stepHash = (String) m.get("stepHash");
        return new ProofStep(
                id, lemmaName, stratum, tacticName, proposition,
                status, summary, evidence,
                Collections.unmodifiableList(witnesses),
                Collections.unmodifiableList(children),
                stepHash);
    }

    public String id() { return id; }
    public String lemmaName() { return lemmaName; }
    public String stratum() { return stratum; }
    public String tacticName() { return tacticName; }
    public String proposition() { return proposition; }
    public ProofStatus status() { return status; }
    public String summary() { return summary; }
    public Map<String, Object> evidence() { return evidence; }
    public List<String> witnesses() { return witnesses; }
    public List<ProofStep> children() { return children; }
    public String stepHash() { return stepHash; }
}
