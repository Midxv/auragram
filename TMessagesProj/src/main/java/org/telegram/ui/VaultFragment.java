package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class VaultFragment extends BaseFragment {

    private static class VaultItem {
        String type; String content; String path; String thumbnailPath;
        VaultItem(String type, String content, String path, String thumbnailPath) { this.type = type; this.content = content; this.path = path; this.thumbnailPath = thumbnailPath; }
        JSONObject toJSON() { try { JSONObject json = new JSONObject(); json.put("type", type); json.put("content", content); json.put("path", path); json.put("thumbnailPath", thumbnailPath); return json; } catch (Exception e) { return null; } }
        static VaultItem fromJSON(JSONObject json) { try { return new VaultItem(json.getString("type"), json.getString("content"), json.optString("path", null), json.optString("thumbnailPath", null)); } catch (Exception e) { return null; } }
    }

    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ImageView addNoteFab;
    private VaultAdapter adapter;
    private final ArrayList<VaultItem> vaultItems = new ArrayList<>();
    private static final String VAULT_DATA_FILE = "vault_data.json";
    private static final String VAULT_FILES_DIR = "vault_files";
    private static final String VAULT_THUMBS_DIR = "vault_thumbs";
    private static final int FILE_PICKER_REQUEST_CODE = 101;

    @Override
    public boolean onFragmentCreate() {
        loadVaultItems();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        fragmentView = LayoutInflater.from(context).inflate(R.layout.fragment_vault, null, false);

        actionBar.setTitle("Vault");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) { finishFragment(); } } });

        recyclerView = fragmentView.findViewById(R.id.vault_recycler_view);
        emptyStateLayout = fragmentView.findViewById(R.id.empty_state_layout);
        addNoteFab = (ImageView) fragmentView.findViewById(R.id.add_note_fab);

        adapter = new VaultAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(context, 4));
        recyclerView.setAdapter(adapter);

        addNoteFab.setOnClickListener(v -> showAddChoiceDialog());
        checkEmptyState();
        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                copyFileToVault(uri);
            }
        }
    }

    private void checkEmptyState() { if (vaultItems.isEmpty()) { recyclerView.setVisibility(View.GONE); emptyStateLayout.setVisibility(View.VISIBLE); } else { recyclerView.setVisibility(View.VISIBLE); emptyStateLayout.setVisibility(View.GONE); } }

    private void showAddChoiceDialog() { new AlertDialog.Builder(getParentActivity()).setTitle("Add to Vault").setItems(new CharSequence[]{"Add Note", "Add File"}, (dialog, which) -> { if (which == 0) { showAddNoteDialog(); } else { launchFilePicker(); } }).show(); }

    private void showAddNoteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Add New Note");
        final EditText input = new EditText(getParentActivity());
        input.setHint("Enter your secret note...");
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> { String noteText = input.getText().toString(); if (!noteText.isEmpty()) { vaultItems.add(new VaultItem("NOTE", noteText, null, null)); adapter.notifyItemInserted(vaultItems.size() - 1); saveVaultItems(); checkEmptyState(); } });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void launchFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select a file to hide"), FILE_PICKER_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getParentActivity(), "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFileToVault(Uri uri) {
        try (InputStream inputStream = getParentActivity().getContentResolver().openInputStream(uri)) {
            String fileName = getFileName(uri);
            String mimeType = getParentActivity().getContentResolver().getType(uri);
            File vaultDir = new File(getParentActivity().getFilesDir(), VAULT_FILES_DIR);
            if (!vaultDir.exists()) vaultDir.mkdir();
            File destinationFile = new File(vaultDir, fileName);
            try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) { byte[] buf = new byte[4096]; int len; while ((len = inputStream.read(buf)) > 0) { outputStream.write(buf, 0, len); } }

            String itemType = "FILE";
            String thumbnailPath = null;
            if (mimeType != null && mimeType.startsWith("image/")) { itemType = "IMAGE"; thumbnailPath = createThumbnail(destinationFile, false); }
            else if (mimeType != null && mimeType.startsWith("video/")) { itemType = "VIDEO"; thumbnailPath = createThumbnail(destinationFile, true); }

            vaultItems.add(new VaultItem(itemType, fileName, destinationFile.getAbsolutePath(), thumbnailPath));
            adapter.notifyItemInserted(vaultItems.size() - 1);
            saveVaultItems();
            checkEmptyState();
            Toast.makeText(getParentActivity(), "File added to vault", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); Toast.makeText(getParentActivity(), "Failed to add file", Toast.LENGTH_SHORT).show(); }
    }

    private String createThumbnail(File sourceFile, boolean isVideo) {
        try {
            Bitmap thumbnail;
            if (isVideo) { thumbnail = ThumbnailUtils.createVideoThumbnail(sourceFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND); }
            else { thumbnail = ThumbnailUtils.extractThumbnail(android.graphics.BitmapFactory.decodeFile(sourceFile.getAbsolutePath()), 256, 256); }
            if (thumbnail == null) return null;
            File thumbsDir = new File(getParentActivity().getFilesDir(), VAULT_THUMBS_DIR);
            if (!thumbsDir.exists()) thumbsDir.mkdir();
            File thumbFile = new File(thumbsDir, sourceFile.getName() + ".png");
            try (FileOutputStream fos = new FileOutputStream(thumbFile)) { thumbnail.compress(Bitmap.CompressFormat.PNG, 80, fos); }
            thumbnail.recycle();
            return thumbFile.getAbsolutePath();
        } catch (Exception e) { FileLog.e(e); return null; }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) { try (android.database.Cursor cursor = getParentActivity().getContentResolver().query(uri, null, null, null, null)) { if (cursor != null && cursor.moveToFirst()) { int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (index >= 0) { result = cursor.getString(index); } } } }
        if (result == null) { result = uri.getPath(); int cut = result.lastIndexOf('/'); if (cut != -1) { result = result.substring(cut + 1); } }
        return result;
    }

    private void saveVaultItems() { try { JSONArray jsonArray = new JSONArray(); for (VaultItem item : vaultItems) { jsonArray.put(item.toJSON()); } File file = new File(getParentActivity().getFilesDir(), VAULT_DATA_FILE); try (FileOutputStream fos = new FileOutputStream(file, false)) { fos.write(jsonArray.toString().getBytes()); } } catch (Exception e) { FileLog.e(e); } }

    private void loadVaultItems() { try { File file = new File(getParentActivity().getFilesDir(), VAULT_DATA_FILE); if (!file.exists()) return; StringBuilder sb; try (FileInputStream fis = new FileInputStream(file)) { InputStreamReader isr = new InputStreamReader(fis); BufferedReader bufferedReader = new BufferedReader(isr); sb = new StringBuilder(); String line; while ((line = bufferedReader.readLine()) != null) { sb.append(line); } } JSONArray jsonArray = new JSONArray(sb.toString()); vaultItems.clear(); for (int i = 0; i < jsonArray.length(); i++) { VaultItem item = VaultItem.fromJSON(jsonArray.getJSONObject(i)); if (item != null) { vaultItems.add(item); } } } catch (Exception e) { FileLog.e(e); } }

    private class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.vault_note_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VaultItem item = vaultItems.get(position);
            holder.itemTextView.setText(item.content);

            holder.itemThumbnail.setImageDrawable(null);
            holder.itemThumbnail.setBackgroundColor(0xFF333333);

            if (item.thumbnailPath != null) {
                holder.itemThumbnail.setImageURI(Uri.fromFile(new File(item.thumbnailPath)));
            }

            switch (item.type) {
                case "NOTE":
                    holder.itemTypeIcon.setImageResource(R.drawable.msg_edit);
                    holder.itemThumbnail.setImageResource(R.drawable.msg_settings_old);
                    holder.itemThumbnail.setScaleType(ImageView.ScaleType.CENTER);
                    break;
                case "VIDEO":
                    holder.itemTypeIcon.setImageResource(R.drawable.msg_video);
                    holder.itemThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    break;
                case "IMAGE":
                    holder.itemTypeIcon.setImageResource(R.drawable.msg_gallery);
                    holder.itemThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    break;
                default: // FILE
                    holder.itemTypeIcon.setImageResource(R.drawable.msg_settings_old);
                    holder.itemThumbnail.setImageResource(R.drawable.msg_settings_old);
                    holder.itemThumbnail.setScaleType(ImageView.ScaleType.CENTER);
                    break;
            }

            holder.itemView.setOnClickListener(v -> { if (!"NOTE".equals(item.type)) { try { File fileToOpen = new File(item.path); Uri fileUri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", fileToOpen); Intent intent = new Intent(Intent.ACTION_VIEW); String mimeType = getParentActivity().getContentResolver().getType(fileUri); if (mimeType == null) { mimeType = "*/*"; } intent.setDataAndType(fileUri, mimeType); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); getParentActivity().startActivity(Intent.createChooser(intent, "Open with...")); } catch (Exception e) { e.printStackTrace(); Toast.makeText(getParentActivity(), "Could not open file. No app found.", Toast.LENGTH_SHORT).show(); } } });

            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(getParentActivity()).setTitle("Delete Item").setMessage("Are you sure you want to delete this item?").setPositiveButton("Delete", (dialog, which) -> {
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        VaultItem itemToDelete = vaultItems.get(currentPosition);
                        if (itemToDelete.path != null) { try { new File(itemToDelete.path).delete(); } catch (Exception e) { e.printStackTrace(); } }
                        if (itemToDelete.thumbnailPath != null) { try { new File(itemToDelete.thumbnailPath).delete(); } catch (Exception e) { e.printStackTrace(); } }
                        vaultItems.remove(currentPosition);
                        notifyItemRemoved(currentPosition);
                        saveVaultItems();
                        checkEmptyState();
                        Toast.makeText(getParentActivity(), "Item deleted", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Cancel", null).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return vaultItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemTextView;
            ImageView itemThumbnail;
            ImageView itemTypeIcon;
            ViewHolder(View view) {
                super(view);
                itemTextView = view.findViewById(R.id.item_text_view);
                itemThumbnail = view.findViewById(R.id.item_thumbnail);
                itemTypeIcon = view.findViewById(R.id.item_type_icon);
            }
        }
    }
}