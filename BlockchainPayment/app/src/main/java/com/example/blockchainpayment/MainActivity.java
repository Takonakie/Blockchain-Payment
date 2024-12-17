package com.example.blockchainpayment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d("Bluetooth", "Device: " + device.getName() + ", " + device.getAddress());
            }
        }

        try {
            BluetoothDevice device = pairedDevices.iterator().next(); // Assume a paired device is selected
            // Menggunakan UUID acak atau UUID standar untuk Bluetooth RFCOMM
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // RFCOMM UUID standar
            // Atau gunakan UUID standar
            // UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // RFCOMM UUID
            socket = device.createRfcommSocketToServiceRecord(uuid);
            socket.connect();

            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to connect via Bluetooth", Toast.LENGTH_SHORT).show();
        }

        Button sendPaymentButton = findViewById(R.id.sendPaymentButton);
        sendPaymentButton.setOnClickListener(view -> {
            String receiverAddress = "RECEIVER_WALLET_ADDRESS";
            double amount = 0.01;

            String transactionHash = BlockchainUtils.createTransaction("SENDER_WALLET_ADDRESS", receiverAddress, amount);

            try {
                if (outputStream != null) {
                    outputStream.write(transactionHash.getBytes());
                    Toast.makeText(this, "Transaction Sent via Bluetooth", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Output stream is not initialized", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to send transaction", Toast.LENGTH_SHORT).show();
            }
        });

        Button syncButton = findViewById(R.id.syncButton);
        syncButton.setOnClickListener(view -> {
            SharedPreferences sharedPreferences = getSharedPreferences("TransactionData", MODE_PRIVATE);
            String receivedTransaction = sharedPreferences.getString("ReceivedTransaction", null);

            if (receivedTransaction != null && BlockchainUtils.validateTransaction(receivedTransaction)) {
                try {
                    Web3j web3j = Web3j.build(new HttpService("https://mainnet.infura.io/v3/301e7e2e050b4d6899b00454862bdf61"));
                    Credentials credentials = Credentials.create("301e7e2e050b4d6899b00454862bdf61");

                    TransactionReceipt receipt = Transfer.sendFunds(
                            web3j, credentials, "RECEIVER_WALLET_ADDRESS",
                            BigDecimal.valueOf(0.01), Convert.Unit.ETHER
                    ).send();

                    Log.d("Blockchain", "Transaction Hash: " + receipt.getTransactionHash());
                    Toast.makeText(this, "Transaction synced to blockchain", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to sync transaction", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No valid transactions to sync", Toast.LENGTH_SHORT).show();
            }
        });

        // Listening for incoming transactions
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            try {
                // Pastikan inputStream tidak null sebelum mencoba membaca
                if (inputStream != null) {
                    bytes = inputStream.read(buffer);
                    String receivedTransaction = new String(buffer, 0, bytes);

                    runOnUiThread(() -> {
                        TextView transactionView = findViewById(R.id.transactionView);
                        transactionView.setText("Transaction Received: " + receivedTransaction);

                        SharedPreferences sharedPreferences = getSharedPreferences("TransactionData", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("ReceivedTransaction", receivedTransaction);
                        editor.apply();
                    });
                } else {
                    Log.e("Bluetooth", "InputStream is null, unable to read data");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
