/*
 * Copyright (C) 2017 Schürmann & Breitmoser GbR
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;


import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.ExportResult;
import org.sufficientlysecure.keychain.provider.TemporaryFileProvider;
import org.sufficientlysecure.keychain.service.BackupKeyringParcel;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.ui.base.CryptoOperationFragment;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.ActionListener;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.ui.widget.ToolableViewAnimator;
import org.sufficientlysecure.keychain.util.Numeric9x4PassphraseUtil;
import org.sufficientlysecure.keychain.util.FileHelper;
import org.sufficientlysecure.keychain.util.Passphrase;

public class BackupCodeFragment extends CryptoOperationFragment<BackupKeyringParcel, ExportResult>
        implements OnBackStackChangedListener {

    public static final String ARG_BACKUP_CODE = "backup_code";
    public static final String BACK_STACK_INPUT = "state_display";
    public static final String ARG_EXPORT_SECRET = "export_secret";
    public static final String ARG_EXECUTE_BACKUP_OPERATION = "execute_backup_operation";
    public static final String ARG_MASTER_KEY_IDS = "master_key_ids";
    public static final String ARG_CURRENT_STATE = "current_state";


    public static final int REQUEST_SAVE = 1;
    public static final String ARG_BACK_STACK = "back_stack";

    // argument variables
    private boolean mExportSecret;
    private long[] mMasterKeyIds;
    Passphrase mBackupCode;
    private boolean mExecuteBackupOperation;

    private TextView[] mCodeEditText;

    private ToolableViewAnimator mStatusAnimator, mTitleAnimator, mCodeFieldsAnimator;
    private Integer mBackStackLevel;

    private Uri mCachedBackupUri;
    private boolean mShareNotSave;
    private boolean mDebugModeAcceptAnyCode;

    public static BackupCodeFragment newInstance(long[] masterKeyIds, boolean exportSecret,
                                                 boolean executeBackupOperation) {
        BackupCodeFragment frag = new BackupCodeFragment();

        Bundle args = new Bundle();
        args.putParcelable(ARG_BACKUP_CODE, Numeric9x4PassphraseUtil.generateNumeric9x4Passphrase());
        args.putLongArray(ARG_MASTER_KEY_IDS, masterKeyIds);
        args.putBoolean(ARG_EXPORT_SECRET, exportSecret);
        args.putBoolean(ARG_EXECUTE_BACKUP_OPERATION, executeBackupOperation);
        frag.setArguments(args);

        return frag;
    }

    enum BackupCodeState {
        STATE_UNINITIALIZED, STATE_DISPLAY, STATE_INPUT, STATE_INPUT_ERROR, STATE_OK
    }

    BackupCodeState mCurrentState = BackupCodeState.STATE_UNINITIALIZED;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Constants.DEBUG) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (Constants.DEBUG) {
            inflater.inflate(R.menu.backup_fragment_debug_menu, menu);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (Constants.DEBUG && item.getItemId() == R.id.debug_accept_any_log) {
            boolean newCheckedState = !item.isChecked();
            item.setChecked(newCheckedState);
            mDebugModeAcceptAnyCode = newCheckedState;
            if (newCheckedState && TextUtils.isEmpty(mCodeEditText[0].getText())) {
                mCodeEditText[0].setText("1234");
                mCodeEditText[1].setText("5678");
                mCodeEditText[2].setText("9012");
                mCodeEditText[3].setText("3456");
                mCodeEditText[4].setText("7890");
                mCodeEditText[5].setText("1234");
                mCodeEditText[6].setText("5678");
                mCodeEditText[7].setText("9012");
                mCodeEditText[8].setText("3456");
                Notify.create(getActivity(), "Actual backup code is all '1's", Style.WARN).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void switchState(BackupCodeState state, boolean animate) {

        switch (state) {
            case STATE_UNINITIALIZED:
                throw new AssertionError("can't switch to uninitialized state, this is a bug!");

            case STATE_DISPLAY:
                mTitleAnimator.setDisplayedChild(0, animate);
                mStatusAnimator.setDisplayedChild(0, animate);
                mCodeFieldsAnimator.setDisplayedChild(0, animate);
                break;

            case STATE_INPUT:
                mTitleAnimator.setDisplayedChild(1, animate);
                mStatusAnimator.setDisplayedChild(1, animate);
                mCodeFieldsAnimator.setDisplayedChild(1, animate);
                for (TextView editText : mCodeEditText) {
                    editText.setText("");
                }

                pushBackStackEntry();

                break;

            case STATE_INPUT_ERROR: {
                mTitleAnimator.setDisplayedChild(1, false);
                mStatusAnimator.setDisplayedChild(2, animate);
                mCodeFieldsAnimator.setDisplayedChild(1, false);

                hideKeyboard();

                if (animate) {
                    @ColorInt int black = mCodeEditText[0].getCurrentTextColor();
                    @ColorInt int red = getResources().getColor(R.color.android_red_dark);
                    animateFlashText(mCodeEditText, black, red, false);
                }

                break;
            }

            case STATE_OK: {
                mTitleAnimator.setDisplayedChild(2, animate);
                mCodeFieldsAnimator.setDisplayedChild(1, false);
                if (mExecuteBackupOperation) {
                    mStatusAnimator.setDisplayedChild(3, animate);
                } else {
                    mStatusAnimator.setDisplayedChild(1, animate);
                }

                hideKeyboard();

                for (TextView editText : mCodeEditText) {
                    editText.setEnabled(false);
                }

                @ColorInt int green = getResources().getColor(R.color.android_green_dark);
                if (animate) {
                    @ColorInt int black = mCodeEditText[0].getCurrentTextColor();
                    animateFlashText(mCodeEditText, black, green, true);
                } else {
                    for (TextView textView : mCodeEditText) {
                        textView.setTextColor(green);
                    }
                }

                popBackStackNoAction();

                // special case for remote API, see RemoteBackupActivity
                if (!mExecuteBackupOperation) {
                    // wait for animation to finish...
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startBackup();
                        }
                    }, 2000);
                }

                break;
            }

        }

        mCurrentState = state;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.backup_code_fragment, container, false);

        Bundle args = getArguments();
        mBackupCode = args.getParcelable(ARG_BACKUP_CODE);
        mMasterKeyIds = args.getLongArray(ARG_MASTER_KEY_IDS);
        mExportSecret = args.getBoolean(ARG_EXPORT_SECRET);
        mExecuteBackupOperation = args.getBoolean(ARG_EXECUTE_BACKUP_OPERATION, true);

        mCodeEditText = getTransferCodeTextViews(view, R.id.transfer_code_input);

        {
            TextView[] codeDisplayText = getTransferCodeTextViews(view, R.id.transfer_code_display);

            // set backup code in code TextViews
            char[] backupCode = mBackupCode.getCharArray();
            for (int i = 0; i < codeDisplayText.length; i++) {
                codeDisplayText[i].setText(backupCode, i * 5, 4);
            }

            // set background to null in TextViews - this will retain padding from EditText style!
            for (TextView textView : codeDisplayText) {
                // noinspection deprecation, setBackground(Drawable) is API level >=16
                textView.setBackgroundDrawable(null);
            }
        }

        setupEditTextFocusNext(mCodeEditText);
        setupEditTextSuccessListener(mCodeEditText);

        mStatusAnimator = view.findViewById(R.id.button_bar_animator);
        mTitleAnimator = view.findViewById(R.id.title_animator);
        mCodeFieldsAnimator = view.findViewById(R.id.code_animator);

        View backupInput = view.findViewById(R.id.button_backup_input);
        backupInput.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switchState(BackupCodeState.STATE_INPUT, true);
            }
        });

        view.findViewById(R.id.button_backup_save).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mShareNotSave = false;
                startBackup();
            }
        });

        view.findViewById(R.id.button_backup_share).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mShareNotSave = true;
                startBackup();
            }
        });

        view.findViewById(R.id.button_backup_back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fragMan = getFragmentManager();
                if (fragMan != null) {
                    fragMan.popBackStack();
                }
            }
        });

        view.findViewById(R.id.button_faq).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showFaq();
            }
        });
        return view;
    }

    @NonNull
    private TextView[] getTransferCodeTextViews(View view, int transferCodeViewGroupId) {
        ViewGroup transferCodeGroup = view.findViewById(transferCodeViewGroupId);
        TextView[] codeDisplayText = new TextView[9];
        codeDisplayText[0] = transferCodeGroup.findViewById(R.id.transfer_code_block_1);
        codeDisplayText[1] = transferCodeGroup.findViewById(R.id.transfer_code_block_2);
        codeDisplayText[2] = transferCodeGroup.findViewById(R.id.transfer_code_block_3);
        codeDisplayText[3] = transferCodeGroup.findViewById(R.id.transfer_code_block_4);
        codeDisplayText[4] = transferCodeGroup.findViewById(R.id.transfer_code_block_5);
        codeDisplayText[5] = transferCodeGroup.findViewById(R.id.transfer_code_block_6);
        codeDisplayText[6] = transferCodeGroup.findViewById(R.id.transfer_code_block_7);
        codeDisplayText[7] = transferCodeGroup.findViewById(R.id.transfer_code_block_8);
        codeDisplayText[8] = transferCodeGroup.findViewById(R.id.transfer_code_block_9);
        return codeDisplayText;
    }

    private void showFaq() {
        HelpActivity.startHelpActivity(getActivity(), HelpActivity.TAB_FAQ);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            int savedBackStack = savedInstanceState.getInt(ARG_BACK_STACK);
            if (savedBackStack >= 0) {
                mBackStackLevel = savedBackStack;
                // unchecked use, we know that this one is available in onViewCreated
                getFragmentManager().addOnBackStackChangedListener(this);
            }
            BackupCodeState savedState = BackupCodeState.values()[savedInstanceState.getInt(ARG_CURRENT_STATE)];
            switchState(savedState, false);
        } else if (mCurrentState == BackupCodeState.STATE_UNINITIALIZED) {
            switchState(BackupCodeState.STATE_DISPLAY, true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_CURRENT_STATE, mCurrentState.ordinal());
        outState.putInt(ARG_BACK_STACK, mBackStackLevel == null ? -1 : mBackStackLevel);
    }

    private void setupEditTextSuccessListener(final TextView[] backupCodes) {
        for (TextView backupCode : backupCodes) {

            backupCode.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 4) {
                        throw new AssertionError("max length of each field is 4!");
                    }

                    boolean inInputState = mCurrentState == BackupCodeState.STATE_INPUT
                            || mCurrentState == BackupCodeState.STATE_INPUT_ERROR;
                    boolean partIsComplete = s.length() == 4;
                    if (!inInputState || !partIsComplete) {
                        return;
                    }

                    checkIfCodeIsCorrect();
                }
            });

        }
    }

    private void checkIfCodeIsCorrect() {

        if (Constants.DEBUG && mDebugModeAcceptAnyCode) {
            switchState(BackupCodeState.STATE_OK, true);
            return;
        }

        StringBuilder backupCodeInput = new StringBuilder(26);
        for (TextView editText : mCodeEditText) {
            if (editText.getText().length() < 4) {
                return;
            }
            backupCodeInput.append(editText.getText());
            backupCodeInput.append('-');
        }
        backupCodeInput.deleteCharAt(backupCodeInput.length() - 1);

        // if they don't match, do nothing
        if (backupCodeInput.toString().equals(mBackupCode)) {
            switchState(BackupCodeState.STATE_OK, true);
            return;
        }

        switchState(BackupCodeState.STATE_INPUT_ERROR, true);

    }

    private static void animateFlashText(
            final TextView[] textViews, int color1, int color2, boolean staySecondColor) {

        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), color1, color2);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                for (TextView textView : textViews) {
                    textView.setTextColor((Integer) animator.getAnimatedValue());
                }
            }
        });
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(staySecondColor ? 4 : 5);
        anim.setDuration(180);
        anim.setInterpolator(new AccelerateInterpolator());
        anim.start();

    }

    private static void setupEditTextFocusNext(final TextView[] backupCodes) {
        for (int i = 0; i < backupCodes.length - 1; i++) {

            final int next = i + 1;

            backupCodes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean inserting = before < count;
                    boolean cursorAtEnd = (start + count) == 4;

                    if (inserting && cursorAtEnd) {
                        backupCodes[next].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

        }
    }

    private void pushBackStackEntry() {
        if (mBackStackLevel != null) {
            return;
        }
        FragmentManager fragMan = getFragmentManager();
        mBackStackLevel = fragMan.getBackStackEntryCount();
        fragMan.beginTransaction().addToBackStack(BACK_STACK_INPUT).commit();
        fragMan.addOnBackStackChangedListener(this);
    }

    private void popBackStackNoAction() {
        FragmentManager fragMan = getFragmentManager();
        fragMan.removeOnBackStackChangedListener(this);
        fragMan.popBackStackImmediate(BACK_STACK_INPUT, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        mBackStackLevel = null;
    }

    @Override
    public void onBackStackChanged() {
        FragmentManager fragMan = getFragmentManager();
        if (mBackStackLevel != null && fragMan.getBackStackEntryCount() == mBackStackLevel) {
            fragMan.removeOnBackStackChangedListener(this);
            switchState(BackupCodeState.STATE_DISPLAY, true);
            mBackStackLevel = null;
        }
    }

    private void startBackup() {

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String filename = Constants.FILE_ENCRYPTED_BACKUP_PREFIX + date
                + (mExportSecret ? Constants.FILE_EXTENSION_ENCRYPTED_BACKUP_SECRET
                : Constants.FILE_EXTENSION_ENCRYPTED_BACKUP_PUBLIC);

        Passphrase passphrase = new Passphrase(mBackupCode.getCharArray());
        if (Constants.DEBUG && mDebugModeAcceptAnyCode) {
            passphrase = new Passphrase("1111-1111-1111-1111-1111-1111-1111-1111-1111");
        }

        // if we don't want to execute the actual operation outside of this activity, drop out here
        if (!mExecuteBackupOperation) {
            ((BackupActivity) getActivity()).handleBackupOperation(
                    CryptoInputParcel.createCryptoInputParcel(passphrase));
            return;
        }

        if (mCachedBackupUri == null) {
            mCachedBackupUri = TemporaryFileProvider.createFile(activity, filename,
                    Constants.MIME_TYPE_ENCRYPTED_ALTERNATE);

            cryptoOperation(CryptoInputParcel.createCryptoInputParcel(passphrase));
            return;
        }

        if (mShareNotSave) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Constants.MIME_TYPE_ENCRYPTED_ALTERNATE);
            intent.putExtra(Intent.EXTRA_STREAM, mCachedBackupUri);
            startActivity(intent);
        } else {
            saveFile(filename, false);
        }

    }

    private void saveFile(final String filename, boolean overwrite) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        // for kitkat and above, we have the document api
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            FileHelper.saveDocument(this, filename, Constants.MIME_TYPE_ENCRYPTED_ALTERNATE, REQUEST_SAVE);
            return;
        }

        if (!Constants.Path.APP_DIR.mkdirs()) {
            Notify.create(activity, R.string.snack_backup_error_saving, Style.ERROR).show();
            return;
        }

        File file = new File(Constants.Path.APP_DIR, filename);

        if (!overwrite && file.exists()) {
            Notify.create(activity, R.string.snack_backup_exists, Style.WARN, new ActionListener() {
                @Override
                public void onAction() {
                    saveFile(filename, true);
                }
            }, R.string.snack_btn_overwrite).show();
            return;
        }

        try {
            FileHelper.copyUriData(activity, mCachedBackupUri, Uri.fromFile(file));
            Notify.create(activity, R.string.snack_backup_saved_dir, Style.OK).show();
        } catch (IOException e) {
            Notify.create(activity, R.string.snack_backup_error_saving, Style.ERROR).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SAVE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (resultCode != FragmentActivity.RESULT_OK) {
            return;
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        try {
            Uri outputUri = data.getData();
            FileHelper.copyUriData(activity, mCachedBackupUri, outputUri);
            Notify.create(activity, R.string.snack_backup_saved, Style.OK).show();
        } catch (IOException e) {
            Notify.create(activity, R.string.snack_backup_error_saving, Style.ERROR).show();
        }
    }

    @Nullable
    @Override
    public BackupKeyringParcel createOperationInput() {
        return BackupKeyringParcel
                .create(mMasterKeyIds, mExportSecret, true, true, mCachedBackupUri);
    }

    @Override
    public void onCryptoOperationSuccess(ExportResult result) {
        startBackup();
    }

    @Override
    public void onCryptoOperationError(ExportResult result) {
        result.createNotify(getActivity()).show();
        mCachedBackupUri = null;
    }

    @Override
    public void onCryptoOperationCancelled() {
        mCachedBackupUri = null;
    }

}
