package org.xrpl.xrpl4j.samples;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.Transaction;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory;
import org.xrpl.xrpl4j.wallet.Wallet;
import org.xrpl.xrpl4j.wallet.WalletFactory;

import java.math.BigDecimal;

public class SendPayment {

  public static void main(String[] args) throws JsonRpcClientErrorException, JsonProcessingException {
    System.out.println("Running the SendPayment sample...");

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

    // Look up your Account Info
    final AccountInfoRequestParams requestParams = AccountInfoRequestParams.of(classicAddress);
    final AccountInfoResult accountInfoResult = xrplClient.accountInfo(requestParams);

    // Request current fee information from rippled
    final FeeResult feeResult = xrplClient.fee();

    // Construct a Payment
    Payment payment = Payment.builder()
      .account(classicAddress)
      .amount(XrpCurrencyAmount.ofXrp(BigDecimal.ONE))
      .destination(Address.of("rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"))
      .sequence(accountInfoResult.accountData().sequence())
      .fee(feeResult.drops().openLedgerFee())
      .signingPublicKey(testWallet.publicKey())
      .build();

    // Print the Payment
    System.out.println("Constructed Payment: " + payment);

    // Construct a SignatureService to sign the Payment
    PrivateKey privateKey = PrivateKey.fromBase16EncodedPrivateKey(testWallet.privateKey().get());
    SignatureService signatureService = new SingleKeySignatureService(privateKey);

    // Sign the Payment
    final SignedTransaction<Payment> signedPayment = signatureService.sign(KeyMetadata.EMPTY, payment);
    System.out.println("Signed Payment: " + signedPayment.signedTransaction());

    // Submit the Payment
    final SubmitResult<Transaction> submitResult = xrplClient.submit(signedPayment);

    // Print the response
    System.out.println(submitResult);
  }
}
