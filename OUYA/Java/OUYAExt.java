/**
|-----------------------------------|
| GAMEMAKER STUDIO - OUYA EXTENSION |
|-----------------------------------|
|    By Manuel Etchegaray - 2014    |
|      contact@invadergames.net     |
|-----------------------------------|
**/
package ${YYAndroidPackageName};

import java.text.DecimalFormat;
import android.util.DisplayMetrics;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.String;
import java.lang.Math;
import java.util.*;
import java.security.spec.X509EncodedKeySpec;
import java.security.*;
import java.text.ParseException;
import java.text.NumberFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.yoyogames.runner.RunnerJNILib;
import ${YYAndroidPackageName}.RunnerActivity;
import ${YYAndroidPackageName}.R;

import tv.ouya.console.api.*;

public class OUYAExt {
    public static Context ms_context;
    public static String DEVELOPER_ID = "00000000-0000-0000-0000-000000000000";
    public static String UUID = "00000000-0000-0000-0000-000000000000";
    public static String USERNAME = "";
    public static double IS_OUYA = 0;

    public static OuyaFacade ouyaFacade = OuyaFacade.getInstance();

    // 
    public static double initFunction() {
        Log.i("OUYAExt", "OUYA EXTENSION INIT");
        ms_context = RunnerJNILib.GetApplicationContext();
        OuyaController.init(ms_context);

        if (ouyaFacade.isRunningOnOUYAHardware() == true) {
            IS_OUYA = 1;
        }
        else {
            IS_OUYA = 0;
        }

        return 1;
    }

    //
    public static double finalFunction() {
        Log.i("OUYAExt", "OUYA EXTENSION FINAL");
        ouyaFacade.shutdown();

        return 1;
    }

    //
    public static String ouyaSetDevID(String arg0) {
        DEVELOPER_ID = arg0;
        return DEVELOPER_ID;
    }

    //
    public static String ouyaGetDevID() {
        return DEVELOPER_ID;
    }

    //
    public static String ouyaGetUUID() {
        return UUID;
    }

    //
    public static String ouyaGetUsername() {
        return USERNAME;
    }

    //
    public static double ouyaIsOUYA() {
        return IS_OUYA;
    }

    //
    public static double ouyaPutData(String arg0, String arg1) {
        final String key = arg0;
        final String name = arg1;
        ouyaFacade.putGameData(key, name);

        return 1;
    }

    //
    public static String ouyaGetData(String arg0) {
        final String key = arg0;
        String data = ouyaFacade.getGameData(key);
        if (data == null) {
            return "";
        }
        else {
            return data;
        }
    }

    //
    public static double ouyaShowCursor(double arg0) {
        if (arg0 == 0) {
            OuyaController.showCursor(false);
        }
        else {
            OuyaController.showCursor(true);
        }

        return 1;
    }

    //
    // Begin of IAPs stuff
    //

    public static PublicKey mPublicKey;
    private static JSONObject json = new JSONObject();
    public static final List<Purchasable> PRODUCT_ID_LIST = new ArrayList<Purchasable>();
    public static final List<String> PRODUCT_ID_LIST_ID = new ArrayList<String>();
    private static List<Product> mProductList;
    private static List<Receipt> mReceiptList;
    private static final Map<String, Product> mOutstandingPurchaseRequests = new HashMap<String, Product>();

    // ********************
    public static double ouyaIAPProductAdd(String arg0) {
        PRODUCT_ID_LIST.add(new Purchasable(arg0));
        PRODUCT_ID_LIST_ID.add(arg0);
        return PRODUCT_ID_LIST.size()-1;
    }

    // ********************
    public static double ouyaIAPProductRemove(double arg0) {
        final double pos = arg0;
        PRODUCT_ID_LIST.remove(pos);
        PRODUCT_ID_LIST_ID.remove(pos);
        return PRODUCT_ID_LIST.size();
    }

    // ******************** 
    public static double ouyaIAPProductRefresh() {
        requestProducts();

        return 1;
    }

    // ******************** 
    public static String ouyaIAPProductList() {
        return json.toString();
    }

    // ******************** 
    public static double ouyaIAPCreate(String arg0) {
        Log.i("OUYAExt", "** OUYA IAP Create **");
        ouyaSetDevID(arg0);
        ouyaFacade.init(ms_context, arg0);
        ouyaRefreshGamerInfo();
        
        // taken directly from devs.ouya
        try {
            // Read the key.der file (downloaded from the developer portal)
            //InputStream inputStream = RunnerActivity.CurrentActivity.getResources().openRawResource(R.raw.key);
            InputStream inputStream = RunnerActivity.CurrentActivity.getResources().getAssets().open( "key.der" );
            
            byte[] APPLICATION_KEY = new byte[inputStream.available()];
            inputStream.read(APPLICATION_KEY);
            inputStream.close();
            // Create a public key
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(APPLICATION_KEY);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            mPublicKey = keyFactory.generatePublic(keySpec);
            Log.i("OUYAExt", "Key gen: OK!?");
        } catch (Exception e) {
            Log.i("OUYAExt", "Key gen: "+e.getMessage());
            Log.e("OUYAExt", "Unable to create encryption key", e);
        }

        return 1;
    }

    // ******************** 
    public static double ouyaIAPProductBuy(double arg0) {
        Log.i("OUYAExt", "Init ouyaIAPProductBuy()..");
        final String pid = PRODUCT_ID_LIST_ID.get((int) arg0);
        Log.i("OUYAExt", "pid: "+pid);
        Product product = null;
        for (Product tp : mProductList) {
            if (pid.equals(tp.getIdentifier())) {
                product = tp;
                break;
            }
        }

        if (product == null) {
            Log.i("OUYAExt", "product_buy: No such product exists.");
            return -1;
        }

        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

            // This is an ID that allows you to associate a successful purchase with
            // it's original request. The server does nothing with this string except
            // pass it back to you, so it only needs to be unique within this instance
            // of your app to allow you to pair responses with requests.
            String uniqueId = Long.toHexString(sr.nextLong());

            JSONObject purchaseRequest = new JSONObject();
            purchaseRequest.put("uuid", uniqueId);
            purchaseRequest.put("identifier", product.getIdentifier());

            String purchaseRequestJson = purchaseRequest.toString();

            byte[] keyBytes = new byte[16];
            sr.nextBytes(keyBytes);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");

            byte[] ivBytes = new byte[16];
            sr.nextBytes(ivBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] payload = cipher.doFinal(purchaseRequestJson.getBytes("UTF-8"));

            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, mPublicKey);
            byte[] encryptedKey = cipher.doFinal(keyBytes);

            Purchasable purchasable =
                    new Purchasable(
                            product.getIdentifier(),
                            Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
                            Base64.encodeToString(ivBytes, Base64.NO_WRAP),
                            Base64.encodeToString(payload, Base64.NO_WRAP) );

            synchronized (mOutstandingPurchaseRequests) {
                mOutstandingPurchaseRequests.put(uniqueId, product);
            }

            ouyaFacade.requestPurchase(purchasable, new PurchaseListener(product));
            //throws GeneralSecurityException, UnsupportedEncodingException, JSONException 
        } catch (GeneralSecurityException e) {
            Log.i("OUYAExt", "GeneralSecurityException: "+e.getMessage());
        } catch (UnsupportedEncodingException e) {
            Log.i("OUYAExt", "UnsupportedEncodingException: "+e.getMessage());
        } catch (JSONException e) {
            Log.i("OUYAExt", "JSONException: "+e.getMessage());
        }

        return 1;
    }



    //
    // Start of receivers & listeners
    //
    // *********************
    // *********************
    private static double requestProducts() {
        ouyaFacade.requestProductList(PRODUCT_ID_LIST, new CancelIgnoringOuyaResponseListener<ArrayList<Product>>() {
            @Override
            public void onSuccess(final ArrayList<Product> products) {
                mProductList = products;

                JSONArray array_ = new JSONArray();
                try {
                    json.put("products", array_);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }

                for (Product p : products) {
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("cost", p.getPriceInCents());
                        obj.put("name", p.getName());
                        obj.put("id", p.getIdentifier());
                        obj.put("purchased", false);
                        json.getJSONArray("products").put(obj);
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }

                    Log.i( "OUYAExt", p.getName() + " costs " + p.getPriceInCents());
                }
                RunnerJNILib.IAPProductDetailsReceived(json.toString());
                ouyaFacade.requestReceipts(new ReceiptListener());
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
                try {
                    json.put("products", errorMessage);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.i( "OUYAExt", errorMessage);
            }
        });

        return 1;
    }

    // *********************
    // *********************
    private static class ReceiptListener extends CancelIgnoringOuyaResponseListener<String> {
        @Override
        public void onSuccess(String receiptResponse) {
            OuyaEncryptionHelper helper = new OuyaEncryptionHelper();
            List<Receipt> receipts = null;
            try {
                Log.i("OUYAExt", "Receipts (raw)");
                Log.i("OUYAExt", receiptResponse);
                Log.i("OUYAExt", " ");
                JSONObject response = new JSONObject(receiptResponse);
                if (response.has("key") && response.has("iv") && response.has("blob")) {
                    receipts = helper.decryptReceiptResponse(response, mPublicKey);
                    Log.i("OUYAExt", "Decripted..");
                } else {
                    receipts = helper.parseJSONReceiptResponse(receiptResponse);
                    Log.i("OUYAExt", "Decripted.. (parsed)");
                }
                Log.i("OUYAExt", "receipts List: "+receipts.toString());
                
            } catch (ParseException e) {
                Log.i("OUYAExt", "ParseException: "+e.getMessage());
                throw new RuntimeException(e);
            } catch (JSONException e) {
                Log.i("OUYAExt", "JSONException: "+e.getMessage());
                throw new RuntimeException(e);
            } catch (GeneralSecurityException e) {
                Log.i("OUYAExt", "GeneralSecurityException: "+e.getMessage());
                throw new RuntimeException(e);
            } catch (IOException e) {
                Log.i("OUYAExt", "IOException: "+e.getMessage());
                throw new RuntimeException(e);
            }

            for (Receipt r : receipts) {
                String pid = r.getIdentifier();
                Log.i("OUYAExt", pid);
                try {
                    JSONArray prod_ = json.getJSONArray("products");
                    for (int i=0; i<prod_.length(); i++) {
                        JSONObject o = prod_.getJSONObject(i);
                        String oid = o.getString("id");
                        if (oid.compareTo(pid) == 0) {
                            o.put("purchased", true);
                        }
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Log.i("OUYAExt", json.toString());
            RunnerJNILib.IAPProductPurchaseEvent(json.toString());
        }

        @Override
        public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
            Log.i("OUYAExt", "Request Receipts error (code " + errorCode + ": " + errorMessage + ")");
        }
    }


    // *********************
    // *********************
    private static class PurchaseListener extends CancelIgnoringOuyaResponseListener<String> {
        private Product mProduct;

        PurchaseListener(final Product product) {
            mProduct = product;
        }

        @Override
        public void onSuccess(String result) {
            Product product;
            String id;
            try {
                OuyaEncryptionHelper helper = new OuyaEncryptionHelper();
                JSONObject response = new JSONObject(result);
                if(response.has("key") && response.has("iv")) {
                    id = helper.decryptPurchaseResponse(response, mPublicKey);
                    Product storedProduct;
                    synchronized (mOutstandingPurchaseRequests) {
                        storedProduct = mOutstandingPurchaseRequests.remove(id);
                    }
                    if(storedProduct == null || !storedProduct.getIdentifier().equals(mProduct.getIdentifier())) {
                        Log.i("OUYAExt", "Purchased product is not the same as purchase request product (a)");
                        return;
                    }
                } else {
                    product = new Product(new JSONObject(result));
                    if(!mProduct.getIdentifier().equals(product.getIdentifier())) {
                        Log.i("OUYAExt", "Purchased product is not the same as purchase request product (b)");
                        return;
                    }
                }
            } catch (ParseException e) {
                Log.i("OUYAExt", e.getMessage());
            } catch (JSONException e) {
                Log.i("OUYAExt", e.getMessage());
                return;
            } catch (IOException e) {
                Log.i("OUYAExt", e.getMessage());
                return;
            } catch (GeneralSecurityException e) {
                Log.i("OUYAExt", e.getMessage());
                return;
            }
            requestProducts();
            /*
            new AlertDialog.Builder(ms_context)
                .setTitle("Congrats!")
                .setMessage("You have successfully purchased " + mProduct.getName() + " for " + formatDollarAmount(mProduct.getPriceInCents()))
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .show();
            */
        }


        @Override
        public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
            Log.i("OUYAExt", "Purchase failure!! damn!");
            Log.i("OUYAExt", "Unable to make purchase (error " + errorCode + ": " + errorMessage + ")");
        }
    }

    // Taken from Strings.h, in the OUYA_SDK IAPs example src
    public static String formatDollarAmount(int amount) {
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        return "$" + currencyFormatter.format((float)amount / 100f).substring(1);
    }

    // *********************
    // *********************
    private static void ouyaRefreshGamerInfo() {
        ouyaFacade.requestGamerInfo(new CancelIgnoringOuyaResponseListener<GamerInfo>() {
            @Override
            public void onSuccess(GamerInfo result) {
                Log.i("OUYAExt", "UUID is: " + result.getUuid());
                Log.i("OUYAExt", "Username is: " + result.getUsername());

                UUID = result.getUuid();
                USERNAME = result.getUsername();
            }

            @Override
            public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
                Log.i("OUYAExt", "fetch gamer info error (code " + errorCode + ": " + errorMessage + ")");
                boolean wasHandledByAuthHelper = OuyaAuthenticationHelper.handleError(
                    RunnerActivity.CurrentActivity, errorCode, errorMessage, optionalData, 2,
                    new OuyaResponseListener<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            ouyaRefreshGamerInfo(); // Retry the fetch if the error was handled.
                        }

                        @Override
                        public void onFailure(int errorCode, String errorMessage, Bundle optionalData) {
                            Log.i("OUYAExt", "Unable to fetch gamer info (error " +errorCode + ": " + errorMessage + ")");
                        }

                        @Override
                        public void onCancel() {
                            Log.i("OUYAExt", "Unable to fetch gamer info");
                        }
                    });

                if (!wasHandledByAuthHelper) {
                    Log.i("OUYAExt", "Unable to fetch gamer info (error " + errorCode + ": " + errorMessage + ")");
                }
            }
        });
    }


}
