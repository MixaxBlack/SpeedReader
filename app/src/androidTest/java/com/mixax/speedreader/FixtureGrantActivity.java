package com.mixax.speedreader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** The test provider's owning UID issues the same scoped tree grant as a picker. */
public class FixtureGrantActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Uri tree=DocumentsContract.buildTreeDocumentUri(FixtureDocumentsProvider.AUTHORITY,"root");
        if(getIntent().getBooleanExtra("revoke",false))revokeUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        else grantUriPermission("com.mixax.speedreader",tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        finish();
    }
}
