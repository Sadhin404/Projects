package com.shadhinkhan.grabit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Download settings: where finished files go (a folder the user picks,
 *  via the system folder picker) and how many downloads may run at the
 *  same time (1-4; extra downloads queue). */
public class DownloadSettingsActivity extends ThemedActivity {
    private static final int REQ_FOLDER = 41;
    private TextView folderValue;
    private Button resetBtn;
    private final TextView[] parChips = new TextView[4];

    private SharedPreferences prefs() { return getSharedPreferences("grabit_prefs", MODE_PRIVATE); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        initTheme();
        LinearLayout page = chrome("Download settings");

        // ---- download folder ----
        page.addView(sectionLabel("Download folder"));
        LinearLayout fCard = new LinearLayout(this);
        fCard.setOrientation(LinearLayout.VERTICAL);
        fCard.setBackground(cardBg());
        fCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams fcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fcp.setMargins(0, dp(8), 0, 0);
        page.addView(fCard, fcp);
        folderValue = new TextView(this);
        folderValue.setTextColor(INK); folderValue.setTextSize(14.5f); folderValue.setTypeface(Typeface.DEFAULT_BOLD);
        fCard.addView(folderValue);
        TextView fNote = new TextView(this);
        fNote.setText("Finished downloads are copied to this folder. Until you pick one, videos go to Movies/Zynox, music to Music/Zynox and photos to Pictures/Zynox.");
        fNote.setTextColor(GREY); fNote.setTextSize(12.5f);
        fNote.setPadding(0, dp(4), 0, dp(12));
        fCard.addView(fNote);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        fCard.addView(btnRow);
        Button choose = new Button(this);
        choose.setText("Choose folder");
        choose.setTextColor(Color.WHITE); choose.setTextSize(14f); choose.setTypeface(Typeface.DEFAULT_BOLD);
        choose.setAllCaps(false);
        choose.setBackground(violetBg(12));
        btnRow.addView(choose, new LinearLayout.LayoutParams(0, dp(46), 1));
        choose.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(i, REQ_FOLDER); }
            catch (Throwable e) { Toast.makeText(this, "No folder picker available on this phone", Toast.LENGTH_LONG).show(); }
        });
        resetBtn = new Button(this);
        resetBtn.setText("Reset");
        resetBtn.setTextColor(INK); resetBtn.setTextSize(14f);
        resetBtn.setAllCaps(false);
        resetBtn.setBackground(chipBg(false));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(46), 1);
        rlp.setMargins(dp(10), 0, 0, 0);
        btnRow.addView(resetBtn, rlp);
        resetBtn.setOnClickListener(v -> {
            prefs().edit().remove("dl_folder_uri").remove("dl_folder_label").apply();
            refreshFolder();
            Toast.makeText(this, "Back to the default Zynox folders", Toast.LENGTH_SHORT).show();
        });
        refreshFolder();

        // ---- multiple downloads ----
        page.addView(sectionLabel("Multiple downloads"));
        LinearLayout mCard = new LinearLayout(this);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setBackground(cardBg());
        mCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mcp.setMargins(0, dp(8), 0, 0);
        page.addView(mCard, mcp);
        TextView mTitle = new TextView(this);
        mTitle.setText("Download files together");
        mTitle.setTextColor(INK); mTitle.setTextSize(14.5f); mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mCard.addView(mTitle);
        TextView mNote = new TextView(this);
        mNote.setText("How many files may download at the same time (maximum 4). Extra downloads wait in a queue and start on their own. More at once splits your network speed between them.");
        mNote.setTextColor(GREY); mNote.setTextSize(12.5f);
        mNote.setPadding(0, dp(4), 0, dp(12));
        mCard.addView(mNote);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        mCard.addView(chips);
        for (int i = 0; i < 4; i++) {
            final int n = i + 1;
            TextView c = new TextView(this);
            c.setText(n == 1 ? "1 file" : n + " files");
            c.setTextSize(13.5f); c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) clp.setMargins(dp(8), 0, 0, 0);
            chips.addView(c, clp);
            parChips[i] = c;
            c.setOnClickListener(v -> {
                prefs().edit().putInt("max_parallel", n).apply();
                styleParChips();
                Toast.makeText(this, n == 1 ? "One download at a time" : "Up to " + n + " downloads together", Toast.LENGTH_SHORT).show();
            });
        }
        styleParChips();
    }

    private void styleParChips() {
        int cur = prefs().getInt("max_parallel", 1);
        for (int i = 0; i < 4; i++) {
            boolean sel = (i + 1) == cur;
            parChips[i].setBackground(chipBg(sel));
            parChips[i].setTextColor(sel ? Color.WHITE : INK);
        }
    }

    private void refreshFolder() {
        String label = prefs().getString("dl_folder_label", "");
        boolean custom = label != null && !label.isEmpty();
        folderValue.setText(custom ? label : "Default — Zynox folders");
        resetBtn.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_FOLDER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Throwable ignored) {}
        String label = treeLabel(tree);
        prefs().edit().putString("dl_folder_uri", tree.toString()).putString("dl_folder_label", label).apply();
        refreshFolder();
        Toast.makeText(this, "Downloads will be saved to: " + label, Toast.LENGTH_LONG).show();
    }

    /** Friendly folder name for a SAF tree URI (e.g. "Download" or "My Videos"). */
    private String treeLabel(Uri tree) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            Cursor c = getContentResolver().query(doc, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    String name = c.getString(0);
                    c.close();
                    if (name != null && !name.isEmpty()) return name;
                }
                c.close();
            }
        } catch (Throwable ignored) {}
        try {
            String seg = tree.getLastPathSegment();
            if (seg != null) {
                int colon = seg.lastIndexOf(':');
                String tail = colon >= 0 ? seg.substring(colon + 1) : seg;
                return tail.isEmpty() ? "Phone storage" : tail;
            }
        } catch (Throwable ignored) {}
        return "Selected folder";
    }
}
