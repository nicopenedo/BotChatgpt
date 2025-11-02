package com.bottrading.web.api;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

  private final byte[] secret;

  public WebhookController(@Value("${webhook.provider.secret:change-me}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  @PostMapping("/provider")
  public ResponseEntity<Void> handle(
      @RequestHeader(value = "X-Signature", required = false) String signature,
      @RequestBody byte[] payload) {
    if (!StringUtils.hasText(signature) || !isValidSignature(signature, payload)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok().build();
  }

  private boolean isValidSignature(String signature, byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] expected = mac.doFinal(payload);
      byte[] provided = Hex.decodeHex(signature.toCharArray());
      return MessageDigest.isEqual(expected, provided);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return false;
    }
  }
}
