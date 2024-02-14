//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.utils.crypt;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.HashMap;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class RSAHelper {
    final static Logger logger = LogManager.getLogger(RSAHelper.class);

    public final static String PRIVATE_KEY = "privateKey" ;
    public final static String PUBLIC_KEY = "publicKey" ;
    public final static String PUBLIC_KEY_MODULUS = "publicKeyModulus" ;
    public final static String PUBLIC_KEY_EXPONENT = "publicKeyExponent";

    static {
        BouncyCastleProvider provider = new BouncyCastleProvider();
        if (Security.getProvider(provider.getName()) == null)
            Security.addProvider(provider);
    }

    private static RSAPublicKey readKey(String key) throws Exception {
        byte[] encKey = Base64.decodeBase64(key.split(" ")[1]);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(encKey));

        byte[] header = readElement(dis);
        String pubKeyFormat = new String(header);
        if (!pubKeyFormat.equals("ssh-rsa"))
            throw new RuntimeException("Unsupported format");

        byte[] publicExponent = readElement(dis);
        byte[] modulus = readElement(dis);

        KeySpec spec = new RSAPublicKeySpec(new BigInteger(modulus), new BigInteger(publicExponent));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        RSAPublicKey pubKey = (RSAPublicKey)keyFactory.generatePublic(spec);

        return pubKey;
    }

    private static byte[] readElement(DataInput dis) throws IOException {
        int len = dis.readInt();
        byte[] buf = new byte[len];
        dis.readFully(buf);
        return buf;
    }

    public static String encryptWithSSHPublicKey(String sshPublicKey, String content) {
        String returnString = null;
        try {
            RSAPublicKey publicKey = readKey(sshPublicKey);
            Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom());
            byte[] encrypted = cipher.doFinal(content.getBytes());
            returnString = Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            logger.info("[ignored]"
                    + "error during public key encryption: " + e.getLocalizedMessage());
        }

        return returnString;
    }

    public static KeyPair genKey() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = new SecureRandom();
        KeyPairGenerator gen;
        gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(4096, secureRandom);
        KeyPair keyPair = gen.genKeyPair();
        return keyPair;
    }

    public static String decryptRSA(String encrypted, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException {
        if (privateKey == null) {
            logger.error("no private key");
        }
        String decrypted = "";
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            byte[] encryptedBytes = hexToByteArray(encrypted);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            decrypted = new String(decryptedBytes, "utf-8");
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return decrypted;
    }

    public static byte[] hexToByteArray(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return new byte[]{};
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            byte value = (byte)Integer.parseInt(hex.substring(i, i + 2), 16);
            bytes[(int) Math.floor(i / 2)] = value;
        }
        return bytes;
    }

    public static Map<String, String> getKeySpec(PublicKey publicKey) {
        Map<String, String> spec = new HashMap<String, String>();
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec publicSpec = (RSAPublicKeySpec) keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
            spec.put(RSAHelper.PUBLIC_KEY_MODULUS, publicSpec.getModulus().toString(16));
            spec.put(RSAHelper.PUBLIC_KEY_EXPONENT, publicSpec.getPublicExponent().toString(16));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return spec;
    }

}
