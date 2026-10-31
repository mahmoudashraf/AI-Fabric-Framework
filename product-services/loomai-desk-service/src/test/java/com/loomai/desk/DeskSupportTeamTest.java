package com.loomai.desk;

import static com.loomai.desk.DeskIsolationTest.*;
import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.service.DeskDirectory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DeskSupportTeamTest {
  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void projectsOnlyRegisteredSupportAssigneesWithBindingScopedReferences(String db) throws Exception {
    try (var database = new DeskTestDatabase(db)) {
      var jdbc = new JdbcTemplate(database.dataSource);
      var directory = new DeskDirectory(jdbc, properties());
      workspace(directory, "a", "owner-a");
      workspace(directory, "b", "owner-b");
      directory.putBinding("a", binding("a", "http://127.0.0.1:9999/a"));
      directory.putBinding("b", binding("b", "http://127.0.0.1:9999/b"));
      var ownerA = directory.register(identity("owner-a"));
      var ownerB = directory.register(identity("owner-b"));
      var sam = directory.register(identity("sam"));
      var noPhoto = directory.register(identity("no-photo"));
      var reviewer = directory.register(identity("reviewer"));
      var inactive = directory.register(identity("inactive"));
      directory.updateProfile(sam, new ProfileInput("Sam Support", DeskProfileTest.photo(80, 80), false));
      directory.updateProfile(noPhoto, new ProfileInput("no-photo@example.test", null, false));
      directory.assign("a", ownerA, new AssignmentInput(null, sam.id(), Set.of(Role.SUPPORT, Role.REVIEWER)));
      directory.assign("a", ownerA, new AssignmentInput(null, noPhoto.id(), Set.of(Role.SUPPORT)));
      directory.assign("a", ownerA, new AssignmentInput(null, reviewer.id(), Set.of(Role.REVIEWER)));
      directory.assign("a", ownerA, new AssignmentInput(null, inactive.id(), Set.of(Role.SUPPORT)));
      directory.assign("b", ownerB, new AssignmentInput(null, sam.id(), Set.of(Role.SUPPORT)));
      jdbc.update("UPDATE desk_operator SET registered=FALSE WHERE operator_id=?", inactive.id());

      var team = directory.supportTeam("a");
      assertThat(team).hasSize(2);
      var samProfile = team.stream().filter(p -> p.displayName().equals("Sam Support")).findFirst().orElseThrow();
      assertThat(samProfile.profileRef()).matches("team-[a-f0-9]{64}");
      assertThat(samProfile.photoAvailable()).isTrue();
      assertThat(team).anySatisfy(p -> {
        assertThat(p.displayName()).isEqualTo("Support agent");
        assertThat(p.photoAvailable()).isFalse();
        missing(() -> directory.supportTeamPhoto("a", p.profileRef()));
      });
      String json = JSON.writeValueAsString(team);
      assertThat(json).doesNotContain("email", "subject", "issuer", "roles", "avatarUrl", "online", "@example.test", sam.id(), ownerA.id());
      assertThat(directory.supportTeamPhoto("a", samProfile.profileRef())).containsExactly(directory.photo(sam, sam.id()));
      String otherReference = directory.supportTeam("b").getFirst().profileRef();
      assertThat(otherReference).isNotEqualTo(samProfile.profileRef());
      missing(() -> directory.supportTeamPhoto("a", otherReference));
      missing(() -> directory.supportTeamPhoto("b", samProfile.profileRef()));
      missing(() -> directory.supportTeamPhoto("a", sam.id()));

      directory.updateProfile(sam, new ProfileInput("Sam Renamed", null, false));
      assertThat(directory.supportTeam("a")).anySatisfy(p -> {
        assertThat(p.profileRef()).isEqualTo(samProfile.profileRef());
        assertThat(p.displayName()).isEqualTo("Sam Renamed");
      });
      directory.assign("a", ownerA, new AssignmentInput(null, sam.id(), Set.of(Role.REVIEWER)));
      missing(() -> directory.supportTeamPhoto("a", samProfile.profileRef()));
      assertThat(directory.supportTeam("a")).noneMatch(p -> p.profileRef().equals(samProfile.profileRef()));
      directory.revoke("a", ownerA, noPhoto.id());
      assertThat(directory.supportTeam("a")).isEmpty();
      jdbc.update("UPDATE desk_binding SET enabled=FALSE WHERE binding_id='b'");
      missing(() -> directory.supportTeam("b"));
      missing(() -> directory.supportTeamPhoto("b", otherReference));
      missing(() -> directory.supportTeam("unknown"));
    }
  }

  private void missing(ThrowingCallable action) {
    assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
  }
}
