package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

public class PasscodeFragment extends BaseFragment {

    private EditText passcodeEditText;
    private TextView promptTextView;
    private final ImageView[] dots = new ImageView[4];
    private boolean isFirstTime;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View createView(Context context) {
        // Inflate the layout we created
        fragmentView = LayoutInflater.from(context).inflate(R.layout.fragment_passcode, null, false);

        // We set the action bar title for our new screen.
        actionBar.setTitle("Enter Passcode");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment(); // This handles the back button press.
                }
            }
        });

        // Get the SharedPreferences file where we'll store the passcode
        sharedPreferences = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE);

        // Find all our UI elements
        promptTextView = fragmentView.findViewById(R.id.passcode_prompt_text);
        passcodeEditText = fragmentView.findViewById(R.id.passcode_edit_text);
        dots[0] = fragmentView.findViewById(R.id.dot_1);
        dots[1] = fragmentView.findViewById(R.id.dot_2);
        dots[2] = fragmentView.findViewById(R.id.dot_3);
        dots[3] = fragmentView.findViewById(R.id.dot_4);

        // Check if a passcode has been saved before
        isFirstTime = sharedPreferences.getString("vault_passcode", null) == null;

        if (isFirstTime) {
            promptTextView.setText("Create Passcode");
        } else {
            promptTextView.setText("Enter Passcode");
        }

        setupInputListener();

        return fragmentView;
    }

    private void setupInputListener() {
        passcodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Update the dots every time the user types a number
                updateDots(s.length());

                // If 4 digits have been entered, check the passcode
                if (s.length() == 4) {
                    checkPasscode(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateDots(int length) {
        for (int i = 0; i < 4; i++) {
            if (i < length) {
                dots[i].setImageResource(R.drawable.passcode_dot_filled);
            } else {
                dots[i].setImageResource(R.drawable.passcode_dot_empty);
            }
        }
    }

    private void checkPasscode(String input) {
        if (isFirstTime) {
            // If it's the first time, save the new passcode
            sharedPreferences.edit().putString("vault_passcode", input).apply();
            Toast.makeText(getParentActivity(), "Passcode Set", Toast.LENGTH_SHORT).show();
            // Go to the vault
            presentFragment(new VaultFragment(), true);
        } else {
            // If it's not the first time, check against the saved passcode
            String savedPasscode = sharedPreferences.getString("vault_passcode", null);
            if (input.equals(savedPasscode)) {
                // Correct passcode, go to the vault
                presentFragment(new VaultFragment(), true);
            } else {
                // Incorrect passcode
                Toast.makeText(getParentActivity(), "Incorrect Passcode", Toast.LENGTH_SHORT).show();
                passcodeEditText.setText(""); // Clear the input field
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Automatically focus the invisible EditText and show the number keyboard
        if (passcodeEditText != null) {
            passcodeEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getParentActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(passcodeEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }
}