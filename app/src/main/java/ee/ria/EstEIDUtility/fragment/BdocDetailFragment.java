package ee.ria.EstEIDUtility.fragment;

import android.app.Activity;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

import ee.ria.EstEIDUtility.BuildConfig;
import ee.ria.EstEIDUtility.R;
import ee.ria.EstEIDUtility.activity.BdocDetailActivity;
import ee.ria.EstEIDUtility.domain.X509Cert;
import ee.ria.EstEIDUtility.service.ServiceCreatedCallback;
import ee.ria.EstEIDUtility.service.TokenServiceConnection;
import ee.ria.EstEIDUtility.util.Constants;
import ee.ria.EstEIDUtility.util.FileUtils;
import ee.ria.EstEIDUtility.util.NotificationUtil;
import ee.ria.libdigidocpp.Container;
import ee.ria.libdigidocpp.Signature;
import ee.ria.libdigidocpp.Signatures;
import ee.ria.token.tokenservice.token.Token;
import ee.ria.token.tokenservice.TokenService;
import ee.ria.token.tokenservice.callback.CertCallback;
import ee.ria.token.tokenservice.callback.RetryCounterCallback;
import ee.ria.token.tokenservice.callback.SignCallback;
import ee.ria.token.tokenservice.token.PinVerificationException;

public class BdocDetailFragment extends Fragment {

    public static final String TAG = "BDOC_DETAIL_FRAGMENT";

    private EditText title;
    private TextView body;
    private TextView fileInfoTextView;
    private AlertDialog pinDialog;
    private EditText pinText;
    private TextView enterPinText;

    private Button addFileButton;
    private Button addSignatureButton;
    private Button sendButton;
    private ImageView editBdoc;

    private String fileName;
    private File bdocFile;

    private TokenService tokenService;
    private TokenServiceConnection tokenServiceConnection;

    private boolean tokenServiceBound;

    private void unBindTokenService() {
        addSignatureButton.setEnabled(false);
        if (tokenServiceConnection != null && tokenServiceBound) {
            getActivity().unbindService(tokenServiceConnection);
            tokenServiceBound = false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        connectTokenService();
    }

    private void connectTokenService() {
        tokenServiceConnection = new TokenServiceConnection(getActivity(), new TokenServiceCreatedCallback());
        tokenServiceConnection.connectService();
        tokenServiceBound = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        unBindTokenService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup containerView, Bundle savedInstanceState) {
        View fragLayout = inflater.inflate(R.layout.fragment_bdoc_detail, containerView, false);

        fileName = getArguments().getString(Constants.BDOC_NAME);

        bdocFile = FileUtils.getBdocFile(getContext().getFilesDir(), fileName);
        
        createFilesListFragment();
        createSignatureListFragment();

        title = (EditText) fragLayout.findViewById(R.id.listDocName);
        title.setKeyListener(null);

        body = (TextView) fragLayout.findViewById(R.id.listDocLocation);
        fileInfoTextView = (TextView) fragLayout.findViewById(R.id.dbocInfo);

        editBdoc = (ImageView) fragLayout.findViewById(R.id.editBdoc);
        addFileButton = (Button) fragLayout.findViewById(R.id.addFile);
        addSignatureButton = (Button) fragLayout.findViewById(R.id.addSignature);
        sendButton = (Button) fragLayout.findViewById(R.id.sendButton);
        createPinDialog();

        return fragLayout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        addFileButton.setOnClickListener(new AddFileButtonListener());
        addSignatureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tokenService.readCert(Token.CertType.CertSign, new SameSignatureCallback());
            }
        });
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Uri uriToFile = FileProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID, bdocFile);
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uriToFile);
                shareIntent.setType("application/zip");
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.upload_to)));
            }
        });
        editBdoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                title.setInputType(EditorInfo.TYPE_CLASS_TEXT);
                InputMethodManager input = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                input.showSoftInput(title, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        String fileInfo = getContext().getResources().getString(R.string.file_info);
        fileInfo = String.format(fileInfo, FilenameUtils.getExtension(fileName).toUpperCase(), FileUtils.getKilobytes(bdocFile.length()));

        fileInfoTextView.setText(fileInfo);
        title.setText(fileName);
        body.setText(fileName);
    }

    class SignTaskCallback implements SignCallback {
        Signature signature;
        Container container;

        SignTaskCallback(Container container, Signature signature) {
            this.signature = signature;
            this.container = container;
        }

        @Override
        public void onSignResponse(byte[] signatureBytes) {
            signature.setSignatureValue(signatureBytes);
            container.save();
            BdocDetailFragment bdocDetailFragment = (BdocDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BdocDetailFragment.TAG);
            BdocSignaturesFragment bdocSignaturesFragment = (BdocSignaturesFragment) bdocDetailFragment.getChildFragmentManager().findFragmentByTag(BdocSignaturesFragment.TAG);
            bdocSignaturesFragment.addSignature(signature);
            enterPinText.setText(getResources().getString(R.string.enter_pin));
            pinText.setText("");
        }

        @Override
        public void onSignError(Exception e, PinVerificationException pinVerificationException) {
            if (pinVerificationException != null) {
                NotificationUtil.showNotification(getActivity(), R.string.pin_verification_failed, NotificationUtil.NotificationType.ERROR);
                pinText.setText("");
                tokenService.readRetryCounter(pinVerificationException.getPinType(), new RetryCounterTaskCallback());
            } else {
                Toast.makeText(getActivity(), e.getMessage(), NotificationUtil.NotificationDuration.SHORT.duration).show();
            }
        }
    }

    private class RetryCounterTaskCallback implements RetryCounterCallback {
        @Override
        public void onCounterRead(byte counterByte) {
            enterPinText.setText(String.format(getResources().getString(R.string.enter_pin_retries_left), String.valueOf(counterByte)));
        }
    }

    private class AddFileButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Container container = FileUtils.getContainer(getContext().getFilesDir(), fileName);
            if (container.signatures().size() > 0) {
                NotificationUtil.showNotification(getActivity(), R.string.add_file_remove_signatures, NotificationUtil.NotificationType.ERROR);
                return;
            }
            Intent intent = new Intent()
                    .setType("*/*")
                    .setAction(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            getActivity().startActivityForResult(
                    Intent.createChooser(intent, getResources().getString(R.string.select_file)),
                    BdocDetailActivity.CHOOSE_FILE_REQUEST);
        }
    }

    private void createFilesListFragment() {
        BdocFilesFragment filesFragment = (BdocFilesFragment) getChildFragmentManager().findFragmentByTag(BdocFilesFragment.TAG);
        if (filesFragment != null) {
            return;
        }
        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        Bundle bundle = new Bundle();
        bundle.putString(Constants.BDOC_NAME, fileName);

        filesFragment = new BdocFilesFragment();
        filesFragment.setArguments(bundle);
        fragmentTransaction.add(R.id.filesListLayout, filesFragment, BdocFilesFragment.TAG);
        fragmentTransaction.commit();
    }

    private void createSignatureListFragment() {
        BdocSignaturesFragment signaturesFragment = (BdocSignaturesFragment) getChildFragmentManager().findFragmentByTag(BdocSignaturesFragment.TAG);
        if (signaturesFragment != null) {
            return;
        }
        FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();

        Bundle bundle = new Bundle();
        bundle.putString(Constants.BDOC_NAME, fileName);

        signaturesFragment = new BdocSignaturesFragment();
        signaturesFragment.setArguments(bundle);
        fragmentTransaction.add(R.id.signaturesListLayout, signaturesFragment, BdocSignaturesFragment.TAG);
        fragmentTransaction.commit();
    }

    private void createPinDialog() {
        View view = getActivity().getLayoutInflater().inflate(R.layout.enter_pin, null);

        enterPinText = (TextView) view.findViewById(R.id.enterPin);
        pinText = (EditText) view.findViewById(R.id.pin);
        pinText.setHint(Token.PinType.PIN2.name());
        InputFilter[] inputFilters = {new InputFilter.LengthFilter(5)};
        pinText.setFilters(inputFilters);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setPositiveButton(R.string.sign_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                tokenService.readCert(Token.CertType.CertSign, new CertificateInfoCallback());
            }
        }).setNegativeButton(R.string.cancel, null);
        builder.setView(view);
        pinDialog = builder.create();
    }

    class TokenServiceCreatedCallback implements ServiceCreatedCallback {

        @Override
        public void created(Service service) {
            tokenService = (TokenService) service;
            addSignatureButton.setEnabled(true);
        }

        @Override
        public void failed() {
            Log.d(TAG, "failed to bind token service");
            addSignatureButton.setEnabled(false);
        }

        @Override
        public void disconnected() {
            Log.d(TAG, "token service disconnected");
            addSignatureButton.setEnabled(false);
        }
    }

    class CertificateInfoCallback implements CertCallback {
        @Override
        public void onCertificateResponse(byte[] cert) {
            Container container = FileUtils.getContainer(getContext().getFilesDir(), fileName);
            Signature signature = container.prepareWebSignature(cert);
            byte[] dataToSign = signature.dataToSign();
            String pin2 = pinText.getText().toString();
            tokenService.sign(Token.PinType.PIN2, pin2, dataToSign, new SignTaskCallback(container, signature));
        }

        @Override
        public void onCertificateError(Exception e) {
            Toast.makeText(getActivity(), e.getMessage(), NotificationUtil.NotificationDuration.SHORT.duration).show();
        }

    }

    class SameSignatureCallback implements CertCallback {
        @Override
        public void onCertificateResponse(byte[] cert) {
            Container container = FileUtils.getContainer(getContext().getFilesDir(), fileName);
            if (isSignedByPerson(container.signatures(), container, cert)) {
                NotificationUtil.showNotification(getActivity(), R.string.already_signed_by_person, NotificationUtil.NotificationType.WARNING);
                return;
            }

            pinDialog.show();
            pinDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            final Button positiveButton = pinDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);
            pinText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (pinText.getText().length() == 5) {
                        positiveButton.setEnabled(true);
                    } else if (positiveButton.isEnabled()) {
                        positiveButton.setEnabled(false);
                    }
                }
            });
        }

        private boolean isSignedByPerson(Signatures signatures, Container container, byte[] cert) {
            Signature signature = container.prepareWebSignature(cert);
            X509Cert x509Cert = new X509Cert(signature.signingCertificateDer());
            for (int i = 0; i < signatures.size(); i++) {
                Signature s = signatures.get(i);
                X509Cert c = new X509Cert(s.signingCertificateDer());
                if (c.getCertificate().equals(x509Cert.getCertificate())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onCertificateError(Exception e) {}

    }

}
