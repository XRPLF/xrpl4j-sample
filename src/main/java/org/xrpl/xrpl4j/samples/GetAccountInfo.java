package org.xrpl.xrpl4j.samples;

import okhttp3.HttpUrl;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.XAddress;
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory;
import org.xrpl.xrpl4j.wallet.Wallet;
import org.xrpl.xrpl4j.wallet.WalletFactory;

public class GetAccountInfo {

  public static void main(String[] args) throws JsonRpcClientErrorException {
    System.out.println("Running the GetAccountInfo sample...");

    // Construct a network client
    final HttpUrl rippledUrl = HttpUrl.get("https://s.altnet.rippletest.net:51234/");
    System.out.println("Constructing an XrplClient connected to " + rippledUrl);
    XrplClient xrplClient = new XrplClient(rippledUrl);

    // Create a Wallet using a WalletFactory
    WalletFactory walletFactory = DefaultWalletFactory.getInstance();
    final Wallet testWallet = walletFactory.randomWallet(true).wallet();
    System.out.println("Generated a wallet with the following public key: " + testWallet.publicKey());

    // Get the Classic and X-Addresses from testWallet
    final Address classicAddress = testWallet.classicAddress();
    final XAddress xAddress = testWallet.xAddress();
    System.out.println("Classic Address: " + classicAddress);
    System.out.println("X-Address: " + xAddress);

    // Fund the account using the testnet Faucet
    final FaucetClient faucetClient = FaucetClient.construct(HttpUrl.get("https://faucet.altnet.rippletest.net"));
    faucetClient.fundAccount(FundAccountRequest.of(classicAddress));
    System.out.println("Funded the account using the Testnet faucet.");

    // Look up your Account Info
    final AccountInfoRequestParams requestParams = AccountInfoRequestParams.of(classicAddress);
    final AccountInfoResult accountInfoResult = xrplClient.accountInfo(requestParams);
    System.out.println(accountInfoResult);
  }

}
