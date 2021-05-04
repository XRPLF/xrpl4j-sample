package org.xrpl.xrpl4j.samples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import okhttp3.HttpUrl;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.crypto.KeyMetadata;
import org.xrpl.xrpl4j.crypto.PrivateKey;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.SingleKeySignatureService;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.immutables.FluentCompareTo;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.Transaction;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory;
import org.xrpl.xrpl4j.wallet.Wallet;
import org.xrpl.xrpl4j.wallet.WalletFactory;

import java.math.BigDecimal;

public class SendXrp {

  public static void main(String[] args) throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
    System.out.println("Running the SendXrp sample...");

    // Construct a network client
    final HttpUrl rippledUrl = HttpUrl.get("https://s.altnet.rippletest.net:51234/");
    XrplClient xrplClient = new XrplClient(rippledUrl);

    // Create a Wallet using a WalletFactory
    WalletFactory walletFactory = DefaultWalletFactory.getInstance();
    final Wallet testWallet = walletFactory.randomWallet(true).wallet();
    System.out.println("Generated a wallet with the following public key: " + testWallet.publicKey());

    // Get the Classic and X-Addresses from testWallet
    final Address classicAddress = testWallet.classicAddress();
    System.out.println("Classic Address: " + classicAddress);

    // Fund the account using the testnet Faucet
    final FaucetClient faucetClient = FaucetClient.construct(HttpUrl.get("https://faucet.altnet.rippletest.net"));
    faucetClient.fundAccount(FundAccountRequest.of(classicAddress));
    System.out.println("Funded the account using the Testnet faucet.");

    // Wait for the Faucet Payment to get validated
    Thread.sleep(4 * 1000);

    // Look up your Account Info
    final AccountInfoRequestParams requestParams = AccountInfoRequestParams
      .builder().ledgerIndex(LedgerIndex.VALIDATED)
      .account(classicAddress)
      .build();
    final AccountInfoResult accountInfoResult = xrplClient.accountInfo(requestParams);
    final UnsignedInteger sequence = accountInfoResult.accountData().sequence();

    // Request current fee information from rippled
    final FeeResult feeResult = xrplClient.fee();
    final XrpCurrencyAmount openLedgerFee = feeResult.drops().openLedgerFee();

    // Construct a Payment
    // Workaround for https://github.com/XRPLF/xrpl4j/issues/84
    final LedgerIndex validatedLedger = xrplClient.ledger(LedgerRequestParams.builder().ledgerIndex(LedgerIndex.VALIDATED).build())
      .ledgerIndex()
      .orElseThrow(() -> new RuntimeException("LedgerIndex not available."));

    final UnsignedInteger lastLedgerSequence = UnsignedInteger.valueOf(
      validatedLedger.plus(UnsignedLong.valueOf(4)).unsignedLongValue().intValue()
    ); // <-- LastLedgerSequence is the current ledger index + 4

    Payment payment = Payment.builder()
      .account(classicAddress)
      .amount(XrpCurrencyAmount.ofXrp(BigDecimal.ONE))
      .destination(Address.of("rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"))
      .sequence(sequence)
      .fee(openLedgerFee)
      .signingPublicKey(testWallet.publicKey())
      .lastLedgerSequence(lastLedgerSequence)
      .build();
    System.out.println("Constructed Payment: " + payment);

    // Construct a SignatureService to sign the Payment
    PrivateKey privateKey = PrivateKey.fromBase16EncodedPrivateKey(testWallet.privateKey().get());
    SignatureService signatureService = new SingleKeySignatureService(privateKey);

    // Sign the Payment
    final SignedTransaction<Payment> signedPayment = signatureService.sign(KeyMetadata.EMPTY, payment);
    System.out.println("Signed Payment: " + signedPayment.signedTransaction());

    // Submit the Payment
    final SubmitResult<Transaction> submitResult = xrplClient.submit(signedPayment);
    System.out.println(submitResult);

    // Wait for validation
    TransactionResult<Payment> transactionResult = null;

    boolean transactionValidated = false;
    boolean transactionExpired = false;
    while (!transactionValidated && !transactionExpired) {
      Thread.sleep(4 * 1000);
      final LedgerIndex latestValidatedLedgerIndex = xrplClient.ledger(
        LedgerRequestParams.builder().ledgerIndex(LedgerIndex.VALIDATED).build()
      )
        .ledgerIndex()
        .orElseThrow(() -> new RuntimeException("Ledger response did not contain a LedgerIndex."));

      transactionResult = xrplClient.transaction(
        TransactionRequestParams.of(signedPayment.hash()),
        Payment.class
      );

      if (transactionResult.validated()) {
        System.out.println("Payment was validated with result code " + transactionResult.metadata().get().transactionResult());
        transactionValidated = true;
      } else {
        final boolean lastLedgerSequenceHasPassed = FluentCompareTo.
          is(latestValidatedLedgerIndex.unsignedLongValue())
          .greaterThan(UnsignedLong.valueOf(lastLedgerSequence.intValue()));
        if (lastLedgerSequenceHasPassed) {
          System.out.println("Payment was never validated and has expired.");
          transactionExpired = true;
        } else {
          System.out.println("Payment not yet validated.");
        }
      }
    }

    // Check transaction results
    System.out.println(transactionResult);
    System.out.println("Explorer link: https://testnet.xrpl.org/transactions/" + signedPayment.hash());
    transactionResult.metadata().ifPresent(metadata -> {
      System.out.println("Result code: " + metadata.transactionResult());

      metadata.deliveredAmount().ifPresent(deliveredAmount ->
        System.out.println("XRP Delivered: " + ((XrpCurrencyAmount) deliveredAmount).toXrp())
      );
    });
  }
}
