package org.jetlinks.community.utils;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * 加密工具类，提供多种加密算法的封装，包括DES、AES和RSA。
 */
public class CryptoUtils {

    /**
     * 生成DES密钥。
     *
     * @param password 密码，用于生成密钥。
     * @return 返回生成的DES密钥。
     * @throws Exception 如果密钥生成失败则抛出异常。
     */
    private static Key generateDESKey(byte[] password) throws Exception {
        DESKeySpec dks = new DESKeySpec(password);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        return keyFactory.generateSecret(dks);
    }

    /**
     * 使用DES算法对数据进行加密。
     *
     * @param password 密码，用于生成DES密钥。
     * @param ivParameter 初始化向量。
     * @param data 需要加密的数据。
     * @return 返回加密后的数据。
     * @throws Exception 如果加密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] encryptDES(byte[] password, byte[] ivParameter, byte[] data) {
        Key secretKey = generateDESKey(password);
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        IvParameterSpec iv = new IvParameterSpec(ivParameter);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        return cipher.doFinal(data);
    }

    /**
     * 使用DES算法对数据进行解密。
     *
     * @param password 密码，用于生成DES密钥。
     * @param ivParameter 初始化向量。
     * @param data 需要解密的数据。
     * @return 返回解密后的数据。
     * @throws Exception 如果解密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] decryptDES(byte[] password, byte[] ivParameter, byte[] data) {
        Key secretKey = generateDESKey(password);
        Cipher cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
        IvParameterSpec iv = new IvParameterSpec(ivParameter);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        return cipher.doFinal(data);
    }

    /**
     * 使用AES算法在CBC模式下对数据进行加密。
     *
     * @param password 密码，用于生成AES密钥。
     * @param ivParameter 初始化向量。
     * @param data 需要加密的数据。
     * @return 返回加密后的数据。
     * @throws Exception 如果加密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] encryptAESCBC(byte[] password, byte[] ivParameter, byte[] data) {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        Key secretKey = new SecretKeySpec(fillBit(password, cipher.getBlockSize()), "AES");
        IvParameterSpec iv = new IvParameterSpec(fillBit(ivParameter, cipher.getBlockSize()));
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        return cipher.doFinal(data);
    }

    /**
     * 使用AES算法在CBC模式下对数据进行解密。
     *
     * @param password 密码，用于生成AES密钥。
     * @param ivParameter 初始化向量。
     * @param data 需要解密的数据。
     * @return 返回解密后的数据。
     * @throws Exception 如果解密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] decryptAESCBC(byte[] password, byte[] ivParameter, byte[] data) {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        Key secretKey = new SecretKeySpec(fillBit(password, cipher.getBlockSize()), "AES");
        IvParameterSpec iv = new IvParameterSpec(fillBit(ivParameter, cipher.getBlockSize()));
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        return cipher.doFinal(data);
    }

    /**
     * 使用AES算法在ECB模式下对数据进行加密。
     *
     * @param password 密码，用于生成AES密钥。
     * @param data 需要加密的数据。
     * @return 返回加密后的数据。
     * @throws Exception 如果加密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] encryptAESECB(byte[] password, byte[] data) {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        Key secretKey = new SecretKeySpec(fillBit(password, cipher.getBlockSize()), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(fillBit(data, cipher.getBlockSize()));
    }

    /**
     * 使用AES算法在ECB模式下对数据进行解密。
     *
     * @param password 密码，用于生成AES密钥。
     * @param data 需要解密的数据。
     * @return 返回解密后的数据。
     * @throws Exception 如果解密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] decryptAESECB(byte[] password, byte[] data) {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        Key secretKey = new SecretKeySpec(fillBit(password, cipher.getBlockSize()), "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decrypted = cipher.doFinal(data);
        if (decrypted[decrypted.length - 1] != 0x00) {
            return decrypted;
        }
        // 去除补位的0x00
        for (int i = decrypted.length - 1; i >= 0; i--) {
            if (decrypted[i] != 0x00) {
                return Arrays.copyOf(decrypted, i + 1);
            }
        }
        return decrypted;
    }

    /**
     * 对密码和初始化向量进行补位，以满足AES算法对数据长度的要求。
     *
     * @param data 需要补位的数据。
     * @param blockSize AES块大小。
     * @return 返回补位后的数据。
     */
    private static byte[] fillBit(byte[] data, int blockSize) {
        int len = (data.length / blockSize + (data.length % blockSize == 0 ? 0 : 1)) * 16;
        if (len == data.length) {
            return data;
        }
        return Arrays.copyOf(data, len);
    }

    /**
     * 生成RSA密钥对。
     *
     * @return 返回生成的RSA密钥对。
     * @throws Exception 如果密钥对生成失败则抛出异常。
     */
    @SneakyThrows
    public static KeyPair generateRSAKey() {
        KeyPairGenerator keyPairGen;
        keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(512);
        return keyPairGen.generateKeyPair();
    }

    /**
     * 使用RSA算法对数据进行解密。
     *
     * @param data 需要解密的数据。
     * @param key 解密所用的密钥。
     * @return 返回解密后的数据。
     * @throws Exception 如果解密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] decryptRSA(byte[] data, Key key) {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    /**
     * 使用RSA算法对数据进行加密。
     *
     * @param data 需要加密的数据。
     * @param key 加密所用的密钥。
     * @return 返回加密后的数据。
     * @throws Exception 如果加密过程出错则抛出异常。
     */
    @SneakyThrows
    public static byte[] encryptRSA(byte[] data, Key key) {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    /**
     * 从Base64编码的字符串中解析RSA公钥。
     *
     * @param base64 Base64编码的公钥字符串。
     * @return 返回解析后的RSA公钥。
     * @throws Exception 如果解析过程出错则抛出异常。
     */
    @SneakyThrows
    public static PublicKey decodeRSAPublicKey(String base64) {
        byte[] keyBytes = Base64.decodeBase64(base64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * 从Base64编码的字符串中解析RSA私钥。
     *
     * @param base64 Base64编码的私钥字符串。
     * @return 返回解析后的RSA私钥。
     * @throws Exception 如果解析过程出错则抛出异常。
     */
    @SneakyThrows
    public static PrivateKey decodeRSAPrivateKey(String base64) {
        byte[] keyBytes = Base64.decodeBase64(base64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

}

