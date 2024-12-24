/* Copyright 2013-2016 Wilco Baan Hofman <wilco@baanhofman.nl>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package nl.eventinfra.wifisetup;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.pm.PackageInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/* import android.util.Base64; */
// API level 18 and up
enum Profile {
    PROFILE_UNFILTERED,
    PROFILE_SITEONLY,
    PROFILE_PROTECTME,
    PROFILE_SPECIAL
}

public class WifiSetup extends Activity {
	protected static final int SHOW_PREFERENCES = 0;
	// FIXME This should be a configuration setting somehow
	private static final String INT_EAP = "eap";
	private static final String INT_PHASE2 = "phase2";
	private static final String INT_ENGINE = "engine";
	private static final String INT_ENGINE_ID = "engine_id";
	private static final String INT_CLIENT_CERT = "client_cert";
	private static final String INT_CA_CERT = "ca_cert";
	private static final String INT_PRIVATE_KEY = "private_key";
	private static final String INT_PRIVATE_KEY_ID = "key_id";
	private static final String INT_SUBJECT_MATCH = "subject_match";
	private static final String INT_ALTSUBJECT_MATCH = "altsubject_match";
	private static final String INT_PASSWORD = "password";
	private static final String INT_IDENTITY = "identity";
	private static final String INT_ANONYMOUS_IDENTITY = "anonymous_identity";
	private static final String INT_ENTERPRISEFIELD_NAME = "android.net.wifi.WifiConfiguration$EnterpriseField";
	// Because android.security.Credentials cannot be resolved...
	private static final String INT_KEYSTORE_URI = "keystore://";
	private static final String INT_CA_PREFIX = INT_KEYSTORE_URI + "CACERT_";
	private static final String INT_PRIVATE_KEY_PREFIX = INT_KEYSTORE_URI + "USRPKEY_";
	private static final String INT_PRIVATE_KEY_ID_PREFIX = "USRPKEY_";
	private static final String INT_CLIENT_CERT_PREFIX = INT_KEYSTORE_URI + "USRCERT_";
    private Handler mHandler = new Handler();
	private EditText username;
	private EditText password;
	private CheckBox check5g;
	private Button btn;
	private String subject_match;
	private String altsubject_match;

	private String realm;
	private String ssid;
	private boolean busy = false;
	private Toast toast = null;
	private int logoclicks = 0;
	private String s_username;
	private String s_password;
	private ViewFlipper flipper;
	Profile selected_profile;


	static String removeQuotes(String str) {
		int len = str.length();
		if ((len > 1) && (str.charAt(0) == '"') && (str.charAt(len - 1) == '"')) {
			return str.substring(1, len - 1);
		}
		return str;
	}

	/*
	 * Unfortunately, this returns false on a LOT of devices :(
	 *
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private boolean get5G() {
		WifiManager wifiManager = (WifiManager) this.getSystemService(WIFI_SERVICE);
		return wifiManager.is5GHzBandSupported();
	}
	*/

	static String surroundWithQuotes(String string) {
		return "\"" + string + "\"";
	}

	private void toastText(final String text) {
		if (toast != null)
			toast.cancel();
		toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
		toast.show();
	}

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.logon);

        flipper = findViewById(R.id.viewflipper);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);

		/*
		check5g = (CheckBox) findViewById(R.id.check5g);
		check5g.setChecked(true);

		TextView label5g = (TextView) findViewById(R.id.label5g);
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			check5g.setChecked(get5G());
			label5g.setText("(autodetected value)");
		} else {
			check5g.setChecked(true);
			label5g.setText("(Android 5.0 is needed to autodetect this)");
		}
		*/

        Spinner spinner = findViewById(R.id.profile);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View v, int position,
									   long id) {
                View logindata = findViewById(R.id.logindata);
                logindata.setVisibility(View.INVISIBLE);
				switch((int) id) {
					case 0:
						selected_profile = Profile.PROFILE_SITEONLY;
						toastText("You trust people on-site more than the internet! Thank you!");
						break;
                    case 1:
                        selected_profile = Profile.PROFILE_UNFILTERED;
                        toastText("Don't filter me!");
                        break;

                    case 2:
                        selected_profile = Profile.PROFILE_PROTECTME;
                        toastText("You don't trust anyone? Or maybe not your device?");
                        break;
                    case 3:
                        selected_profile = Profile.PROFILE_SPECIAL;
                        logindata.setVisibility(View.VISIBLE);
                        toastText("Hi fellow special person!");
                        break;


                }
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

            }
	    });

        btn = findViewById(R.id.button1);
		if (btn == null)
			throw new RuntimeException("button1 not found. Odd");
		btn.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View _v) {
				if (busy) {
					return;
				}
				busy = true;
				_v.setClickable(false);

				// Most of this stuff runs in the background
				Thread t = new Thread() {

					@Override
					public void run() {
						try {

							if (android.os.Build.VERSION.SDK_INT >= 18) {
								saveWifiConfig();
								resultStatus(true, "You should now have a wifi connection entry with correct security settings and certificate verification.\n\nMake sure to actually use it!");
								// Clear the password field in the UI thread
								/*
								mHandler.post(new Runnable() {
									@Override
									public void run() {
										password.setText("");
									};
								});
								*/
							} else {
								throw new RuntimeException("What version is this?! API Mismatch");
							}
						} catch (RuntimeException e) {
							resultStatus(false, "Something went wrong: " + e.getMessage());
							e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
						busy = false;
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								btn.setClickable(true);
							}
						});
					}
				};
				t.start();

			}
		});

	}

	private void saveWifiConfig() {
/*
		ssid = "emfcamp";

		subject_match = "/CN=radius.emf.camp";
		altsubject_match = "DNS:radius.emf.camp";
//		subject_match = "/CN=radius.synnack.net";
//		altsubject_match = "DNS:radius.synnack.net";


		realm = "";
		switch (selected_profile) {
			case PROFILE_UNFILTERED:
				s_username = "allowany";
				s_password = "allowany";
				break;
			case PROFILE_SITEONLY:
				s_username = "emf";
				s_password = "emf";
				break;
			case PROFILE_PROTECTME:
				s_username = "outboundonly";
				s_password = "outboundonly";
				break;
			case PROFILE_SPECIAL:
				s_username = username.getText().toString();
				s_password = password.getText().toString();
				if (s_username.contains("@")) {
					int idx = s_username.indexOf("@");
					realm = s_username.substring(idx);
				}
				break;
		}
		StoreWifiProfile(ssid, subject_match, altsubject_match, s_username, s_password);
*/

		/*if (check5g.isChecked()) {
			ssid = "MCH2022";
		} else {
			ssid = "MCH2022-legacy";
		}*/
		ssid = "38C3";
        subject_match = "/CN=radius.c3noc.net";
        altsubject_match = "DNS:radius.c3noc.net";

		realm = "";
		switch (selected_profile) {
			case PROFILE_UNFILTERED:
				s_username = "allowany";
				s_password = "allowany";
				break;
			case PROFILE_SITEONLY:
				s_username = "38c3";
				s_password = "38c3";
				break;
			case PROFILE_PROTECTME:
				s_username = "outboundonly";
				s_password = "outboundonly";
				break;
			case PROFILE_SPECIAL:
				s_username = username.getText().toString();
				s_password = password.getText().toString();
				if (s_username.contains("@")) {
					int idx = s_username.indexOf("@");
					realm = s_username.substring(idx);
				}
				break;
		}
		StoreWifiProfile(ssid, subject_match, altsubject_match, s_username, s_password);
	}
	void StoreWifiProfile(String ssid, String subject_match, String altsubject_match, String s_username, String s_password) {


		// Enterprise Settings
		HashMap<String,String> configMap = new HashMap<>();
		configMap.put(INT_SUBJECT_MATCH, subject_match);
		configMap.put(INT_ALTSUBJECT_MATCH, altsubject_match);
		configMap.put(INT_ANONYMOUS_IDENTITY, "anonymous" + realm);
		configMap.put(INT_IDENTITY, s_username);
		configMap.put(INT_PASSWORD, s_password);
		configMap.put(INT_EAP, "TTLS");
		configMap.put(INT_PHASE2, "auth=PAP");
		configMap.put(INT_ENGINE, "0");

		WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
		if (wifiManager == null) {
			return;
		}
		WifiConfiguration currentConfig = new WifiConfiguration();

		if (android.os.Build.VERSION.SDK_INT >= 29) {
			try {

				WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
						.setSsid(ssid)
						.setWpa2EnterpriseConfig(applyAndroid43EnterpriseSettings(configMap)).build();
				wifiManager.addNetworkSuggestions(Arrays.asList(suggestion));
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}

		wifiManager.setWifiEnabled(true);


		List<WifiConfiguration> configs = null;
		for (int i = 0; i < 10 && configs == null; i++) {
			configs = wifiManager.getConfiguredNetworks();
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// Do nothing ;-)
			}
		}
		// Use the existing ssid profile if it exists.
		boolean ssidExists = false;
		if (configs != null) {
			for (WifiConfiguration config : configs) {
				if (config.SSID.equals(surroundWithQuotes(ssid))) {
					currentConfig = config;
					ssidExists = true;
					break;
				}
			}
		}
		// This sets the CA certificate.
		currentConfig.enterpriseConfig = applyAndroid43EnterpriseSettings(configMap);

		// General (old) config settings
		currentConfig.SSID = surroundWithQuotes(ssid);
		currentConfig.hiddenSSID = false;
		currentConfig.priority = 40;
		currentConfig.status = WifiConfiguration.Status.DISABLED;

		currentConfig.allowedKeyManagement.clear();
		currentConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);

		// GroupCiphers (Allow most ciphers)
		currentConfig.allowedGroupCiphers.clear();
		currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
		currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);


		// PairwiseCiphers (CCMP = WPA2 only)
		currentConfig.allowedPairwiseCiphers.clear();
		currentConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

		// Authentication Algorithms (OPEN)
		currentConfig.allowedAuthAlgorithms.clear();
		currentConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

		// Protocols (RSN/WPA2 only)
		currentConfig.allowedProtocols.clear();
		currentConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);


		if (!ssidExists) {
			int networkId = wifiManager.addNetwork(currentConfig);
			wifiManager.enableNetwork(networkId, false);
		} else {
			wifiManager.updateNetwork(currentConfig);
			wifiManager.enableNetwork(currentConfig.networkId, false);
		}
		wifiManager.saveConfiguration();

	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private WifiEnterpriseConfig applyAndroid43EnterpriseSettings(HashMap<String,String> configMap) {
		try {
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			InputStream in = getResources().openRawResource(R.raw.cacert);
			// InputStream in = new ByteArrayInputStream(Base64.decode(ca.replaceAll("-----(BEGIN|END) CERTIFICATE-----", ""), 0));
			X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(in);

			WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
			enterpriseConfig.setPhase2Method(Phase2.PAP);
			enterpriseConfig.setAnonymousIdentity(configMap.get(INT_ANONYMOUS_IDENTITY));
			enterpriseConfig.setEapMethod(Eap.TTLS);

			enterpriseConfig.setCaCertificate(caCert);
			enterpriseConfig.setIdentity(s_username);
			enterpriseConfig.setPassword(s_password);
			enterpriseConfig.setSubjectMatch(configMap.get(INT_SUBJECT_MATCH));
			enterpriseConfig.setAltSubjectMatch(configMap.get(INT_ALTSUBJECT_MATCH));

			return enterpriseConfig;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		Builder builder = new AlertDialog.Builder(this);
		switch (item.getItemId()) {
	    case R.id.about:
			PackageInfo pi;
			try{
	    		pi = getPackageManager().getPackageInfo(getClass().getPackage().getName(), 0);
	    	} catch(Exception e){
	    		e.printStackTrace();
	    		return false;
	    	}
	    	builder.setTitle(getString(R.string.ABOUT_TITLE));
	    	builder.setMessage(getString(R.string.ABOUT_CONTENT)+
					"\n\n"+pi.packageName+"\n"+
					"V"+pi.versionName+
					"C"+pi.versionCode+"-syn");
			builder.setPositiveButton(getString(android.R.string.ok), null);
			builder.show();

			return true;
			case R.id.exit:
	    	System.exit(0);
	    }
	    return false;
	}

	/* Update the status in the main thread */
	protected void resultStatus(final boolean success, final String text) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
                TextView res_title = findViewById(R.id.resulttitle);
                TextView res_text = findViewById(R.id.result);

				System.out.println(text);
				res_text.setText(text);
				if (success)
					res_title.setText("Success!");
				else
					res_title.setText("ERROR!");

				if (toast != null)
					toast.cancel();
				/* toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_LONG);
				toast.show(); */
				flipper.showNext();
			}
		});
	}
}
