package com.wallaceespindola.dbmigration.service;

import com.wallaceespindola.dbmigration.domain.MigrationEngine;
import com.wallaceespindola.dbmigration.dto.AppliedMigration;
import com.wallaceespindola.dbmigration.dto.MigrationStatusReport;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Service;

/**
 * Reads Flyway's migration history through Flyway's own {@code info()} API.
 *
 * <p>No SQL is needed: {@link Flyway#info()} returns both applied and pending migrations, already
 * ordered, with state and checksum attached. That first-class status API is one of Flyway's
 * practical advantages over Liquibase when embedding the engine in an application.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlywayHistoryService implements MigrationHistoryProvider {

    /** Flyway does not record who authored a migration; Liquibase requires it. */
    private static final String NO_AUTHOR = "n/a";

    private final Flyway flyway;

    @Override
    public MigrationEngine engine() {
        return MigrationEngine.FLYWAY;
    }

    @Override
    public MigrationStatusReport status() {
        MigrationInfo[] all = flyway.info().all();
        log.debug("Flyway reported {} known migrations", all.length);

        List<AppliedMigration> applied = Arrays.stream(all)
                .filter(info -> info.getInstalledOn() != null)
                .map(FlywayHistoryService::toAppliedMigration)
                .sorted(Comparator.comparing(AppliedMigration::appliedAt))
                .toList();

        int pending = (int) Arrays.stream(all).filter(info -> info.getInstalledOn() == null).count();

        Instant lastApplied = applied.isEmpty() ? null : applied.getLast().appliedAt();

        return new MigrationStatusReport(
                MigrationEngine.FLYWAY,
                MigrationEngine.FLYWAY.displayName(),
                MigrationEngine.FLYWAY.historyTable(),
                applied.size(),
                pending,
                lastApplied,
                pending == 0,
                applied);
    }

    private static AppliedMigration toAppliedMigration(MigrationInfo info) {
        // Repeatable migrations have a null version — they are identified by description instead.
        String identifier = info.getVersion() != null
                ? "V" + info.getVersion()
                : "R__" + info.getDescription();

        return new AppliedMigration(
                identifier,
                info.getDescription(),
                info.getScript(),
                info.getType().name(),
                NO_AUTHOR,
                Objects.toString(info.getChecksum(), null),
                info.getInstalledOn().toInstant(),
                info.getExecutionTime() < 0 ? null : (long) info.getExecutionTime(),
                info.getState().getDisplayName());
    }
}
