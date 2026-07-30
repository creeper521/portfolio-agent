package com.portfolio.agent.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class PublicReleaseActivationServiceTest {

    @Test
    void publishesAndUpsertsTheActiveReleaseInOneTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = immediateTransactions();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PublicReleaseActivationService service = new PublicReleaseActivationService(jdbcTemplate, transactions);

        PublicReleaseActivationResult result = service.activate("5d0dc8b2-2cb0-4e87-b4b3-29f8f4c22564");

        assertThat(result.getReleaseStatus()).isEqualTo("PUBLISHED");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
                .contains("WHERE release_id = CAST(? AS uuid)")
                .contains("AND status = 'VERIFIED'")
                .contains("INSERT INTO active_release")
                .contains("ON CONFLICT (singleton) DO UPDATE");
        assertThat(parameters.getValue()).containsExactly("5d0dc8b2-2cb0-4e87-b4b3-29f8f4c22564");
    }

    @Test
    void refusesNonVerifiedReleaseWithoutChangingTheExistingActiveRelease() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TransactionOperations transactions = immediateTransactions();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        PublicReleaseActivationService service = new PublicReleaseActivationService(jdbcTemplate, transactions);

        assertThatThrownBy(() -> service.activate("5d0dc8b2-2cb0-4e87-b4b3-29f8f4c22564"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("FROM verified_release");
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private TransactionOperations immediateTransactions() {
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }
}
