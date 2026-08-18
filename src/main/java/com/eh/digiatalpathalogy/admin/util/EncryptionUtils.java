package com.eh.digiatalpathalogy.admin.util;

import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.google.cloud.ServiceOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class EncryptionUtils {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtils.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // 96 bits (recommended)
    private static final int TAG_LENGTH_BITS = 128;   // Authentication tag
    private static final int AES_KEY_BYTES = 16;      // AES-128
    private static final String MASKED_VALUE = "************";

    private EncryptionUtils() {
    }

    public static String mask(String ignored) {
        return MASKED_VALUE;
    }

    public static String encrypt(String plainText) {

        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(ServiceOptions.getDefaultProjectId()), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + cipherText.length)
                            .put(iv)
                            .put(cipherText)
                            .array()
                    );
        } catch (GeneralSecurityException ex) {
            log.error("Encryption failed due to cryptographic error", ex);
            throw new InternalServerException("Failed to encrypt data", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during encryption", ex);
            throw new InternalServerException("Unexpected encryption failure", ex);
        }


    }

    public static String decrypt(String encryptedText) {

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(ServiceOptions.getDefaultProjectId()), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            log.error("Decryption failed due to cryptographic error", ex);
            throw new InternalServerException("Failed to decrypt data", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during decryption", ex);
            throw new InternalServerException("Unexpected decryption failure", ex);
        }

    }

    private static SecretKeySpec deriveKey(String secret) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(ByteBuffer.wrap(hash, 0, AES_KEY_BYTES).array(), "AES");
    }
}