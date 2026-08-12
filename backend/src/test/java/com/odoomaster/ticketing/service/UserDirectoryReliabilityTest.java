package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.iam.UserDirectory;
import com.odoomaster.ticketing.iam.internal.User;
import com.odoomaster.ticketing.iam.internal.UserDirectoryImpl;
import com.odoomaster.ticketing.iam.internal.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import com.odoomaster.ticketing.iam.internal.IamFixtures;

/**
 * Reliability tests for {@link UserDirectoryImpl} — the published iam identity API that lets
 * {@code feedback}/{@code notification} resolve users without touching the {@code User} entity.
 */
@ExtendWith(MockitoExtension.class)
class UserDirectoryReliabilityTest {

    @Mock UserRepository users;

    UserDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new UserDirectoryImpl(users);
    }

    @Test
    void find_mapsEntityToUserRef() {
        User u = IamFixtures.user(7L, "buyer@dede.test");
        when(users.findById(7L)).thenReturn(Optional.of(u));

        assertThat(directory.find(7L)).get().satisfies(ref -> {
            assertThat(ref.id()).isEqualTo(7L);
            assertThat(ref.email()).isEqualTo("buyer@dede.test");
        });
    }

    @Test
    void findByEmail_resolvesByAddress() {
        User u = IamFixtures.user(3L, "demo@dede.test");
        when(users.findByEmail("demo@dede.test")).thenReturn(Optional.of(u));

        assertThat(directory.findByEmail("demo@dede.test")).get()
                .extracting(UserDirectory.UserRef::id).isEqualTo(3L);
    }

    @Test
    void find_missingUser_returnsEmpty() {
        when(users.findById(404L)).thenReturn(Optional.empty());

        assertThat(directory.find(404L)).isEmpty();
    }
}
