package com.a0x0f.rak811tester;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.a0x0f.rak811tester.databinding.ViewholderLoglineBinding;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableList;
import androidx.recyclerview.widget.RecyclerView;

public class LogLineRecyclerViewAdapter extends RecyclerView.Adapter<LogLineRecyclerViewAdapter.LogLineViewHolder> {

    private final ObservableList<Logger.LogLine> logLines;

    private LogLineRecyclerViewAdapter(ObservableList<Logger.LogLine> logLines) {
        this.logLines = logLines;
        logLines.addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<Logger.LogLine>>() {
            @Override
            public void onChanged(ObservableList<Logger.LogLine> sender) {
                LogLineRecyclerViewAdapter.this.notifyDataSetChanged();
            }

            @Override
            public void onItemRangeChanged(ObservableList<Logger.LogLine> sender, int positionStart, int itemCount) {
                LogLineRecyclerViewAdapter.this.notifyDataSetChanged();
            }

            @Override
            public void onItemRangeInserted(ObservableList<Logger.LogLine> sender, int positionStart, int itemCount) {
                LogLineRecyclerViewAdapter.this.notifyItemRangeInserted(positionStart, itemCount);
            }

            @Override
            public void onItemRangeMoved(ObservableList<Logger.LogLine> sender, int fromPosition, int toPosition, int itemCount) {
                LogLineRecyclerViewAdapter.this.notifyDataSetChanged();
            }

            @Override
            public void onItemRangeRemoved(ObservableList<Logger.LogLine> sender, int positionStart, int itemCount) {
                LogLineRecyclerViewAdapter.this.notifyDataSetChanged();
            }
        });
    }

    public static LogLineRecyclerViewAdapter createAdapter(ObservableList<Logger.LogLine> logLines) {
        return new LogLineRecyclerViewAdapter(logLines);
    }

    @NonNull
    @Override
    public LogLineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return LogLineViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull LogLineViewHolder holder, int position) {
        Logger.LogLine logLine = logLines.get(position);
        holder.binding.setLogLine(logLine);
    }

    @Override
    public int getItemCount() {
        return logLines.size();
    }

    public static class LogLineViewHolder extends RecyclerView.ViewHolder {

        private ViewholderLoglineBinding binding;

        private LogLineViewHolder(ViewholderLoglineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public static LogLineViewHolder create(ViewGroup parent) {
            ViewholderLoglineBinding binding = DataBindingUtil
                    .inflate(LayoutInflater.from(parent.getContext()), R.layout.viewholder_logline, parent, false);
            return new LogLineViewHolder(binding);
        }
    }

}
