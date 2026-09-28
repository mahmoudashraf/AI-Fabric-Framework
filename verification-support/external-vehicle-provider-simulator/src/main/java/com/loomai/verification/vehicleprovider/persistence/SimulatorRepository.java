package com.loomai.verification.vehicleprovider.persistence;

import com.loomai.verification.vehicleprovider.model.SimulatorContracts.AccountState;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ActiveFault;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultMode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FixtureRun;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleInput;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SimulatorRepository {

    private final JdbcTemplate jdbc;

    public SimulatorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FixtureRun> currentRun() {
        return jdbc.query(
            "select fixture_version, reset_id, reset_at from simulator_run where singleton_id = 1",
            (rs, rowNum) -> new FixtureRun(
                rs.getString("fixture_version"),
                rs.getString("reset_id"),
                instant(rs, "reset_at")
            )
        ).stream().findFirst();
    }

    public List<AccountState> accountStates() {
        return jdbc.query(
            "select profile, account_id, source_version, event_sequence from simulator_account_state order by profile, account_id",
            (rs, rowNum) -> new AccountState(
                rs.getString("profile"),
                rs.getString("account_id"),
                rs.getLong("source_version"),
                rs.getLong("event_sequence")
            )
        );
    }

    @Transactional
    public FixtureRun reset(String fixtureVersion, String accountA, String accountB,
                            List<VehicleInput> vehiclesA, List<VehicleInput> vehiclesB) {
        jdbc.update("delete from simulator_fault");
        jdbc.update("delete from simulator_vehicle");
        jdbc.update("delete from simulator_account_state");
        jdbc.update("delete from simulator_run");

        Instant now = Instant.now();
        FixtureRun run = new FixtureRun(fixtureVersion, UUID.randomUUID().toString(), now);
        jdbc.update(
            "insert into simulator_run (singleton_id, fixture_version, reset_id, reset_at) values (1, ?, ?, ?)",
            run.fixtureVersion(), run.resetId(), Timestamp.from(now)
        );
        insertAccount(Profile.PROFILE_A, accountA, now);
        insertAccount(Profile.PROFILE_B, accountB, now);
        vehiclesA.forEach(vehicle -> insertVehicle(Profile.PROFILE_A, accountA, vehicle, 1, now));
        vehiclesB.forEach(vehicle -> insertVehicle(Profile.PROFILE_B, accountB, vehicle, 1, now));
        return run;
    }

    public List<VehicleRecord> vehicles(Profile profile, String accountId) {
        return jdbc.query(
            """
                select profile, account_id, vehicle_id, make_name, model_name, derivative,
                       registration_year, price_minor, currency, fuel_type, body_style,
                       transmission, mileage, lifecycle_state, record_version, updated_at
                  from simulator_vehicle
                 where profile = ? and account_id = ?
                 order by vehicle_id
                """,
            this::vehicle,
            profile.value(), accountId
        );
    }

    public Optional<VehicleRecord> vehicle(Profile profile, String accountId, String vehicleId) {
        return jdbc.query(
            """
                select profile, account_id, vehicle_id, make_name, model_name, derivative,
                       registration_year, price_minor, currency, fuel_type, body_style,
                       transmission, mileage, lifecycle_state, record_version, updated_at
                  from simulator_vehicle
                 where profile = ? and account_id = ? and vehicle_id = ?
                """,
            this::vehicle,
            profile.value(), accountId, vehicleId
        ).stream().findFirst();
    }

    public long sourceVersion(Profile profile, String accountId) {
        Long value = jdbc.queryForObject(
            "select source_version from simulator_account_state where profile = ? and account_id = ?",
            Long.class,
            profile.value(), accountId
        );
        if (value == null) {
            throw new IllegalStateException("Simulator account state is missing.");
        }
        return value;
    }

    @Transactional
    public long upsertVehicle(Profile profile, String accountId, VehicleInput input) {
        long nextVersion = incrementSourceVersion(profile, accountId);
        Instant now = Instant.now();
        int updated = jdbc.update(
            """
                update simulator_vehicle
                   set make_name = ?, model_name = ?, derivative = ?, registration_year = ?,
                       price_minor = ?, currency = ?, fuel_type = ?, body_style = ?,
                       transmission = ?, mileage = ?, lifecycle_state = ?, record_version = ?, updated_at = ?
                 where profile = ? and account_id = ? and vehicle_id = ?
                """,
            input.make(), input.model(), input.derivative(), input.year(), input.priceMinor(), input.currency(),
            input.fuelType(), input.bodyStyle(), input.transmission(), input.mileage(), input.state(), nextVersion,
            Timestamp.from(now), profile.value(), accountId, input.id()
        );
        if (updated == 0) {
            insertVehicle(profile, accountId, input, nextVersion, now);
        }
        return nextVersion;
    }

    @Transactional
    public long deleteVehicle(Profile profile, String accountId, String vehicleId, boolean purge) {
        VehicleRecord existing = vehicle(profile, accountId, vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle record does not exist."));
        long nextVersion = incrementSourceVersion(profile, accountId);
        if (purge) {
            jdbc.update(
                "delete from simulator_vehicle where profile = ? and account_id = ? and vehicle_id = ?",
                profile.value(), accountId, vehicleId
            );
        } else {
            jdbc.update(
                """
                    update simulator_vehicle
                       set lifecycle_state = 'deleted', record_version = ?, updated_at = ?
                     where profile = ? and account_id = ? and vehicle_id = ?
                    """,
                nextVersion, Timestamp.from(Instant.now()), profile.value(), accountId, existing.id()
            );
        }
        return nextVersion;
    }

    @Transactional
    public long nextEventSequence(Profile profile, String accountId) {
        jdbc.update(
            """
                update simulator_account_state
                   set event_sequence = event_sequence + 1, updated_at = ?
                 where profile = ? and account_id = ?
                """,
            Timestamp.from(Instant.now()), profile.value(), accountId
        );
        Long value = jdbc.queryForObject(
            "select event_sequence from simulator_account_state where profile = ? and account_id = ?",
            Long.class,
            profile.value(), accountId
        );
        return value == null ? 0 : value;
    }

    @Transactional
    public void setFault(Profile profile, String accountId, FaultMode mode, int remaining, int delayMs) {
        jdbc.update("delete from simulator_fault where profile = ? and account_id = ?", profile.value(), accountId);
        if (mode != FaultMode.NONE) {
            jdbc.update(
                """
                    insert into simulator_fault
                        (profile, account_id, fault_mode, remaining, delay_ms, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                profile.value(), accountId, mode.name(), remaining, delayMs, Timestamp.from(Instant.now())
            );
        }
    }

    @Transactional
    public Optional<ActiveFault> consumeFault(Profile profile, String accountId) {
        Optional<ActiveFault> fault = jdbc.query(
            "select fault_mode, remaining, delay_ms from simulator_fault where profile = ? and account_id = ?",
            (rs, rowNum) -> new ActiveFault(
                FaultMode.valueOf(rs.getString("fault_mode")),
                rs.getInt("remaining"),
                rs.getInt("delay_ms")
            ),
            profile.value(), accountId
        ).stream().findFirst();
        fault.ifPresent(active -> {
            if (active.remaining() <= 1) {
                jdbc.update("delete from simulator_fault where profile = ? and account_id = ?", profile.value(), accountId);
            } else {
                jdbc.update(
                    "update simulator_fault set remaining = remaining - 1, updated_at = ? where profile = ? and account_id = ?",
                    Timestamp.from(Instant.now()), profile.value(), accountId
                );
            }
        });
        return fault;
    }

    private void insertAccount(Profile profile, String accountId, Instant now) {
        jdbc.update(
            """
                insert into simulator_account_state
                    (profile, account_id, source_version, event_sequence, updated_at)
                values (?, ?, 1, 0, ?)
                """,
            profile.value(), accountId, Timestamp.from(now)
        );
    }

    private void insertVehicle(Profile profile, String accountId, VehicleInput input, long version, Instant now) {
        jdbc.update(
            """
                insert into simulator_vehicle
                    (profile, account_id, vehicle_id, make_name, model_name, derivative,
                     registration_year, price_minor, currency, fuel_type, body_style,
                     transmission, mileage, lifecycle_state, record_version, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            profile.value(), accountId, input.id(), input.make(), input.model(), input.derivative(), input.year(),
            input.priceMinor(), input.currency(), input.fuelType(), input.bodyStyle(), input.transmission(),
            input.mileage(), input.state(), version, Timestamp.from(now)
        );
    }

    private long incrementSourceVersion(Profile profile, String accountId) {
        int updated = jdbc.update(
            """
                update simulator_account_state
                   set source_version = source_version + 1, updated_at = ?
                 where profile = ? and account_id = ?
                """,
            Timestamp.from(Instant.now()), profile.value(), accountId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("Unknown simulator account.");
        }
        return sourceVersion(profile, accountId);
    }

    private VehicleRecord vehicle(ResultSet rs, int rowNum) throws SQLException {
        return new VehicleRecord(
            rs.getString("profile"),
            rs.getString("account_id"),
            rs.getString("vehicle_id"),
            rs.getString("make_name"),
            rs.getString("model_name"),
            rs.getString("derivative"),
            rs.getInt("registration_year"),
            rs.getLong("price_minor"),
            rs.getString("currency"),
            rs.getString("fuel_type"),
            rs.getString("body_style"),
            rs.getString("transmission"),
            rs.getInt("mileage"),
            rs.getString("lifecycle_state"),
            rs.getLong("record_version"),
            instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
