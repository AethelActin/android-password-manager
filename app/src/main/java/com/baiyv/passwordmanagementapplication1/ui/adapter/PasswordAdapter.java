package com.baiyv.passwordmanagementapplication1.ui.adapter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baiyv.passwordmanagementapplication1.R;
import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;
import com.baiyv.passwordmanagementapplication1.utils.Constants;
import com.baiyv.passwordmanagementapplication1.utils.CryptoUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.ViewHolder> {

    private final List<PasswordEntry> passwordList;
    private final OnPasswordClickListener listener;
    private final String masterPassword;
    private final int passwordStyle;
    private final Set<Integer> visiblePasswordIds = new HashSet<>();
    private final Map<Integer, Runnable> autoHideRunnables = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnPasswordClickListener {
        void onPasswordClick(PasswordEntry password);
        void onCopyAccountClick(PasswordEntry password);
        void onCopyPasswordClick(PasswordEntry password);
        void onCopyAllClick(PasswordEntry password);
        void onCopyClick(PasswordEntry password);
        void onDeleteClick(PasswordEntry password);
        void onOpenTargetClick(PasswordEntry password);
        void ensurePasswordViewAuth(Runnable onAuthed);
    }

    public PasswordAdapter(List<PasswordEntry> passwordList, OnPasswordClickListener listener, String masterPassword, int passwordStyle) {
        this.passwordList = passwordList;
        this.listener = listener;
        this.masterPassword = masterPassword;
        this.passwordStyle = passwordStyle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = passwordStyle == 1 ? R.layout.item_password : R.layout.item_password_simple;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutRes, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PasswordEntry password = passwordList.get(position);

        holder.tvTitle.setText(safe(password.getTitle()));
        holder.tvAccount.setText(safe(password.getUsername()));
        holder.tvCategory.setText(safe(password.getCategory()));
        holder.tvWebsite.setText(formatTargetLabel(holder.itemView.getContext(), safe(password.getWebsite())));
        holder.tvNotes.setText(safe(password.getNotes()));

        String plain = visiblePasswordIds.contains(password.getId())
                ? CryptoUtils.decrypt(password.getEncryptedPassword(), masterPassword) : null;
        boolean canToggle = password.getEncryptedPassword() != null;
        boolean visible = canToggle && visiblePasswordIds.contains(password.getId());

        holder.btnEye.setEnabled(canToggle);
        holder.btnEye.setAlpha(canToggle ? 1f : 0.4f);
        holder.btnEye.setImageResource(visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        holder.tvPassword.setText(visible ? safe(plain) : maskPassword("********"));

        String target = safe(password.getWebsite()).trim();
        boolean hasTarget = isTargetOpenable(holder.itemView.getContext(), target);
        if (holder.btnOpenTarget != null) {
            holder.btnOpenTarget.setEnabled(hasTarget);
            holder.btnOpenTarget.setAlpha(hasTarget ? 1f : 0.4f);
            holder.btnOpenTarget.setOnClickListener(v -> {
                if (!hasTarget) return;
                listener.onOpenTargetClick(password);
            });
        }

        holder.btnEye.setOnClickListener(v -> {
            int id = password.getId();

            if (visiblePasswordIds.contains(id)) {
                visiblePasswordIds.remove(id);
                cancelAutoHide(id);
                holder.btnEye.setImageResource(R.drawable.ic_visibility_off);
                holder.tvPassword.setText(maskPassword("********"));
                return;
            }

            listener.ensurePasswordViewAuth(() -> {
                visiblePasswordIds.add(id);
                scheduleAutoHide(id);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos);
                } else {
                    notifyDataSetChanged();
                }
            });
        });

        holder.btnCopyAccount.setOnClickListener(v -> listener.onCopyAccountClick(password));
        holder.btnCopyPassword.setOnClickListener(v -> listener.onCopyPasswordClick(password));
        holder.btnCopyAll.setOnClickListener(v -> listener.onCopyAllClick(password));

        holder.itemView.setOnClickListener(v -> listener.onPasswordClick(password));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onDeleteClick(password);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return passwordList.size();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private boolean isTargetOpenable(Context context, String raw) {
        if (raw == null) return false;
        String target = raw.trim();
        if (target.isEmpty()) return false;

        if (target.startsWith(Constants.TARGET_PREFIX_APP)) {
            String pkg = target.substring(Constants.TARGET_PREFIX_APP.length()).trim();
            if (pkg.isEmpty()) return false;
            try {
                return context.getPackageManager().getLaunchIntentForPackage(pkg) != null;
            } catch (Exception ignored) {
                return false;
            }
        }

        return true;
    }

    private String formatTargetLabel(Context context, String raw) {
        if (raw == null) return "";
        String target = raw.trim();
        if (!target.startsWith(Constants.TARGET_PREFIX_APP)) return target;

        String pkg = target.substring(Constants.TARGET_PREFIX_APP.length()).trim();
        if (pkg.isEmpty()) return "";

        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }

    public void hideAllPasswords() {
        if (visiblePasswordIds.isEmpty() && autoHideRunnables.isEmpty()) return;

        visiblePasswordIds.clear();
        for (Runnable r : autoHideRunnables.values()) {
            mainHandler.removeCallbacks(r);
        }
        autoHideRunnables.clear();
        notifyDataSetChanged();
    }

    private void scheduleAutoHide(int id) {
        cancelAutoHide(id);

        Runnable r = () -> {
            if (!visiblePasswordIds.contains(id)) {
                autoHideRunnables.remove(id);
                return;
            }
            visiblePasswordIds.remove(id);
            autoHideRunnables.remove(id);

            int pos = findPositionById(id);
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos);
            } else {
                notifyDataSetChanged();
            }
        };

        autoHideRunnables.put(id, r);
        mainHandler.postDelayed(r, 30_000L);
    }

    private void cancelAutoHide(int id) {
        Runnable r = autoHideRunnables.remove(id);
        if (r != null) {
            mainHandler.removeCallbacks(r);
        }
    }

    private int findPositionById(int id) {
        for (int i = 0; i < passwordList.size(); i++) {
            PasswordEntry e = passwordList.get(i);
            if (e != null && e.getId() == id) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) return "";
        int count = Math.max(6, Math.min(10, password.length()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(' ');
            sb.append('•');
        }
        return sb.toString();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvAccount;
        TextView tvPassword;
        TextView tvCategory;
        TextView tvWebsite;
        TextView tvNotes;
        ImageButton btnEye;
        ImageButton btnOpenTarget;
        Button btnCopyAccount;
        Button btnCopyPassword;
        Button btnCopyAll;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAccount = itemView.findViewById(R.id.tvAccount);
            tvPassword = itemView.findViewById(R.id.tvPassword);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvWebsite = itemView.findViewById(R.id.tvWebsite);
            tvNotes = itemView.findViewById(R.id.tvNotes);
            btnEye = itemView.findViewById(R.id.btnTogglePassword);
            btnOpenTarget = itemView.findViewById(R.id.btnOpenTarget);
            btnCopyAccount = itemView.findViewById(R.id.btnCopyAccount);
            btnCopyPassword = itemView.findViewById(R.id.btnCopyPassword);
            btnCopyAll = itemView.findViewById(R.id.btnCopyAll);
        }
    }
}