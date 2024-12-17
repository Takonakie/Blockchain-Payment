package com.example.blockchainpayment;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public class BlockchainUtils {
    public static String createTransaction(String sender, String receiver, double amount) {
        String rawData = sender + receiver + amount + System.currentTimeMillis();
        return Numeric.toHexString(Hash.sha3(rawData.getBytes()));
    }

    public static boolean validateTransaction(String transactionHash) {
        return transactionHash != null && transactionHash.length() == 64;
    }
}