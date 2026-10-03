package com.example.csvbatch.normalization;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.Blueprint;
import com.example.csvbatch.blueprint.MappingPlan;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.nio.file.Path;
import java.util.List;

public final class SourceCommandReader implements ItemStreamReader<List<Command>> {
    private final Path input;
    private final Blueprint blueprint;
    private final AppProperties limits;
    private BoundedCsv csv;
    private MappingPlan plan;
    private long record;
    private int width;

    public SourceCommandReader(Path input, Blueprint blueprint, AppProperties limits) {
        this.input = input;
        this.blueprint = blueprint;
        this.limits = limits;
    }

    @Override
    public void open(ExecutionContext context) {
        try {
            csv = new BoundedCsv(input, blueprint.input(), limits);
            String[] header = blueprint.input().header() ? csv.next() : null;
            if (blueprint.input().header() && header == null) {
                throw new IllegalArgumentException("Missing CSV header");
            }
            width = header == null ? -1 : header.length;
            plan = new MappingPlan(blueprint, header, limits);
            record = context.getLong("source.records", 0L);
            for (long i = 0; i < record; i++) {
                if (csv.next() == null) {
                    throw new IllegalArgumentException("Input shortened since checkpoint");
                }
            }
        } catch (Exception ex) {
            close();
            throw new ItemStreamException("Cannot open source CSV", ex);
        }
    }

    @Override
    public List<Command> read() {
        String[] row = csv.next();
        if (row == null) return null;
        record++;
        if (width >= 0 && row.length != width) {
            throw new IllegalArgumentException("Wrong column count at source record " + record);
        }
        return plan.apply(row, record);
    }

    @Override
    public void update(ExecutionContext context) {
        context.putLong("source.records", record);
    }

    @Override
    public void close() {
        if (csv != null) {
            try {
                csv.close();
            } catch (Exception ex) {
                throw new ItemStreamException("Cannot close CSV", ex);
            } finally {
                csv = null;
            }
        }
    }
}
