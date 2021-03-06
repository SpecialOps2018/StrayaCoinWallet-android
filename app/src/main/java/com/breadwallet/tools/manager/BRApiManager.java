package com.breadwallet.tools.manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.breadwallet.BreadApp;
import com.breadwallet.presenter.entities.BRBusinessEntity;
import com.breadwallet.presenter.entities.CurrencyEntity;
import com.breadwallet.tools.sqlite.BusinessesDataSource;
import com.breadwallet.tools.sqlite.CurrencyDataSource;
import com.breadwallet.tools.threads.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.BRWalletManager;
import com.platform.APIClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static com.breadwallet.presenter.fragments.FragmentSend.isEconomyFee;
import okhttp3.Request;
import okhttp3.Response;

/**
 * BreadWallet
 * <p>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/22/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class BRApiManager {
    private static final String TAG = BRApiManager.class.getName();

    private static BRApiManager instance;
    private Timer timer;

    private TimerTask timerTask;

    private Handler handler;

//    public Set<BRBusinessEntity> businesses;
    public List<BRBusinessEntity> businesses;

    private BRApiManager() {
        handler = new Handler();
    }

    public static BRApiManager getInstance() {

        if (instance == null) {
            instance = new BRApiManager();
        }
        return instance;
    }

    private Set<CurrencyEntity> getCurrencies(Activity context) {
        Set<CurrencyEntity> set = new LinkedHashSet<>();
        try {
            JSONArray arrBTCRates = fetchRates(context);
            JSONArray arrNAHRates = fetchNAHRates(context);
            updateFeePerKb(context);
            if (arrBTCRates != null && arrNAHRates != null) {
                JSONObject tmpNAHObj = (JSONObject) arrNAHRates.get(0);
                float BTCNAHRate = (float) tmpNAHObj.getDouble("price");
                int length = arrBTCRates.length();
                for (int i = 0; i < length; i++) {
                    CurrencyEntity tmp = new CurrencyEntity();
                    try {
                        JSONObject tmpObj = (JSONObject) arrBTCRates.get(i);
                        tmp.name = tmpObj.getString("name");
                        tmp.code = tmpObj.getString("code");
                        tmp.rate = (float) tmpObj.getDouble("rate") * BTCNAHRate;
                        String selectedISO = BRSharedPrefs.getIso(context);
//                        Log.e(TAG,"selectedISO: " + selectedISO);
                        if (tmp.code.equalsIgnoreCase(selectedISO)) {
//                            Log.e(TAG, "theIso : " + theIso);
//                                Log.e(TAG, "Putting the shit in the shared preffs");
                            BRSharedPrefs.putIso(context, tmp.code);
                            BRSharedPrefs.putCurrencyListPosition(context, i);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    set.add(tmp);
                }
            } else {
                Log.e(TAG, "getCurrencies: failed to get currencies, response string: " + arrBTCRates + arrNAHRates);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        List tempList = new ArrayList<>(set);
        Collections.reverse(tempList);
        return new LinkedHashSet<>(set);
    }

//    private Set<BRBusinessEntity> getBusinesses(Activity context) {
//        Set<BRBusinessEntity> set = new LinkedHashSet<>();
    private List<BRBusinessEntity> getBusinesses(Activity context) {
        List<BRBusinessEntity> set = new ArrayList<>();

        try {
            JSONArray arr = fetchBusinesses(context);
            if (arr != null) {
                int length = arr.length();
                for (int i = 0; i < length; i++) {
                    BRBusinessEntity tmp1 = new BRBusinessEntity();
                    try {
                        JSONObject tmpObj = (JSONObject) arr.get(i);
                        tmp1.businessname = tmpObj.getString("bn");
                        tmp1.businessproducts = tmpObj.getString("bp");
                        tmp1.lat = (float) tmpObj.getDouble("lat");
                        tmp1.lng = (float) tmpObj.getDouble("lng");
                        tmp1.dateStart = tmpObj.getString("ds");
                        tmp1.regLength = tmpObj.getInt("rl");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    set.add(tmp1);
                }
            } else {
                Log.e(TAG, "getBusinesses: failed to get businesses, response string: " + arr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List tempList = new ArrayList<>(set);
        Collections.reverse(tempList);
//        return new LinkedHashSet<>(set);
        return new ArrayList<>(set);
    }


    private void initializeTimerTask(final Context context) {
        timerTask = new TimerTask() {
            public void run() {
                //use a handler to run a toast that shows the current timestamp
                handler.post(new Runnable() {
                    public void run() {
                        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                            @Override
                            public void run() {
                                if (!BreadApp.isAppInBackground(context)) {
                                    Log.e(TAG, "doInBackground: Stopping timer, no activity on.");
                                    BRApiManager.getInstance().stopTimerTask();
                                }
                                Set<CurrencyEntity> tmp = getCurrencies((Activity) context);
                                if (tmp.size() > 0) {
                                    CurrencyDataSource.getInstance(context).deleteAllCurrencies();
                                    CurrencyDataSource.getInstance(context).putCurrencies(tmp);
                                }
                                businesses = getBusinesses((Activity) context);
                                if (businesses.size() > 0) {
                                    BusinessesDataSource.getInstance(context).deleteAllBusinesses();
                                    BusinessesDataSource.getInstance(context).putBusinesses(businesses);
                                }
                            }
                        });
                    }
                });
            }
        };
    }

    public void startTimer(Context context) {
        //set a new Timer
        if (timer != null) return;
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask(context);

        //schedule the timer, after the first 5000ms the TimerTask will run every 10000ms
        timer.schedule(timerTask, 0, 60000); //
    }

    public void stopTimerTask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }


    public static JSONArray fetchRates(Activity activity) {
//        String jsonString = "{\"data\":[{\"code\":\"BTC\",\"rate\":0.00001000,\"name\":\"Bitcoin\"}, {\"code\":\"AUD\",\"name\":\"Australian Dollar\",\"rate\":0.1228}, {\"code\":\"NZD\",\"name\":\"New Zealand Dollar\",\"rate\":0.1321}, {\"code\":\"USD\",\"name\":\"US Dollar\",\"rate\":0.0963}, {\"code\":\"GBP\",\"name\":\"British Pound Sterling\",\"rate\":0.0690}, {\"code\":\"KRW\",\"name\":\"Korean Won\",\"rate\":103.7212}, {\"code\":\"EUR\",\"name\":\"Euro\",\"rate\":0.0783}, {\"code\":\"JPY\",\"name\":\"Japanese Yen\",\"rate\":10.2945}, {\"code\":\"CAD\",\"name\":\"Canadian Dollar\",\"rate\":0.1217}, {\"code\":\"CNY\",\"name\":\"Chinese Yuan\",\"rate\":0.6103}, {\"code\":\"HKD\",\"name\":\"Hong Kong Dollar\",\"rate\":0.7535}, {\"code\":\"SGD\",\"name\":\"Singapore Dollar\",\"rate\":0.1271}]}";
        String jsonString = urlGET(activity, "https://bitpay.com/rates");
        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);
            jsonArray = obj.getJSONArray("data");

        } catch (JSONException ignored) {
        }
        return jsonArray; // == null ? backupFetchRates(activity) : jsonArray;
    }

    public static JSONArray fetchNAHRates(Activity activity) {

/*
        [{"BTC-NAH":{"initialprice":"0.00000450","price":"0.00000450","high":"0.00000450","low":"0.00000450","volume":"0.01069059"}},{"BTC-NBR":{"initialprice":"0.00000071","price":"0.00000082","high":"0.00000093","low":"0.00000071","volume":"0.04954022"}}]

         */

        String jsonString = urlGET(activity, "https://tradeogre.com/api/v1/markets");
        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        jsonString=jsonString.replace("[{","{");
        jsonString=jsonString.replace("}]","}");
        jsonString=jsonString.replace(":{",":[{");
        jsonString=jsonString.replace("}},{","}],");
        jsonString=jsonString.replace("}}","}]}");
        try {
            JSONObject obj = new JSONObject(jsonString);
            jsonArray = obj.getJSONArray("BTC-NAH");

        } catch (JSONException ignored) {
        }
        return jsonArray;
    }

    public static void updateFeePerKb(Activity activity) {
        String jsonString = urlGET(activity, "https://api.strayawallet.com/fee-per-kb/");
        if (jsonString == null || jsonString.isEmpty()) {
            Log.e(TAG, "updateFeePerKb: failed to update fee, response string: " + jsonString);
            return;
        }
        long fee;
        long economyFee;
        try {
            JSONObject obj = new JSONObject(jsonString);
            fee = obj.getLong("fee_per_kb");
            economyFee = obj.getLong("fee_per_kb_economy");
            if (fee != 0 && fee < BRConstants.MAX_FEE_PER_KB) {
                BRSharedPrefs.putFeePerKb(activity, fee);
                BRWalletManager.getInstance().setFeePerKb(fee, isEconomyFee);
            }

            if (economyFee != 0 && economyFee < BRConstants.MAX_FEE_PER_KB) {
                BRSharedPrefs.putEconomyFeePerKb(activity, economyFee);
            }
        } catch (JSONException e) {
            BRReportsManager.reportBug(e);
            e.printStackTrace();
        }
    }

    public static JSONArray fetchBusinesses(Activity activity) {
//        String jsonString = "{ \"registeredbusinesses\": [{\"businessname\":\"Brunelli Cafe\",\"businessproducts\":\"Food and Drink\",\"lat\":-34.922912,\"lng\":138.606689,\"dateStart\":\"03/03/2018\",\"reglength\":365}]}";
        String jsonString = urlGET(activity, "https://api.strayawallet.com/businessdirectory/results.php");
        JSONArray jsonArray = null;
        if (jsonString == null) return null;
        try {
            JSONObject obj = new JSONObject(jsonString);
            jsonArray = obj.getJSONArray("registeredbusinesses");

        } catch (JSONException ignored) {
        }
        return jsonArray; // == null ? backupFetchRates(activity) : jsonArray;
    }

    public static String urlGET(Context app, String myURL) {
//        System.out.println("Requested URL_EA:" + myURL);
        Request request = new Request.Builder()
                .url(myURL)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-agent", Utils.getAgentString(app, "android/HttpURLConnection"))
                .get().build();
        String response = null;
        Response resp = APIClient.getInstance(app).sendRequest(request, false, 0);

        try {
            if (resp == null) {
                Log.e(TAG, "urlGET: " + myURL + ", resp is null");
                return null;
            }
            response = resp.body().string();
            String strDate = resp.header("date");
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            Date date = formatter.parse(strDate);
            long timeStamp = date.getTime();
            BRSharedPrefs.putSecureTime(app, timeStamp);
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        } finally {
            if (resp != null) resp.close();

        }
        return response;
    }

}
