package org.smartregister.reveal.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.turf.TurfMeasurement;

import org.joda.time.DateTime;
import org.smartregister.domain.Location;
import org.smartregister.domain.LocationProperty;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.adapter.AvailableOfflineMapAdapter;
import org.smartregister.reveal.contract.AvailableOfflineMapsContract;
import org.smartregister.reveal.contract.OfflineMapDownloadCallback;
import org.smartregister.reveal.model.OfflineMapModel;
import org.smartregister.reveal.presenter.AvailableOfflineMapsPresenter;
import org.smartregister.util.DateTimeTypeConverter;
import org.smartregister.util.PropertiesConverter;

import java.util.ArrayList;
import java.util.List;

import io.ona.kujaku.helpers.OfflineServiceHelper;

public class AvailableOfflineMapsFragment extends BaseOfflineMapsFragment implements AvailableOfflineMapsContract.View {

    private RecyclerView offlineMapRecyclerView;

    private AvailableOfflineMapAdapter adapter;

    private AvailableOfflineMapsPresenter presenter;

    private List<OfflineMapModel> offlineMapModelList = new ArrayList<>();

    private List<Location> operationalAreasToDownload = new ArrayList<>();

    private OfflineMapDownloadCallback callback;

    public static AvailableOfflineMapsFragment newInstance(Bundle bundle) {

        AvailableOfflineMapsFragment fragment = new AvailableOfflineMapsFragment();
        if (bundle != null) {
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new AvailableOfflineMapsPresenter(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offline_map, container, false);
        setUpViews(view);
        initializeAdapter();
        return view;
    }

    private void setUpViews(View view) {
        offlineMapRecyclerView = view.findViewById(R.id.offline_map_recyclerView);

        Button btnDownloadMap = view.findViewById(R.id.download_map);

        btnDownloadMap.setOnClickListener(v -> initiateMapDownload());

    }

    private void initializeAdapter() {
        adapter = new AvailableOfflineMapAdapter(this.getContext(), onClickListener);
        offlineMapRecyclerView.setAdapter(adapter);
        if (offlineMapModelList != null) {
            setOfflineMapModelList(offlineMapModelList);
        }

        presenter.fetchOperationalAreas();

    }

    private View.OnClickListener onClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View view) {
            OfflineMapModel offlineMapModel = (OfflineMapModel) view.getTag(R.id.offline_map_checkbox);
            presenter.onDownloadAreaSelected(offlineMapModel);
        }
    };

    @Override
    public void setOfflineMapModelList(List<OfflineMapModel> offlineMapModelList) {
        if (adapter == null) {
            this.offlineMapModelList = offlineMapModelList;
        } else {
            adapter.setOfflineMapModels(offlineMapModelList);
            this.offlineMapModelList = offlineMapModelList;
        }
    }

    @Override
    public void updateOperationalAreasToDownload(Location operationalAreasToDownload) {
        this.operationalAreasToDownload.add(operationalAreasToDownload);
    }

    @Override
    public void disableCheckBox(String operationalAreaName) {
        if (adapter ==null) {
            return;
        }
        for (OfflineMapModel offlineMapModel: offlineMapModelList ) {
            if (offlineMapModel.getDownloadAreaLabel().equals(operationalAreaName)){
                if (offlineMapModel.isDownloadStarted()) {
                    return;
                }
                offlineMapModel.setDownloadStarted(true);
            }
        }

        setOfflineMapModelList(offlineMapModelList);
    }

    @Override
    public void moveDownloadedOAToDownloadedList(String operationalAreaName) {
        for (OfflineMapModel offlineMapModel : offlineMapModelList) {
            if (offlineMapModel.getDownloadAreaLabel().equals(operationalAreaName)) {
                callback.onMapDownloaded(offlineMapModel);
                offlineMapModelList.remove(offlineMapModel);
                setOfflineMapModelList(offlineMapModelList);
                return;
            }
        }
    }

    private void initiateMapDownload() {
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter())
                .registerTypeAdapter(LocationProperty.class, new PropertiesConverter()).create();

        if (this.operationalAreasToDownload == null || this.operationalAreasToDownload.isEmpty()) {
            displayToast("Please select an Operational Area to download");
        }

        for (Location location: this.operationalAreasToDownload ) {
            Feature operationalAreaFeature = Feature.fromJson(gson.toJson(location));
            downloadMap(operationalAreaFeature);
        }
    }

    private void downloadMap(Feature operationalAreaFeature) {
        double[] bbox = TurfMeasurement.bbox(operationalAreaFeature.geometry());

        double minX = bbox[0];
        double minY = bbox[1];
        double maxX = bbox[2];
        double maxY = bbox[3];

        double topLeftLat = maxY;
        double topLeftLng = minX;
        double bottomRightLat = minY;
        double bottomRightLng = maxX;
        double topRightLat = maxY;
        double topRightLng = maxX;
        double bottomLeftLat = minY;
        double bottomLeftLng = minX;

        String mapName = operationalAreasToDownload.get(0).getProperties().getName();

        currentMapDownload = mapName;

        String mapboxStyle = "mapbox://styles/ona/cj9jueph7034i2rphe0gp3o6m";

        LatLng topLeftBound = new LatLng(topLeftLat, topLeftLng);
        LatLng topRightBound = new LatLng(topRightLat, topRightLng);
        LatLng bottomRightBound = new LatLng(bottomRightLat, bottomRightLng);
        LatLng bottomLeftBound = new LatLng(bottomLeftLat, bottomLeftLng);

        double maxZoom = 20.0;
        double minZoom = 0.0;

        OfflineServiceHelper.ZoomRange zoomRange = new OfflineServiceHelper.ZoomRange(minZoom, maxZoom);

        OfflineServiceHelper.requestOfflineMapDownload(getActivity()
                , mapName
                , mapboxStyle
                , BuildConfig.MAPBOX_SDK_ACCESS_TOKEN
                , topLeftBound
                , topRightBound
                , bottomRightBound
                , bottomLeftBound
                , zoomRange
        );
    }

    public void setOfflineMapDownloadCallback(OfflineMapDownloadCallback callBack) {
        this.callback = callBack;
    }

    @Override
    protected void downloadCompleted(String mapUniqueName) {
        presenter.onDownloadComplete(mapUniqueName);
    }

    @Override
    protected void downloadStarted(String mapUniqueName) {
        presenter.onDownloadStarted(mapUniqueName);
    }

}
