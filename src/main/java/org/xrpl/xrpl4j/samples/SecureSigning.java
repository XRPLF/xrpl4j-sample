package org.xrpl.xrpl4j.samples;

import com.google.common.primitives.UnsignedInteger;
import org.xrpl.xrpl4j.codec.addresses.KeyType;
import org.xrpl.xrpl4j.crypto.ServerSecret;
import org.xrpl.xrpl4j.crypto.keys.*;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.bc.BcDerivedKeySignatureService;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

public class SecureSigning {

  public static void main(String[] args) {
    signUsingSingleKeySignatureService();
    signUsingDerivedKeysSignatureService();
  }

  private static void signUsingSingleKeySignatureService() {
    KeyPair randomKeyPair = Seed.ed25519Seed().deriveKeyPair();
    System.out.println("Generated KeyPair: " + randomKeyPair);

    SignatureService<PrivateKey> signatureService = new BcSignatureService();
    Payment payment = constructPayment(randomKeyPair.publicKey());
    SingleSignedTransaction<Payment> signedPayment = signatureService.sign(randomKeyPair.privateKey(), payment);
    System.out.println("Signed Payment: " + signedPayment.signedTransaction());
  }

  private static void signUsingDerivedKeysSignatureService() {
    SignatureService<PrivateKeyReference> derivedKeySignatureService = new BcDerivedKeySignatureService(
      () -> ServerSecret.of("shh".getBytes())
    );

    PrivateKeyReference privateKeyReference = new PrivateKeyReference() {
      @Override
      public KeyType keyType() {
        return KeyType.ED25519;
      }

      @Override
      public String keyIdentifier() {
        return "sample-keypair";
      }
    };

    PublicKey publicKey = derivedKeySignatureService.derivePublicKey(privateKeyReference);
    Payment payment = constructPayment(publicKey);
    SingleSignedTransaction<Payment> signedPayment = derivedKeySignatureService.sign(privateKeyReference, payment);
    System.out.println("Signed Payment: " + signedPayment.signedTransaction());
  }

  private static Payment constructPayment(PublicKey publicKey) {
    return Payment.builder()
      .account(publicKey.deriveAddress())
      .destination(Address.of("rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"))
      .amount(XrpCurrencyAmount.ofDrops(1000))
      .fee(XrpCurrencyAmount.ofDrops(10))
      .sequence(UnsignedInteger.valueOf(16126889))
      .signingPublicKey(publicKey)
      .build();
  }
}
