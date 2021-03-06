package org.smartregister.reveal.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.smartregister.reveal.R;
import org.smartregister.reveal.contract.StatsFragmentContract;
import org.smartregister.reveal.presenter.StatsFragmentPresenter;

import java.util.Map;

import static org.smartregister.reveal.util.Constants.SyncInfo.SYNCED_CLIENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.SYNCED_EVENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.TASK_UNPROCESSED_EVENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.UNSYNCED_CLIENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.UNSYNCED_EVENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.VALID_CLIENTS;
import static org.smartregister.reveal.util.Constants.SyncInfo.VALID_EVENTS;

public class StatsFragment extends Fragment implements StatsFragmentContract.View {
    private StatsFragmentPresenter presenter;

    private TextView tvSyncedEvents;
    private TextView tvUnSyncedEvents;
    private TextView tvSyncedClient;
    private TextView tvUnSyncedClients;
    private TextView tvValidatedEvents;
    private TextView tvValidatedClients;
    private TextView tvTaskUnprocessedEvents;

    public static StatsFragment newInstance(Bundle bundle) {

        StatsFragment fragment = new StatsFragment();
        if (bundle != null) {
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new StatsFragmentPresenter(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_stats, container, false);
        initializeViews(rootView);
        return rootView;
    }

    private void initializeViews(View view)
    {
        tvSyncedEvents = view.findViewById(R.id.synced_events);
        tvUnSyncedEvents = view.findViewById(R.id.unsynced_events);
        tvSyncedClient = view.findViewById(R.id.synced_clients);
        tvUnSyncedClients = view.findViewById(R.id.unsynced_clients);
        tvValidatedEvents = view.findViewById(R.id.validated_events);
        tvValidatedClients = view.findViewById(R.id.validated_clients);
        tvTaskUnprocessedEvents = view.findViewById(R.id.task_unprocessed_events);


        Button btnRefreshStats = view.findViewById(R.id.refresh_button);
        btnRefreshStats.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                presenter.fetchSyncInfo();
            }
        });

        presenter.fetchSyncInfo();
    }

    @Override
    public void refreshECSyncInfo(Map<String, Integer> syncInfoMap) {
        tvSyncedEvents.setText(syncInfoMap.get(SYNCED_EVENTS) + "");
        tvUnSyncedEvents.setText(syncInfoMap.get(UNSYNCED_EVENTS) + "");
        tvTaskUnprocessedEvents.setText(syncInfoMap.get(TASK_UNPROCESSED_EVENTS) + "");

        tvSyncedClient.setText(syncInfoMap.get(SYNCED_CLIENTS) + "");
        tvUnSyncedClients.setText(syncInfoMap.get(UNSYNCED_CLIENTS) + "");

        tvValidatedEvents.setText(syncInfoMap.get(VALID_EVENTS) + "");
        tvValidatedClients.setText(syncInfoMap.get(VALID_CLIENTS) + "");
    }

}
