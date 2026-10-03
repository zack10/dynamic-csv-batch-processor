package com.example.csvbatch.execution;

import com.example.csvbatch.normalization.CanonicalCodec;
import com.example.csvbatch.persistence.repository.StagedCommandExportRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class Artifacts {
    private final StagedCommandExportRepository commands;

    public Artifacts(StagedCommandExportRepository commands) {
        this.commands = commands;
    }

    public void publish(String submission, Path directory) throws Exception {
        Path csv = directory.resolve("canonical.csv.tmp");
        Path provenance = directory.resolve("provenance.csv.tmp");
        long count = 0;
        long cursor = 0;
        try (var out = Files.newBufferedWriter(csv); var trace = Files.newBufferedWriter(provenance)) {
            line(out, CanonicalCodec.HEADER);
            line(trace, "canonical_record,source_record,mapping_name");
            while (true) {
                var page = commands.findExportBatch(submission, cursor);
                if (page.isEmpty()) break;
                for (var row : page) {
                    var command = row.command();
                    line(out, CanonicalCodec.row(command));
                    line(trace, (++count) + "," + command.sourceRecord() + "," + CanonicalCodec.field(command.mappingName()));
                    cursor = row.id();
                }
            }
        }
        move(csv, directory.resolve("canonical.csv"));
        move(provenance, directory.resolve("provenance.csv"));
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("formatVersion", 1);
        manifest.put("submissionId", submission);
        manifest.put("commands", count);
        manifest.put("canonicalSha256", sha256(directory.resolve("canonical.csv")));
        manifest.put("provenanceSha256", sha256(directory.resolve("provenance.csv")));
        manifest.put("ready", true);
        writeJson(directory.resolve("manifest.json"), manifest); // Completion marker is always last.
    }

    public static void verify(Path directory, String submission) throws Exception {
        var node = JsonMapper.builder().build().readTree(Files.readString(directory.resolve("manifest.json")));
        if (node.path("formatVersion").asInt() != 1 || !node.path("ready").asBoolean()
                || !submission.equals(node.path("submissionId").asString())
                || !sha256(directory.resolve("canonical.csv")).equals(node.path("canonicalSha256").asString())
                || !sha256(directory.resolve("provenance.csv")).equals(node.path("provenanceSha256").asString())) {
            throw new IllegalArgumentException("Canonical artifact/manifest integrity check failed");
        }
    }

    public static String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void writeJson(Path path, Map<String, ?> values) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, CanonicalCodec.json(values) + "\n");
        move(tmp, path);
    }

    private static void move(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void line(BufferedWriter writer, String value) throws IOException {
        writer.write(value);
        writer.write('\n');
    }
}
