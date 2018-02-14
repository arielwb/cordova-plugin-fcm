package com.gae.scaffolder.plugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;

import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.HashMap;
import java.util.Map;

public class FCMPlugin extends CordovaPlugin {
 
	private static final String TAG = "FCMPlugin";
	
	public static CordovaWebView gWebView;
	public static String notificationCallBack = "FCMPlugin.onNotificationReceived";
	public static String tokenRefreshCallBack = "FCMPlugin.onTokenRefreshReceived";
    public static Boolean notificationCallBackReady = false;
    
	public static String pushStorageKey = "FCMPluginSavedPushes";
	public static String pushDataTitleKey = "titulo";
	public static String pushDataBodyKey = "msg";
	public static String pushIconName = "tt_push_icon";
	public static String pushIconColor = "#1CABE7";
	public static String pushConfirmationUrl = "http://201.30.147.77:8098/servicessisare/rest/TControllerMensagemPush/setStatusEnvioMensagemPush";
	public SharedPreferences sharedPref;

	public FCMPlugin() {}
	
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		sharedPref = cordova.getActivity().getSharedPreferences(pushStorageKey, Context.MODE_PRIVATE);
		gWebView = webView;
		Log.d(TAG, "==> FCMPlugin initialize");
		FirebaseMessaging.getInstance().subscribeToTopic("android");
		FirebaseMessaging.getInstance().subscribeToTopic("all");
	}
	 
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

		Log.d(TAG,"==> FCMPlugin execute: "+ action);
		
		try{
			// READY //
			if (action.equals("ready")) {
				//
				callbackContext.success();
			}
			// GET TOKEN //
			else if (action.equals("getToken")) {
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						try{
							String token = FirebaseInstanceId.getInstance().getToken();
							callbackContext.success( FirebaseInstanceId.getInstance().getToken() );
							Log.d(TAG,"\tToken: "+ token);
						}catch(Exception e){
							Log.d(TAG,"\tError retrieving token");
						}
					}
				});
			}
			// NOTIFICATION CALLBACK REGISTER //
			else if (action.equals("registerNotification")) {
				notificationCallBackReady = true;
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						String savedPushes = FCMPlugin.getSavedPushes(sharedPref);
						if (savedPushes != null){
                            FCMPlugin.sendPushPayload(savedPushes, sharedPref);
                            FCMPlugin.deleteSavedPushes(sharedPref);
                          }
						callbackContext.success();
					}
				});
			}
			// UN/SUBSCRIBE TOPICS //
			else if (action.equals("subscribeToTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().subscribeToTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals("unsubscribeFromTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().unsubscribeFromTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else{
				callbackContext.error("Method not found");
				return false;
			}
		}catch(Exception e){
			Log.d(TAG, "ERROR: onPluginAction: " + e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		}
		
		//cordova.getThreadPool().execute(new Runnable() {
		//	public void run() {
		//	  //
		//	}
		//});
		
		//cordova.getActivity().runOnUiThread(new Runnable() {
        //    public void run() {
        //      //
        //    }
        //});
		return true;
	}
	
    public static void sendPushPayload(Map<String, Object> payload, SharedPreferences sharedPref) {

        String json = mapToJsonString(payload);
        sendPushPayload(json, sharedPref);
      }
    
      public static void sendTokenRefresh(String token) {
        Log.d(TAG, "==> FCMPlugin sendRefreshToken");
        try {
          String callBack = "javascript:" + tokenRefreshCallBack + "('" + token + "')";
          gWebView.sendJavascript(callBack);
        } catch (Exception e) {
          Log.d(TAG, "\tERROR sendRefreshToken: " + e.getMessage());
        }
      }
    
      public static void sendPushPayload(String payload, SharedPreferences sharedPref) {
        Log.d(TAG, "==> FCMPlugin sendPushPayload");
        Log.d(TAG, "\tnotificationCallBackReady: " + notificationCallBackReady);
        Log.d(TAG, "\tgWebView: " + gWebView);
    
        String callBack = "javascript:" + notificationCallBack + "('" + payload + "')";
        if (notificationCallBackReady && gWebView != null) {
          Log.d(TAG, "\tSent PUSH to view String: " + callBack);
          gWebView.sendJavascript(callBack);
        } else {
          Log.d(TAG, "\tView not ready. SAVED NOTIFICATION: " + callBack);
          FCMPlugin.savePush(payload, sharedPref);
        }
      }
    
      public static String mapToJsonString(Map<String, Object> payload) {
        JSONObject jo = new JSONObject();
        try {
          for (String key : payload.keySet()) {
            jo.put(key, payload.get(key));
            Log.d(TAG, "\tpayload: " + key + " => " + payload.get(key));
          }
    
        } catch (Exception e) {
          Log.d(TAG, "\tERROR toJsonString: " + e.getMessage());
        }
        String data = jo.length() > 0 ? jo.toString() : payload.toString();
        return data;
      }
    
      public static String getSavedPushes(SharedPreferences sharedPref) {
        String savedPushes = sharedPref.getString(pushStorageKey, null);
        Log.d(TAG, "==> FCMPlugin getSavedPushes" + savedPushes);
        return savedPushes;
      }
    
      public static void deleteSavedPushes(SharedPreferences sharedPref) {
    
        Log.d(TAG, "==> FCMPlugin deleteSavedPushes");
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(pushStorageKey);
        editor.apply();
      }
    
      public static void savePush(String payload, SharedPreferences sharedPref) {
        Log.d(TAG, "==> FCMPlugin savePush" + payload);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(pushStorageKey, payload);
        editor.commit();
      }

      public static void sendPushConfirmation(Context context, final String messageId) {

        RequestQueue queue = Volley.newRequestQueue(context);
        final JSONObject jsonBody = new JSONObject();
        try {
          jsonBody.put("idEnvio", messageId.toString());
          jsonBody.put("status", "2");
        } catch (Exception e) {
          Log.d(TAG, "\tERROR creating json: " + e.getMessage());
        }
    
        JsonObjectRequest request = new JsonObjectRequest(pushConfirmationUrl, jsonBody,
          new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
              Log.d(TAG, "Response is: " + response.toString());
            }
          },
          new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
              Log.d(TAG, "That didn't work! ->" + error.getMessage());
            }
          }
        ) {
          @Override
          public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> params = new HashMap<String, String>();
            String credentials = "3EFU4&({H}00F:3EFU4&({H}00F";
            String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    
            params.put("Content-Type", "application/json");
            params.put("Authorization", auth);
            return params;
          }
        };
    
        queue.add(request);
      }
  
  @Override
	public void onDestroy() {
		gWebView = null;
		notificationCallBackReady = false;
	}
} 
