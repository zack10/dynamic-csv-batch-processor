package com.example.csvbatch.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone test-data generator; run its main method from the IDE or compiled test classes.
 */
public class GenerateCsv {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: GenerateCsv.java output.csv sizeMiB");
        Path path = Path.of(args[0]);
        long target = Math.multiplyExact(Long.parseLong(args[1]), 1024L * 1024L);
        if (target < 1) throw new IllegalArgumentException("Size must be positive");
        if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
        long bytes = 0, records = 0;
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            String header = "old_codes,replacement\n";
            writer.write(header);
            bytes += header.length();
            while (bytes < target) {
                String row = "old_" + records + ",new_" + records + "\n";
                writer.write(row);
                bytes += row.length();
                records++;
            }
        }
        System.out.printf("Generated %,d records, %,d bytes at %s%n", records, bytes, path.toAbsolutePath());
    }
}
