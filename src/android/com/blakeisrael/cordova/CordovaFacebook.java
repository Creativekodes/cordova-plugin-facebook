package com.blakeisrael.cordova;

import android.app.Activity;

import android.content.Intent;

import android.os.Bundle;

import android.support.v4.util.SimpleArrayMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.lang.IllegalArgumentException;

import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;

import com.facebook.appevents.AppEventsLogger;

import com.facebook.login.LoginBehavior;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CordovaFacebook extends CordovaPlugin {
    private String appId;
    private AppEventsLogger fbLogger;
    private CallbackManager callbackManager;

    public static final String TYPE_STRING = "string";
    public static final String TYPE_INTEGER = "integer";
    public static final String TYPE_DOUBLE = "double";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_STRING_ARRAY = "string[]";

    public static final String KEY = "key";
    public static final String TYPE = "type";
    public static final String VALUE = "value";

    public static final String EVENT = "event";
    public static final String PURCHASE = "purchase";
    public static final String LOGIN_READ = "login";
    public static final String LOGOUT = "logout";

    public static final String ERR_NO_EVENT_NAME = "Expected the first argument to be a string for the event name.";
    public static final String ERR_NO_EVENT_ARGS = "Expected non-zero number of arguments for `event`.";
    public static final String ERR_NO_PURCH_ARGS = "Expected two or three arguments for `purchase`.";
    public static final String ERR_NO_PURCH_AMNT = "Expected first argument to be a string for the purchase amount.";
    public static final String ERR_NO_PURCH_CURR = "Expected second argument to be a string for the purchase currency.";

    private final Activity getActivity() {
        return this.cordova.getActivity();
    }

    private abstract class CordovaFacebookCallback<RESULT> implements FacebookCallback<RESULT> {
        protected CallbackContext callbackContext;

        public void setCallbackContext(final CallbackContext newContext) {
            this.callbackContext = newContext;
        }
    }

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();

        Activity a = this.getActivity();

        FacebookSdk.sdkInitialize(a);
        this.appId = this.preferences.getString("FacebookAppId", null);

        if(this.appId != null) {
            // In this case, the appId was a preference in the config.xml markup
            this.fbLogger = AppEventsLogger.newLogger(a, this.appId);
        } else {
            // In this case, the appId should be in metadata
            this.fbLogger = AppEventsLogger.newLogger(a);
        }

        this.callbackManager = CallbackManager.Factory.create();

        LoginManager m = LoginManager.getInstance();
        m.setLoginBehavior(LoginBehavior.SUPPRESS_SSO);


        m.registerCallback(callbackManager, new CordovaFacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                if(this.callbackContext == null) {
                    return;
                }

                SimpleArrayMap<String, Object> mappedResult = new SimpleArrayMap<String, Object>(6);
                mappedResult.put("error", 0);
                mappedResult.put("success", 1);
                mappedResult.put("cancelled", 0);
                mappedResult.put("granted", loginResult.getRecentlyGrantedPermissions());
                mappedResult.put("declined", loginResult.getRecentlyDeniedPermissions());

                AccessToken a = loginResult.getAccessToken();

                if(a != null) {
                    mappedResult.put("accessToken", a.getToken());
                    mappedResult.put("userID", a.getUserId());
                }

                this.callbackContext.success(new JSONObject((Map)mappedResult));
            }

            @Override
            public void onCancel() {
                if(this.callbackContext == null) {
                    return;
                }

                SimpleArrayMap<String, Object> mappedResult = new SimpleArrayMap<String, Object>(5);
                mappedResult.put("error", 1);
                mappedResult.put("success", 0);
                mappedResult.put("cancelled", 1);

                AccessToken a = AccessToken.getCurrentAccessToken();

                if(a != null) {
                    mappedResult.put("accessToken", a.getToken());
                    mappedResult.put("userID", a.getUserId());
                }

                this.callbackContext.error(new JSONObject((Map)mappedResult));
            }

            @Override
            public void onError(FacebookException exception) {
                if(this.callbackContext == null) {
                    return;
                }

                SimpleArrayMap<String, Object> mappedResult = new SimpleArrayMap<String, Object>(7);
                mappedResult.put("error", 1);
                mappedResult.put("success", 0);
                mappedResult.put("cancelled", 0);
                mappedResult.put("errorCode", 0);
                mappedResult.put("errorLocalized", exception.getLocalizedMessage());

                AccessToken a = AccessToken.getCurrentAccessToken();

                if(a != null) {
                    mappedResult.put("accessToken", a.getToken());
                    mappedResult.put("userID", a.getUserId());
                }

                this.callbackContext.error(new JSONObject((Map)mappedResult));
            }
        });
    }

    @Override
    public void onResume(boolean multitasking)  {
        super.onResume(multitasking);

        Activity a = this.getActivity();

        if(this.appId != null) {
            AppEventsLogger.activateApp(a, this.appId);
        } else {
            AppEventsLogger.activateApp(a);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        Activity a = this.getActivity();

        if(this.appId != null) {
            AppEventsLogger.deactivateApp(a, this.appId);
        } else {
            AppEventsLogger.deactivateApp(a);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(callbackManager != null) {
            callbackManager.onActivityResult(requestCode, resultCode, intent);
        }
    }

    public static Bundle jsonArrayAsBundle(JSONArray arr) {
        int length = arr == null ? 0 : arr.length();

        if(0 == length) {
            return null;
        }

        Bundle b = new Bundle(length);

        for(int i = 0; i < length; ++i) {
            JSONObject obj = arr.optJSONObject(i);

            if(obj == null || obj.length() == 0) {
                continue;
            }

            String key = obj.optString(KEY);
            String type = obj.optString(TYPE);

            if(key == null || type == null) {
                continue;
            }

            if(TYPE_STRING.equals(type)) {
                String value = obj.optString(VALUE);

                if(value != null) {
                    b.putString(key, value);
                } else {
                    continue;
                }

            } else if(TYPE_INTEGER.equals(type)) {
                try {
                    int value = obj.getInt(VALUE);

                    b.putInt(key, value);
                } catch(JSONException e) {
                    continue;
                }

            } else if(TYPE_DOUBLE.equals(type)) {
                double value = obj.optDouble(VALUE);

                if (!Double.isNaN(value)) {
                    b.putDouble(key, value);
                } else {
                    continue;
                }
            } else if(TYPE_BOOLEAN.equals(type)) {
                try {
                    boolean value = obj.getBoolean(VALUE);
                    b.putBoolean(key, value);
                } catch(JSONException e) {
                    continue;
                }
            }
        }

        return b;
    }

    private void trackEvent(JSONArray args, final CallbackContext callbackContext) {
        /**\
         * Possible ways to log events to Fb: (see: https://developers.facebook.com/docs/reference/android/current/class/AppEventsLogger/)
         * NB: This doesn't include purchase events, which should use the `purchase` function to call into the plugin
         *
         * logEvent(string name)
         * logEvent(string name, double valueToSum)
         * logEvent(string name, Bundle properties)
         * logEvent(string name, double valueToSum, Bundle properties)
         *
         */
        if (args.length() < 1) {
            callbackContext.error(ERR_NO_EVENT_ARGS);
        } else {
            // We require at minimum an event name passed to us, as a string.
            String eventName = args.optString(0);

            if(eventName == null) {
                callbackContext.error(ERR_NO_EVENT_NAME);
                return;
            }

            double valueToSum = Double.NaN;
            JSONArray properties = null;

            for(int i = 1, l = args.length(); i < l; ++i) {
                double tmpd = args.optDouble(i);
                if(Double.isNaN(tmpd)) {
                    JSONArray tmpa = args.optJSONArray(i);
                    if(tmpa != null) {
                        properties = tmpa;
                    }
                } else {
                    valueToSum = tmpd;
                }
            }

            Bundle propertiesBundle = jsonArrayAsBundle(properties);

            boolean withValue = !(Double.isNaN(valueToSum));
            boolean withProperties = (propertiesBundle != null);

            if(withValue && withProperties) {
                this.fbLogger.logEvent(eventName, valueToSum, propertiesBundle);
            } else if(withValue) {
                this.fbLogger.logEvent(eventName, valueToSum);
            } else if(withProperties) {
                this.fbLogger.logEvent(eventName, propertiesBundle);
            } else {
                this.fbLogger.logEvent(eventName);
            }

            callbackContext.success();
        }
    }

    private void trackPurchase(JSONArray args, final CallbackContext callbackContext) {
        if(args.length() < 2) {
            callbackContext.error(ERR_NO_EVENT_ARGS);
        } else {
            String amount = args.optString(0);
            BigDecimal bdAmount = new BigDecimal(amount);

            if(amount == null || bdAmount == null) {
                callbackContext.error(ERR_NO_PURCH_AMNT);
                return;
            }

            String currencyCode = args.optString(1);
            Currency cCurrency;

            try {
                cCurrency = Currency.getInstance(currencyCode);
            } catch(IllegalArgumentException e) {
                cCurrency = null;
            }

            if(currencyCode == null || cCurrency == null) {
                callbackContext.error(ERR_NO_PURCH_CURR);
                return;
            }

            JSONArray properties = args.optJSONArray(2);
            Bundle propertiesBundle = jsonArrayAsBundle(properties);

            if(propertiesBundle != null) {
                fbLogger.logPurchase(bdAmount, cCurrency, propertiesBundle);
            } else {
                fbLogger.logPurchase(bdAmount, cCurrency);
            }

            callbackContext.success();
        }
    }

    private void loginWithPermissions(JSONArray args, final CallbackContext callbackContext) {
        ((CordovaActivity)this.getActivity()).setActivityResultCallback(this);

        JSONArray jsonPermissions = args.optJSONArray(0);

        Collection<String> permissions = null;

        if(jsonPermissions != null) {
            permissions = new ArrayList<String>(jsonPermissions.length());

            for(int i = 0, l = jsonPermissions.length(); i < l; ++i) {
                String s = jsonPermissions.optString(i);

                if(s != null) {
                    permissions.add(s);
                }
            }
        }

        LoginManager.getInstance().logInWithReadPermissions(this.getActivity(), permissions);
    }

    private void logout(final CallbackContext callbackContext) {
        LoginManager.getInstance().logOut();

        callbackContext.success();
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (EVENT.equals(action)) {
            // TODO: Determine if this needs to run in a runnable.
            this.trackEvent(args, callbackContext);

            return true;
        } else if(PURCHASE.equals(action)) {
            // TODO: Determine if this needs to run in a runnable.
            this.trackPurchase(args, callbackContext);

            return true;
        } else if(LOGIN_READ.equals(action)) {
            this.loginWithPermissions(args, callbackContext);

            return true;
        } else if(LOGOUT.equals(action)) {
            this.logout(callbackContext);

            return true;
        } else {
            return false;
        }
    }

}