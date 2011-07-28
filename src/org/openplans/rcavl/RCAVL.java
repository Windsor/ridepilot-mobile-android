/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.openplans.rcavl;

import java.io.IOException;
import java.util.Arrays;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.openplans.rcavl.GpsService.LocalBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class RCAVL extends Activity {
	String TAG = RCAVL.class.toString();

	public static final String API_REQUEST_URL = "http://novalis.org/api.json";
	private ProgressBar spinner;
	public GpsService gpsService;
	private AutoCompleteTextView emailField;
	private TextView passwordField;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		switchToLogin();
	}

	private class LoginTask extends AsyncTask<Void, Void, String> {
		/**
		 * The system calls this to perform work in a worker thread and delivers
		 * it the parameters given to AsyncTask.execute()
		 */
		protected String doInBackground(Void... params) {
			// make login request, which really is just a GET request for
			// the ping URL

			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet(API_REQUEST_URL);

			try {
				String email = emailField.getText().toString();
				String password = passwordField.getText().toString();
				BasicHttpParams httpparams = new BasicHttpParams();
				httpparams.setParameter("email", email);
				httpparams.setParameter("password", password);

				request.setParams(httpparams);
				HttpResponse response = client.execute(request);
				HttpEntity entity = response.getEntity();
				String json = EntityUtils.toString(entity);
				JSONTokener tokener = new JSONTokener(json);
				JSONObject data = (JSONObject) tokener.nextValue();
				return (String) data.get("url");
			} catch (ClientProtocolException e) {
				Log.e(TAG, "exception logging in" + e);
			} catch (IOException e) {
				Log.e(TAG, "exception logging in" + e);
			} catch (JSONException e) {
				Log.e(TAG, "exception logging in" + e);
			}
			return null;
		}

		/**
		 * The system calls this to perform work in the UI thread and delivers
		 * the result from doInBackground()
		 */
		protected void onPostExecute(String url) {
			if (url == null) {
				toast("failed to log in for some reason");
				return;
			}
			loggedIn();
			Intent intent = new Intent(RCAVL.this, GpsService.class);
			intent.putExtra("pingUrl", url);
			String email = emailField.getText().toString();
			String password = passwordField.getText().toString();
			intent.putExtra("email", email);
			intent.putExtra("password", password);
			startService(intent);
			ServiceConnection serviceConnection = new GpsServiceConnection();
			bindService(intent, serviceConnection, 0);
		}
	}

	class GpsServiceConnection implements ServiceConnection {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			gpsService = binder.getService();
			gpsService.setActivity(RCAVL.this);
		}

		public void onServiceDisconnected(ComponentName arg0) {
			// notify the user
			toast("GPS service disconnected");
		}

	}

	public void toast(final String text) {
		runOnUiThread(new Runnable() {
			public void run() {
				Context context = getApplicationContext();
				int duration = Toast.LENGTH_SHORT;

				Toast toast = Toast.makeText(context, text, duration);
				toast.show();
			}
		});
	}

	public void loggedIn() {
		spinner.setIndeterminate(false);
		spinner.setVisibility(View.INVISIBLE);

		/*
		 * Handle adding the user's email address to the frequently used email
		 * address list
		 */
		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		String emails = preferences.getString("emails", null);
		String userEmail = emailField.getText().toString();
		if (emails == null) {
			emails = userEmail;
		} else {
			String[] past_emails = emails.split(",");
			if (!Arrays.asList(past_emails).contains(userEmail)) {
				emails = emails + "," + userEmail;
			}
		}
		Editor editor = preferences.edit();
		editor.putString("emails", emails);
		editor.commit();

		toast("Logged in");
		switchToRunning();
	}

	private void switchToLogin() {
		setContentView(R.layout.login);

		SharedPreferences preferences = getPreferences(MODE_PRIVATE);
		String emails = preferences.getString("emails", "");

		String[] past_emails = emails.split(",");
		if (past_emails == null) {
			past_emails = new String[0];
		}

		emailField = (AutoCompleteTextView) findViewById(R.id.emailField);
		passwordField = (TextView) findViewById(R.id.passwordField);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				R.layout.list_item, past_emails);
		emailField.setAdapter(adapter);
		spinner = (ProgressBar) findViewById(R.id.loginProgressBar);

		View loginButton = findViewById(R.id.loginButton);
		loginButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				spinner.setIndeterminate(true);
				spinner.setVisibility(View.VISIBLE);
				new LoginTask().execute();
			}
		});
	}

	private void switchToRunning() {
		setContentView(R.layout.running);
		final Button breakButton = (Button) findViewById(R.id.breakButton);
		breakButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (gpsService.isActive()) {
					gpsService.setStatus("break", false);
					breakButton.setText(R.string.return_from_break);
				} else {
					gpsService.setStatus("active", true);
					breakButton.setText(R.string.take_a_break);
				}
			}
		});

		final Button logoutButton = (Button) findViewById(R.id.logoutButton);
		logoutButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				gpsService.setStatus("logged_out", false);
				switchToLogin();
			}
		});
	}

	@Override
	public void onDestroy() {
		stopService(new Intent(GpsService.class.getName()));
		super.onDestroy();
	}
}