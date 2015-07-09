package org.sufficientlysecure.keychain.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.ContextThemeWrapper;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.ui.dialog.CustomAlertDialogBuilder;

public class UploadRetryDialogActivity extends FragmentActivity {

    public static final String EXTRA_CRYPTO_INPUT = "extra_crypto_input";

    public static final String RESULT_CRYPTO_INPUT = "result_crypto_input";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UploadRetryDialogFragment.newInstance().show(getSupportFragmentManager(),
                "uploadRetryDialog");
    }

    public static class UploadRetryDialogFragment extends DialogFragment {
        public static UploadRetryDialogFragment newInstance() {
            return new UploadRetryDialogFragment();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            ContextThemeWrapper theme = new ContextThemeWrapper(getActivity(),
                    R.style.Theme_AppCompat_Light_Dialog);

            CustomAlertDialogBuilder dialogBuilder = new CustomAlertDialogBuilder(theme);
            dialogBuilder.setTitle(R.string.retry_up_dialog_title);
            dialogBuilder.setMessage(R.string.retry_up_dialog_message);

            dialogBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().setResult(RESULT_CANCELED);
                    getActivity().finish();
                }
            });

            dialogBuilder.setPositiveButton(R.string.retry_up_dialog_btn_reupload,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent();
                    intent.putExtra(RESULT_CRYPTO_INPUT, getActivity()
                            .getIntent().getParcelableExtra(EXTRA_CRYPTO_INPUT));
                    getActivity().setResult(RESULT_OK, intent);
                    getActivity().finish();
                }
            });

            return dialogBuilder.show();
        }
    }
}
