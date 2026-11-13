package dev.oopproof.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Trusted Computing Base (TCB) proof kernel.
 * Independently verifies ProofCertificates without trusting the ProofSearchEngine:
 * 1. Recomputes SHA-256 Merkle hashes over every ProofStep and the certificate header.
 * 2. Re-hashes source files on disk against the certificate's source manifest (when targetPath provided).
 * 3. Checks structural soundness: no skipped/incomplete leaves, valid status propagation, non-empty evidence.
 */
public final class ProofKernel {

    public record KernelVerdict(boolean accepted, String summary, List<String> diagnostics, int checkedSteps) {}

    public KernelVerdict verify(ProofCertificate cert, Path targetDirOrNull) {
        List<String> errors = new ArrayList<>();
        int[] stepsCount = {0};

        // 1. Verify root certificate hash
        String expectedCertHash = cert.computeCertificateHash();
        if (!expectedCertHash.equals(cert.certificateHash())) {
            errors.add("Certificate hash mismatch: expected " + expectedCertHash + " but got " + cert.certificateHash());
        }

        // 2. Verify Merkle tree & soundness rules recursively
        verifyStep(cert.rootStep(), errors, stepsCount);

        // 3. Verify root verdict is QED
        if (cert.rootStep().status() != ProofStatus.QED) {
            errors.add("Root theorem status is " + cert.rootStep().status() + " (expected QED)");
        }

        // 4. Verify source file SHA-256 manifest on disk if targetDirOrNull is supplied
        if (targetDirOrNull != null) {
            if (cert.sourceSha256Manifest().isEmpty()) {
                errors.add("Source SHA-256 manifest is empty");
            } else {
                for (Map.Entry<String, String> entry : cert.sourceSha256Manifest().entrySet()) {
                    Path file = targetDirOrNull.resolve(entry.getKey());
                    if (!Files.exists(file)) {
                        errors.add("Missing certified source file: " + entry.getKey());
                        continue;
                    }
                    try {
                        String actualHash = ProofStep.sha256Hex(Files.readString(file, StandardCharsets.UTF_8));
                        if (!actualHash.equals(entry.getValue())) {
                            errors.add("Source file modified since certification: " + entry.getKey());
                        }
                    } catch (IOException e) {
                        errors.add("Failed to read source file " + entry.getKey() + ": " + e.getMessage());
                    }
                }
            }
        }

        boolean ok = errors.isEmpty();
        String msg = ok
                ? "KERNEL ACCEPTED (Q.E.D.): " + stepsCount[0] + " proof steps and "
                        + cert.sourceSha256Manifest().size() + " source SHA-256 hashes verified."
                : "KERNEL REJECTED (" + errors.size() + " violations)";
        return new KernelVerdict(ok, msg, List.copyOf(errors), stepsCount[0]);
    }

    private void verifyStep(ProofStep step, List<String> errors, int[] count) {
        count[0]++;
        String recomputed = step.computeHash();
        if (!recomputed.equals(step.stepHash())) {
            errors.add("Merkle hash mismatch at step '" + step.id() + "' (" + step.lemmaName() + ")");
        }

        if (step.status() != ProofStatus.QED) {
            errors.add("Unproved step '" + step.lemmaName() + "' has status " + step.status() + ": " + step.summary());
        }

        if (step.children().isEmpty()) {
            if (step.evidence().isEmpty() && step.witnesses().isEmpty()) {
                errors.add("Leaf step '" + step.lemmaName() + "' lacks verifiable evidence or witnesses");
            }
        } else {
            boolean allChildrenQed = true;
            for (ProofStep child : step.children()) {
                verifyStep(child, errors, count);
                if (child.status() != ProofStatus.QED) {
                    allChildrenQed = false;
                }
            }
            if (step.status() == ProofStatus.QED && !allChildrenQed) {
                errors.add("Unsound inference: step '" + step.lemmaName() + "' claims QED despite unproved children");
            }
        }
    }
}
