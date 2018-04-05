package de.yafp.gimmepassword;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StrictMode;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import static java.lang.Math.log;
import static java.lang.Math.pow;

public class GimmePassword extends AppCompatActivity {

    private static final String TAG = "Gimme Password";

    // Tab 3: Katakana
    private char[] chars;
    private int i_passwordLength;

    // Toast Timing
    private static final int LONG_DELAY = 3500; // default: 3500 aka 3.5 seconds

    // Others
    private String generatedPassword;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;


    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;


    // #############################################################################################
    // ON CREATE
    // #############################################################################################
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // needed for checking password hash online
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // customize action bar
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.app_icon_white_24);
        getSupportActionBar().setTitle(" " + getResources().getString(R.string.app_name));

        // Create the adapter that will return a fragment for each of the three primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));

        // createShortCut on homescreen
        createShortCut();

        // collect a minimal amount of usage informations by calling a single url on app start
        performGet("http://gimmepassword.yafp.de/s/index.html");
        Log.v(TAG, "...Finished stats call");
    }


    // #############################################################################################
    //  CREATE SHORTCUT ON HOMESCREEN
    // #############################################################################################
    private void createShortCut() {
        Log.v(TAG, "F: createShortCut");

        Intent shortcutintent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        shortcutintent.putExtra("duplicate", false);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
        Parcelable icon = Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.drawable.app_icon_default_512);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(getApplicationContext(), GimmePassword.class));
        sendBroadcast(shortcutintent);
    }


    // #############################################################################################
    // HELPER: Convert to Hex
    // #############################################################################################
    private static String convertToHex(byte[] data) {
        Log.v(TAG, "F: convertToHex");

        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    // #############################################################################################
    // HELPER: SHA1
    // #############################################################################################
    private static String SHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        Log.v(TAG, "F: SHA1");

        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] textBytes = text.getBytes("iso-8859-1");
        md.update(textBytes, 0, textBytes.length);
        byte[] sha1hash = md.digest();
        return convertToHex(sha1hash);
    }


    // #############################################################################################
    // HELPER: DISPLAY TOAST MESSAGE
    // #############################################################################################
    private void displayToastMessage(String message) {
        Log.v(TAG, "F: displayToastMessage");

        Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);       // for center vertical
        toast.show();
    }


    // #############################################################################################
    // SHOW HEADS-UP NOTIFICATION
    // #############################################################################################
    private void sendHeadsUpNotification(String message, String title) {
        Log.v(TAG, "F: sendHeadsUpNotification");

        String id = TAG;

        Intent intent = new Intent(this, GimmePassword.class);
        intent.putExtra("id", id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final int not_nu = generateRandom();

        PendingIntent pendingIntent = PendingIntent.getActivity(this, not_nu /* Request code */, intent, PendingIntent.FLAG_ONE_SHOT);

        // notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, id)
                .setSmallIcon(R.drawable.app_icon_white_24)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(not_nu /* ID of notification */, notificationBuilder.build());
        }
    }


    // #############################################################################################
    // SHOW NOTIFICATION IN NOTIFICATION CENTER
    // #############################################################################################
    // https://stackoverflow.com/questions/18102052/how-to-display-multiple-notifications-in-android
    //
    private void sendNotification(String message, String title) {
        Log.v(TAG, "F: sendNotification");

        String id = TAG;

        Intent intent = new Intent(this, GimmePassword.class);
        intent.putExtra("id", id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final int not_nu = generateRandom();

        PendingIntent pendingIntent = PendingIntent.getActivity(this, not_nu /* Request code */, intent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, id)
                .setSmallIcon(R.drawable.app_icon_white_24)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(not_nu /* ID of notification */, notificationBuilder.build());
        }
    }


    // #############################################################################################
    // HELPER: GENERATE RANDOM (FOR NOTIFICATIONS)
    // #############################################################################################
    private int generateRandom() {
        Log.v(TAG, "F: generateRandom");

        Random random = new Random();
        return random.nextInt(9999 - 1000) + 1000;
    }


    // #############################################################################################
    // SHOW PASSWORD ENTROPY AND ASK FOR PWNED QUERY
    // #############################################################################################
    private void askUser(final String password, String entropy_text, String entropy_value) {
        Log.v(TAG, "F: askUser");

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Log.v(TAG, "...user want to check pwned password for generated password ");
                        try {
                            checkPWNEDPasswords(password);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        Log.v(TAG, "...user doesn't want to check pwned password for generated password");
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pw_generation_result_dialog_title);
        builder.setIcon(R.drawable.app_icon_default_32);
        builder.setMessage(getResources().getString(R.string.entropy_start_text) + " " + entropy_text + " " + getResources().getString(R.string.entropy_end_text) + " " + entropy_value + " ).\n\n" + getResources().getString(R.string.ask_to_query_pwned)).setPositiveButton(getResources().getString(R.string.pw_generation_result_dialog_yes), dialogClickListener).setNegativeButton(getResources().getString(R.string.pw_generation_result_dialog_no), dialogClickListener).show();

    }


    // #############################################################################################
    // SHOW NEGATIVE PWNED RESULT
    // #############################################################################################
    private void showPwnedAlert() {
        Log.v(TAG, "F: showPwnedAlert");

        AlertDialog alertDialog = new AlertDialog.Builder(GimmePassword.this).create();
        //alertDialog.setIcon(android.R.drawable.stat_notify_error);
        alertDialog.setIcon(R.drawable.error);
        alertDialog.setTitle(getResources().getString(R.string.pwned_warning_title));
        alertDialog.setMessage(getResources().getString(R.string.pwned_warning_message));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }


    // #############################################################################################
    // SHOW POSITIVE PWNED RESULT
    // #############################################################################################
    private void showPwnedOK() {
        Log.v(TAG, "F: showPwnedOK");

        AlertDialog alertDialog = new AlertDialog.Builder(GimmePassword.this).create();
        //alertDialog.setIcon(android.R.drawable.stat_notify_error);
        alertDialog.setIcon(R.drawable.ok);
        alertDialog.setTitle(getResources().getString(R.string.pwned_ok_title));
        alertDialog.setMessage(getResources().getString(R.string.pwned_ok_message));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }


    // #############################################################################################
    // MENU: CREATE OPTIONS MENU
    // #############################################################################################
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.v(TAG, "F: onCreateOptionsMenu");

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    // #############################################################################################
    // MENU: SELECT OPTIONS MENU
    // #############################################################################################
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "F: onOptionsItemSelected");

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Menu: Issues
        if (id == R.id.action_issues) {

            Uri uri = Uri.parse("https://github.com/yafp/GimmePassword/issues"); // missing 'http://' will cause crashed
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);

            return true;
        }

        // Menu: About
        if (id == R.id.action_about) {
            try {
                showAbout();
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        // Menu: XKCD
        if (id == R.id.action_visit_xkcd) {
            openURL_XKCD();
            /*
            try {
                openURL_XKCD();
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
            */
        }

        // Menu: pwnedpasswords
        if (id == R.id.action_visit_pwned) {
            openURL_pwned();
            /*
            try {
                openURL_pwned();
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
            */
        }

        return super.onOptionsItemSelected(item);
    }


    // #############################################################################################
    // MENU: OPEN URL XKCD
    // #############################################################################################
    private void openURL_XKCD() {
        Log.v(TAG, "F: openURL_XKCD");

        // missing 'http://' will cause crashed
        Uri uri = Uri.parse("https://xkcd.com/936/");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }


    // #############################################################################################
    // MENU: OPEN URL PWNED PASSWORDS
    // #############################################################################################
    private void openURL_pwned() {
        Log.v(TAG, "F: openURL_pwned");

        // missing 'http://' will cause crashed
        Uri uri = Uri.parse("https://haveibeenpwned.com/");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }


    // #############################################################################################
    // MENU: SHOW ABOUT DIALOG
    // #############################################################################################
    private void showAbout() throws NameNotFoundException {
        Log.v(TAG, "F: showAbout");

        // Inflate the about message contents
        View messageView = getLayoutInflater().inflate(R.layout.about, null, false);

        // Get package informations
        PackageManager manager = this.getPackageManager();
        PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.app_icon_default_64);
        builder.setTitle(R.string.app_name);
        builder.setView(messageView);

        // add dynamic information from packageManager
        //
        //Log.v(TAG, info.permissions);
        List<String> gratedPermissions = getGrantedPermissions("de.yafp.gimmepassword");
        Log.v(TAG, gratedPermissions.toString());
        // permissions are null:
        //
        // info.permissions (array?) vs requestedPermission
        //builder.setMessage("\n\nPackage:\t\t" + info.packageName + "\nVersion:\t\t\t" + info.versionName + "\nBuild:\t\t\t\t" + info.versionCode + "\nPerms:\t\t\t" + info.permissions);
        builder.setMessage("\nPackage:\t\t" + info.packageName + "\nVersion:\t\t\t" + info.versionName + "\nBuild:\t\t\t\t" + info.versionCode + "\nPerms:\t\t\t" + gratedPermissions.toString());

        builder.create();
        builder.show();
    }


    // #############################################################################################
    // HELPER: GET GRANTED PERMISSIONS
    // #############################################################################################
    List<String> getGrantedPermissions(final String appPackage) {
        Log.v(TAG, "F: getGrantedPermissions");
        List<String> granted = new ArrayList<>();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(appPackage, PackageManager.GET_PERMISSIONS);
            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                if ((pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    granted.add(pi.requestedPermissions[i]);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "...exception while trying to figure out granted permissions");
        }
        return granted;
    }


    // #############################################################################################
    // CALCULATE PASSWORD ENTROPY
    // #############################################################################################
    private String[] calculateEntropy(int length, int characters) {
        Log.v(TAG, "F: calculateEntropy");

        // calculate entropy
        double step1 = pow(characters, length);
        double step2 = log(step1) / log(2);

        // round to 1 decimal
        double total = Math.round(step2 * 10d) / 10d;

        // calculated entropy as string
        String password_entropy = Double.toString(total);

        // password quality
        //
        // add optional calculation of password strength
        // simple example: https://www.bee-man.us/computer/password_strength.html
        //
        // < 28 bits        = Very Weak; might keep out family members
        // 28 - 35 bits     = Weak; should keep out most people, often good for desktop login passwords
        // 36 - 59 bits     = Reasonable; fairly secure passwords for network and company passwords
        // 60 - 127 bits    = Strong; can be good for guarding financial information
        // 128+ bits        = Very Strong; often overkill
        String password_quality;
        if (total > 128) {
            password_quality = getResources().getString(R.string.entropy_very_strong);
        } else if (total > 60) {
            password_quality = getResources().getString(R.string.entropy_quiet_strong);
        } else if (total > 35) {
            password_quality = getResources().getString(R.string.entropy_reasonable);
        } else if (total > 28) {
            password_quality = getResources().getString(R.string.entropy_weak);
        } else {
            password_quality = getResources().getString(R.string.entropy_very_weak);
        }

        // Log entropy results
        Log.v(TAG, "...Size of charset: " + characters);
        Log.v(TAG, "...Password length: " + length);
        Log.v(TAG, "...Password quality is: " + password_quality);
        Log.v(TAG, "...Password entropy is: " + password_entropy);

        return new String[]{password_quality, password_entropy};
    }


    // #############################################################################################
    // CHECK PASSWORD HASH ONLINE AGAINST api.pwnedpasswords.com
    // #############################################################################################
    private void checkPWNEDPasswords(String password) throws IOException {
        Log.v(TAG, "F: checkPWNEDPasswords");

        // Some Links:
        // - https://www.troyhunt.com/i-wanna-go-fast-why-searching-through-500m-pwned-passwords-is-so-quick/
        // - https://stackoverflow.com/questions/5980658/how-to-sha1-hash-a-string-in-android
        // - https://haveibeenpwned.com/API/v2#PwnedPasswords
        //
        // Method 1:
        // GET https://api.pwnedpasswords.com/pwnedpassword/{password or hash}
        //
        // When a password is found in the Pwned Passwords repository, the API will respond with HTTP 200 and include a count in the response body indicating how many times that password appears in the data set.
        // When no match is found, the response code is HTTP 404.
        //
        //
        // Method 2:
        // GET https://api.pwnedpasswords.com/range/{first 5 hash chars}
        //
        // When a password hash with the same first 5 characters is found in the Pwned Passwords repository,
        // the API will respond with an HTTP 200 and include the suffix of every hash beginning with the specified prefix,
        // followed by a count of how many times it appears in the data set.
        // The API consumer can then search the results of the response for the presence of their
        // source hash and if not found, the password does not exist in the data set.

        // generate sha-1 of password
        String hashstring = null;
        try {
            hashstring = SHA1(password);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.v(TAG, "SHA-1: " + hashstring);

        // generate substring (5 first chars of hash)
        String hashstring_5 = null;
        if (hashstring != null) {
            hashstring_5 = hashstring.substring(0, 5);
        }
        Log.v(TAG, "...SHA-1 (first 5 chars): " + hashstring_5);
        Log.v(TAG, "...should check: https://api.pwnedpasswords.com/range/" + hashstring_5);

        // Start online check
        boolean success;
        // Step 1: Check the 5 digit hash string
        success = performGet("https://api.pwnedpasswords.com/range/" + hashstring_5);
        if (success) {
            Log.w(TAG, "...bummer, the 5-digit-hashstring is known at pwnedpasswords.com.");
            Log.v(TAG, "...Should continue by checking the entire hash");

            // Step 2: found 5 digit hashstring in DB, now check the entire hash
            success = performGet("https://api.pwnedpasswords.com/pwnedpassword/" + hashstring);
            if (success) {
                Log.w(TAG, "...major bummer, the complete password hash is known at pwnedpasswords.com");

                // show warning dialog
                showPwnedAlert();

            } else {
                Log.v(TAG, "...the complete password hash is not known at pwnedpasswords.com.");

                // show ok dialog
                showPwnedOK();
            }
        }
    }


    // #############################################################################################
    // HELPER: FOR PWNED PASSWORDS
    // #############################################################################################
    private boolean performGet(String target_url) {
        Log.v(TAG, "F: performGet");

        try {
            URL url = new URL(target_url);

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            if (builder.length() == 0) {
                Log.v(TAG, "...no answer");
            } else {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    // #############################################################################################
    // Tab 1: GENERATE A DEFAULT PASSWORD
    // #############################################################################################
    public void on_generate_default(View v) {
        Log.v(TAG, "F: on_generate_default");

        TextView t1_generatedPassword;

        // Reset content of password field
        t1_generatedPassword = findViewById(R.id.t1_generatedPassword);
        t1_generatedPassword.setText(null);

        // define charsets
        String charPool_uppercaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; //26
        String charPool_lowercaseLetters = "abcdefghijklmnopqrstuvwxyz"; //26
        String charPool_numbers = "0123456789"; // 10
        String charPool_specialChars = "?!.;:,-_+*/\\|<>{[()]}#&%$§@€^`´~"; // 32
        // overall 94

        String allowedChars = "";
        Random random;

        // checkboxes
        CheckBox t1_cb_uppercaseLetters;
        CheckBox t1_cb_lowercaseLetters;
        CheckBox t1_cb_numbers;
        CheckBox t1_cb_specialChars;

        // checkboxes
        t1_cb_uppercaseLetters = findViewById(R.id.t1_cb_uppercaseLetters);
        t1_cb_lowercaseLetters = findViewById(R.id.t1_cb_lowercaseLetters);
        t1_cb_numbers = findViewById(R.id.t1_cb_numbers);
        t1_cb_specialChars = findViewById(R.id.t1_cb_specialChars);

        // get checkbox state: uppercase
        if (t1_cb_uppercaseLetters.isChecked()) {
            Log.v(TAG, "...adding uppercase to character pool");
            allowedChars = allowedChars + charPool_uppercaseLetters;
        }

        // get checkbox state: lowercase
        if (t1_cb_lowercaseLetters.isChecked()) {
            Log.v(TAG, "...adding lowercase to character pool");
            allowedChars = allowedChars + charPool_lowercaseLetters;
        }

        // get checkbox state: numbers
        if (t1_cb_numbers.isChecked()) {
            Log.v(TAG, "...adding numbers to character pool");
            allowedChars = allowedChars + charPool_numbers;
        }

        // get checkbox state: special chars
        if (t1_cb_specialChars.isChecked()) {
            Log.v(TAG, "...adding special chars to character pool");
            allowedChars = allowedChars + charPool_specialChars;
        }

        // Check if at least 1 pool is selected or not
        if (allowedChars.equals("")) {
            // get error string
            String cur_error = getResources().getString(R.string.t1_error_empty_char_pool);

            // Show error in log
            Log.e(TAG, cur_error);

            // show error as toast
            displayToastMessage(cur_error);
        } else {
            Log.v(TAG, "...character pool is configured to: " + allowedChars);

            // get password length
            EditText n_passwordLength = findViewById(R.id.t1_passwordLength);

            String s_passwordLength = n_passwordLength.getText().toString().trim();

            if ((s_passwordLength.equals("0")) || (s_passwordLength.isEmpty()) || (s_passwordLength.equals(""))) {
                Log.w(TAG, "...invalid password length detected, changing to default");
                i_passwordLength = 10;
                n_passwordLength.setText(Integer.toString(i_passwordLength), TextView.BufferType.EDITABLE);
            } else {
                i_passwordLength = Integer.parseInt(s_passwordLength);
            }
            Log.v(TAG, "...password length is set to " + Integer.toString(i_passwordLength));

            // password generation
            //
            char[] allowedCharsArray = allowedChars.toCharArray();
            chars = new char[i_passwordLength];
            random = new Random();
            for (int i = 0; i < i_passwordLength; i++) {
                chars[i] = allowedCharsArray[random.nextInt(allowedChars.length())];
            }

            // display the new password
            t1_generatedPassword.setText(chars, 0, i_passwordLength);

            // calculate entropy
            String entropy_results[];
            entropy_results = calculateEntropy(i_passwordLength, allowedChars.length());

            // show entropy results
            String entropy_text;
            entropy_text = entropy_results[0];
            String entropy_value;
            entropy_value = entropy_results[1];

            // Resulting password as string
            generatedPassword = t1_generatedPassword.getText().toString();

            // show result
            askUser(generatedPassword, entropy_text, entropy_value);
        }
    }


    // #############################################################################################
    // Tab 2: GENERATE A XKCD  PASSWORD
    // wordlists:  https://github.com/redacted/XKCD-password-generator/tree/master/xkcdpass/static
    //
    // TODO:
    // - configurable separator (drop-down)
    // - add Helper-option (--acrostic='chaos') -> Cxxx Hxxxx Axxxxx Oxxxxx Sxxxxxx
    // - configurable min word length
    // - configurable max word length
    // #############################################################################################
    public void on_generate_xkcd(View v) throws IOException {
        Log.v(TAG, "F: on_generate_xkcd");

        TextView t2_generatedPassword;

        // Reset content of password field
        t2_generatedPassword = findViewById(R.id.t2_generatedPassword);
        t2_generatedPassword.setText(null);

        Random randomGenerator = new Random();

        // get amount of words
        Log.v(TAG, "...get amount of words");
        EditText n_passwordLength = findViewById(R.id.t2_passwordLength);

        String s_passwordLength = n_passwordLength.getText().toString().trim();

        if ((s_passwordLength.equals("0")) || (s_passwordLength.isEmpty()) || (s_passwordLength.equals(""))) {
            Log.w(TAG, "...invalid amount of words, changing to default");
            i_passwordLength = 4;
            n_passwordLength.setText(Integer.toString(i_passwordLength), TextView.BufferType.EDITABLE);
        } else {
            i_passwordLength = Integer.parseInt(s_passwordLength);
        }
        Log.v(TAG, "...amount of words is set to " + Integer.toString(i_passwordLength));

        // get selected language
        Spinner t2_s_language_selection = findViewById(R.id.t2_languageSelection);
        String selected_language = t2_s_language_selection.getSelectedItem().toString();
        Log.v(TAG, "...selected language: " + selected_language);

        String name_of_language_wordlist;

        // Can't use resource strings in switch-statement because of:
        // Error: Constant expression required
        //
        //Thats why we are using an uly if/else
        if (selected_language.equals(getResources().getString(R.string.t2_lang_es))) {
            name_of_language_wordlist = "words_es.txt";
        } else if (selected_language.equals(getResources().getString(R.string.t2_lang_jp))) {
            name_of_language_wordlist = "words_jp.txt";
        } else if (selected_language.equals(getResources().getString(R.string.t2_lang_it))) {
            name_of_language_wordlist = "words_it.txt";
        } else if (selected_language.equals(getResources().getString(R.string.t2_lang_de))) {
            name_of_language_wordlist = "words_de.txt";
        } else if (selected_language.equals(getResources().getString(R.string.t2_lang_fi))) {
            name_of_language_wordlist = "words_fi.txt";
        } else {
            name_of_language_wordlist = "words_en.txt";
        }

        Log.v(TAG, "...Selected wordlist: " + name_of_language_wordlist);

        // read selected file line by line
        //
        List<String> myWords;
        myWords = new ArrayList<>();

        InputStream in;
        BufferedReader reader;
        String line;

        in = this.getAssets().open(name_of_language_wordlist);
        reader = new BufferedReader(new InputStreamReader(in));
        line = reader.readLine();
        while (line != null) {
            // add current line to List
            myWords.add(line);
            line = reader.readLine();
        }
        in.close();

        Log.v(TAG, "...available words in this language-list: " + Integer.toString(myWords.size()));

        // generate xkcd password from wordlist
        //
        StringBuilder generatedPassword = new StringBuilder();
        for (int i = 0; i < i_passwordLength; i++) {
            // generate a random int
            int randomInt = randomGenerator.nextInt(myWords.size());

            // pick random word based on random int
            String wordToDisplay = myWords.get(randomInt);

            // Make Uppercase first char
            wordToDisplay = wordToDisplay.substring(0, 1).toUpperCase(Locale.getDefault()) + wordToDisplay.substring(1);

            // append to current password
            generatedPassword.append(wordToDisplay);

            // add a splitting char between words if needed
            if (i + 1 < i_passwordLength) {
                generatedPassword.append("-");
            }
        }

        // display the new password
        //
        chars = generatedPassword.toString().toCharArray();
        t2_generatedPassword.setText(chars, 0, generatedPassword.length());

        // https://www.explainxkcd.com/wiki/index.php/936:_Password_Strength
        String entropy_results[];
        entropy_results = calculateEntropy(i_passwordLength, myWords.size());

        // show entropy results
        String entropy_text;
        entropy_text = entropy_results[0];
        String entropy_value;
        entropy_value = entropy_results[1];

        generatedPassword = new StringBuilder(t2_generatedPassword.getText().toString());

        // run result dialog for user
        askUser(generatedPassword.toString(), entropy_text, entropy_value);
    }


    // #############################################################################################
    // Tab 3: GENERATE A KATAKANA PASSWORD
    // #############################################################################################
    public void on_generate_katakana(View v) {
        Log.v(TAG, "on_generate_katakana");

        TextView t3_generatedPassword;

        // Reset content of password field
        t3_generatedPassword = findViewById(R.id.t3_generatedPassword);
        t3_generatedPassword.setText(null);

        // init some stuff
        //
        StringBuilder generatedPassword = new StringBuilder();
        Random random;
        int index_c;
        int index_v;

        // Define charsets
        //
        //final String[] foo = {"k", "s", "t", "n", "h", "m", "y", "r", "w"};  // plain katakana
        final String[] consonants = {"k", "s", "t", "n", "h", "m", "y", "r", "w", "f", "g", "z", "d", "b", "p", "K", "S", "T", "N", "H", "M", "Y", "R", "W", "F", "G", "Z", "D", "B", "P"}; // katakana + bonus
        final String[] vowels = {"a", "i", "u", "e", "o", "A", "U", "E", "O"}; // skipping uppercase i

        // get password length
        //
        Log.v(TAG, "...get password length");
        EditText n_passwordLength = findViewById(R.id.t3_passwordLength);

        String s_passwordLength = n_passwordLength.getText().toString().trim();

        if ((s_passwordLength.equals("0")) || (s_passwordLength.isEmpty()) || (s_passwordLength.equals(""))) {
            Log.w(TAG, "...invalid password length detected, changing to default");
            i_passwordLength = 10;
            n_passwordLength.setText(Integer.toString(i_passwordLength), TextView.BufferType.EDITABLE);
        } else {
            i_passwordLength = Integer.parseInt(s_passwordLength);
        }
        Log.v(TAG, "...password length is set to " + Integer.toString(i_passwordLength));

        // generate password
        //
        Log.v(TAG, "...generating password");
        for (int i = 0; i < i_passwordLength; i++) {
            // Odd: pick random consonants array string
            random = new Random();
            index_c = random.nextInt(consonants.length);

            // Even: pick random vowels array string
            random = new Random();
            index_v = random.nextInt(vowels.length);

            generatedPassword.append(consonants[index_c]).append(vowels[index_v]);
        }
        generatedPassword = new StringBuilder(generatedPassword.substring(0, i_passwordLength)); // Substring to match password length

        // display the new password
        //
        chars = generatedPassword.toString().toCharArray();
        t3_generatedPassword.setText(chars, 0, i_passwordLength);

        // entropy
        //
        int allowedChars = consonants.length + vowels.length; // get charset size

        String entropy_results[]; // prepare array for entropy values
        entropy_results = calculateEntropy(i_passwordLength, allowedChars); // get entropy values

        // show entropy results
        String entropy_text;
        entropy_text = entropy_results[0];

        String entropy_value;
        entropy_value = entropy_results[1];

        generatedPassword = new StringBuilder(t3_generatedPassword.getText().toString());

        // run result dialog for user
        askUser(generatedPassword.toString(), entropy_text, entropy_value);
    }


    // #############################################################################################
    // Tab 4: QUERY PWNED DATABASE
    // #############################################################################################
    public void on_click_query_pwned_database(View v) throws IOException {
        Log.v(TAG, "F: on_click_query_pwned_database");

        final String userPassword;

        // get text from textedit
        EditText t4_userPassword;
        t4_userPassword = findViewById(R.id.t4_userPassword);
        userPassword = t4_userPassword.getText().toString();

        if (userPassword.length() == 0) {
            // get error string
            String cur_error = getResources().getString(R.string.t4_error_password);

            // Show error in log
            Log.e(TAG, cur_error);

            // show error as toast
            displayToastMessage(cur_error);
        } else {
            try {
                checkPWNEDPasswords(userPassword);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // #############################################################################################
    // A placeholder fragment containing a simple view.
    // #############################################################################################
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section number.
         */
        /*
        public static PlaceholderFragment newInstance(int sectionNumber) {
            Log.v(TAG, "F: PlaceholderFragment");
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }
        */

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Log.v(TAG, "F: onCreateView");

            //View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            //TextView textView = rootView.findViewById(R.id.section_label);
            //return rootView;
            return null;
        }
    }


    // #############################################################################################
    // A {@link FragmentPagerAdapter} that returns a fragment corresponding to
    // one of the sections/tabs/pages.
    // #############################################################################################
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            Log.v(TAG, "F: SectionsPagerAdapter");
        }

        @Override
        public Fragment getItem(int position) {
            Log.v(TAG, "F: getItem");
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).

            switch (position) {
                case 0:
                    return new TabDefault();

                case 1:
                    return new TabXKCD();

                case 2:
                    return new TabKana();

                case 3:
                    return new TabPwned();

                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            Log.v(TAG, "F: getCount");
            // Show 3 total pages.
            return 4;
        }
    }
}