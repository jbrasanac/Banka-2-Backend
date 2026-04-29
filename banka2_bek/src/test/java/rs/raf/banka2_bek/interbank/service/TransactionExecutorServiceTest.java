package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionExecutorService (§2.8 2PC coordinator).
 * The `self` proxy is replaced with a Mockito mock via ReflectionTestUtils
 * so @Transactional boundaries are exercised without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransactionExecutorServiceTest {

    @Mock
    private InterbankMessageService messageService;
    @Mock
    private InterbankClient client;
    @Mock
    private BankRoutingService routing;
    @Mock
    private InterbankTransactionRepository txRepo;

    /** Mock for the self-proxy; injected via ReflectionTestUtils. */
    @Mock
    private TransactionExecutorService self;

    private TransactionExecutorService service;
    private ObjectMapper objectMapper;

    private static final int MY_RN = 222;
    private static final int REMOTE_RN = 111;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new TransactionExecutorService(messageService, client, routing, txRepo, objectMapper);
        ReflectionTestUtils.setField(service, "self", self);

        when(routing.myRoutingNumber()).thenReturn(MY_RN);
    }

    // -------------------------------------------------------------------------
    // execute — fully local transactions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("execute with local-only postings: YES vote → commitLocal called")
    void execute_localOnly_yesVote_commitsLocal() {
        Transaction tx = localTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());

        service.execute(tx);

        verify(self).prepareLocal(tx);
        verify(self).commitLocal(tx.transactionId());
        verify(self, never()).rollbackLocal(any());
        verifyNoInteractions(messageService, client);
    }

    @Test
    @DisplayName("execute with local-only postings: NO vote → rollbackLocal called")
    void execute_localOnly_noVote_rollsBackLocal() {
        Transaction tx = localTransaction();
        when(self.prepareLocal(tx)).thenReturn(noVote());

        service.execute(tx);

        verify(self).prepareLocal(tx);
        verify(self).rollbackLocal(tx.transactionId());
        verify(self, never()).commitLocal(any());
        verifyNoInteractions(messageService, client);
    }

    // -------------------------------------------------------------------------
    // execute — coordinator with remote banks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("execute as coordinator: myVote=YES + all remotes YES → commitLocal + COMMIT_TX sent")
    void execute_coordinator_allYes_commitsAndSendsCommit() throws Exception {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());
        when(messageService.generateKey()).thenReturn(new IdempotenceKey(MY_RN, "key-abc"));
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(yesVote());
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).commitLocal(tx.transactionId());
        verify(self, never()).rollbackLocal(any());
        verify(client).sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class));
    }

    @Test
    @DisplayName("execute as coordinator: myVote=YES + remote votes NO → rollbackLocal + ROLLBACK_TX sent")
    void execute_coordinator_remoteNo_rollsBackAndSendsRollback() throws Exception {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());
        when(messageService.generateKey()).thenReturn(new IdempotenceKey(MY_RN, "key-abc"));
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(noVote());
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).rollbackLocal(tx.transactionId());
        verify(self, never()).commitLocal(any());
        verify(client).sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class));
    }

    @Test
    @DisplayName("execute as coordinator: myVote=NO → no messages sent, no remote calls")
    void execute_coordinator_myVoteNo_noMessagesNoRemote() {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(noVote());

        service.execute(tx);

        verify(self, never()).commitLocal(any());
        verify(self, never()).rollbackLocal(any());
        verifyNoInteractions(messageService, client);
    }

    @Test
    @DisplayName("execute as coordinator: remote returns null (202) → treated as NO → rollback")
    void execute_coordinator_remote202_treatedAsNo_rollsBack() throws Exception {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());
        when(messageService.generateKey()).thenReturn(new IdempotenceKey(MY_RN, "key-xyz"));
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);
        // null return = 202 Accepted
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(null);
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).rollbackLocal(tx.transactionId());
        verify(messageService).markOutboundSent(any(), eq(202), isNull());
    }

    @Test
    @DisplayName("execute as coordinator: communication exception from remote → treated as NO → rollback")
    void execute_coordinator_communicationException_treatedAsNo_rollsBack() throws Exception {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());
        when(messageService.generateKey()).thenReturn(new IdempotenceKey(MY_RN, "key-err"));
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("timeout"));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).rollbackLocal(tx.transactionId());
        verify(messageService).markOutboundFailed(any(), contains("timeout"));
    }

    @Test
    @DisplayName("execute as coordinator: saves INITIATOR + PREPARING coordinator state")
    void execute_coordinator_savesCoordinatorState() throws Exception {
        Transaction tx = mixedTransaction();
        when(self.prepareLocal(tx)).thenReturn(yesVote());
        when(messageService.generateKey()).thenReturn(new IdempotenceKey(MY_RN, "key-cs"));
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(yesVote());
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        ArgumentCaptor<InterbankTransaction> captor = ArgumentCaptor.forClass(InterbankTransaction.class);
        verify(txRepo).save(captor.capture());
        InterbankTransaction saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo(InterbankTransaction.InterbankTransactionRole.INITIATOR);
        assertThat(saved.getStatus()).isEqualTo(InterbankTransactionStatus.PREPARING);
        assertThat(saved.getTransactionRoutingNumber()).isEqualTo(MY_RN);
        assertThat(saved.getTransactionIdString()).isEqualTo(tx.transactionId().id());
        assertThat(saved.getTransactionBody()).isNotBlank();
        assertThat(saved.getRetryCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Stub methods — verify UnsupportedOperationException while TODO
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("formTransaction throws UnsupportedOperationException (TODO §2.8.3)")
    void formTransaction_throws() {
        assertThatThrownBy(() -> service.formTransaction())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("prepareLocal throws UnsupportedOperationException (TODO §2.8.4)")
    void prepareLocal_throws() {
        assertThatThrownBy(() -> service.prepareLocal(localTransaction()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("commitLocal throws UnsupportedOperationException (TODO §2.8.4)")
    void commitLocal_throws() {
        assertThatThrownBy(() -> service.commitLocal(new ForeignBankId(MY_RN, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rollbackLocal throws UnsupportedOperationException (TODO §2.8.7)")
    void rollbackLocal_throws() {
        assertThatThrownBy(() -> service.rollbackLocal(new ForeignBankId(MY_RN, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("handleNewTx throws UnsupportedOperationException (TODO §2.12.1)")
    void handleNewTx_throws() {
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k");
        assertThatThrownBy(() -> service.handleNewTx(localTransaction(), key))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("handleCommitTx throws UnsupportedOperationException (TODO §2.12.2)")
    void handleCommitTx_throws() {
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k");
        assertThatThrownBy(() -> service.handleCommitTx(
                new CommitTransaction(new ForeignBankId(REMOTE_RN, "x")), key))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("handleRollbackTx throws UnsupportedOperationException (TODO §2.12.3)")
    void handleRollbackTx_throws() {
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k");
        assertThatThrownBy(() -> service.handleRollbackTx(
                new RollbackTransaction(new ForeignBankId(REMOTE_RN, "x")), key))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Transaction with only local accounts (all routing == MY_RN). */
    private Transaction localTransaction() {
        ForeignBankId txId = new ForeignBankId(MY_RN, "local-tx-1");
        Posting debit = new Posting(
                new TxAccount.Account(MY_RN + "123456"),
                BigDecimal.valueOf(100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)));
        Posting credit = new Posting(
                new TxAccount.Account(MY_RN + "789012"),
                BigDecimal.valueOf(-100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)));
        return new Transaction(List.of(debit, credit), txId, null, null, null, null);
    }

    /** Transaction with one local and one remote account. */
    private Transaction mixedTransaction() {
        ForeignBankId txId = new ForeignBankId(MY_RN, "mixed-tx-1");
        Posting localDebit = new Posting(
                new TxAccount.Account(MY_RN + "999001"),
                BigDecimal.valueOf(100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR)));
        Posting remoteCredit = new Posting(
                new TxAccount.Account(REMOTE_RN + "999002"),
                BigDecimal.valueOf(-100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR)));
        return new Transaction(List.of(localDebit, remoteCredit), txId, null, null, null, null);
    }

    private TransactionVote yesVote() {
        return new TransactionVote(TransactionVote.Vote.YES, List.of());
    }

    private TransactionVote noVote() {
        return new TransactionVote(TransactionVote.Vote.NO,
                List.of(new NoVoteReason(NoVoteReason.Reason.INSUFFICIENT_ASSET, null)));
    }
}
