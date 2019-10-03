package org.smartregister.reveal.view;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.domain.FetchStatus;
import org.smartregister.family.util.DBConstants;
import org.smartregister.family.util.Utils;
import org.smartregister.receiver.SyncStatusBroadcastReceiver;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.BaseDrawerContract;
import org.smartregister.reveal.contract.ListTaskContract;
import org.smartregister.reveal.contract.UserLocationContract.UserLocationView;
import org.smartregister.reveal.model.CardDetails;
import org.smartregister.reveal.model.MosquitoHarvestCardDetails;
import org.smartregister.reveal.model.SprayCardDetails;
import org.smartregister.reveal.presenter.ListTaskPresenter;
import org.smartregister.reveal.repository.RevealMappingHelper;
import org.smartregister.reveal.util.AlertDialogUtils;
import org.smartregister.reveal.util.CardDetailsUtil;
import org.smartregister.reveal.util.Constants.Action;
import org.smartregister.reveal.util.Constants.Properties;
import org.smartregister.reveal.util.Constants.TaskRegister;
import org.smartregister.reveal.util.RevealJsonFormUtils;
import org.smartregister.reveal.util.RevealMapHelper;
import org.smartregister.util.AssetHandler;

import io.ona.kujaku.callbacks.OnLocationComponentInitializedCallback;
import io.ona.kujaku.layers.BoundaryLayer;
import io.ona.kujaku.utils.Constants;
import timber.log.Timber;

import static org.smartregister.reveal.util.Constants.ANIMATE_TO_LOCATION_DURATION;
import static org.smartregister.reveal.util.Constants.CONFIGURATION.LOCAL_SYNC_DONE;
import static org.smartregister.reveal.util.Constants.CONFIGURATION.UPDATE_LOCATION_BUFFER_RADIUS;
import static org.smartregister.reveal.util.Constants.DIGITAL_GLOBE_CONNECT_ID;
import static org.smartregister.reveal.util.Constants.Intervention.IRS;
import static org.smartregister.reveal.util.Constants.Intervention.LARVAL_DIPPING;
import static org.smartregister.reveal.util.Constants.Intervention.MOSQUITO_COLLECTION;
import static org.smartregister.reveal.util.Constants.Intervention.PAOT;
import static org.smartregister.reveal.util.Constants.JSON_FORM_PARAM_JSON;
import static org.smartregister.reveal.util.Constants.REQUEST_CODE_GET_JSON;
import static org.smartregister.reveal.util.Constants.VERTICAL_OFFSET;
import static org.smartregister.reveal.util.FamilyConstants.Intent.START_REGISTRATION;
import static org.smartregister.reveal.util.Utils.getDrawOperationalAreaBoundaryAndLabel;
import static org.smartregister.reveal.util.Utils.getLocationBuffer;
import static org.smartregister.reveal.util.Utils.getPixelsPerDPI;

/**
 * Created by samuelgithengi on 11/20/18.
 */
public class ListTasksActivity extends BaseMapActivity implements ListTaskContract.ListTaskView,
        View.OnClickListener, SyncStatusBroadcastReceiver.SyncStatusListener, UserLocationView, OnLocationComponentInitializedCallback {

    private ListTaskPresenter listTaskPresenter;

    private View rootView;

    private GeoJsonSource geoJsonSource;

    private GeoJsonSource selectedGeoJsonSource;

    private ProgressDialog progressDialog;

    private MapboxMap mMapboxMap;

    private TextView tvReason;

    private CardView sprayCardView;
    private CardView mosquitoCollectionCardView;
    private CardView larvalBreedingCardView;
    private CardView potentialAreaOfTransmissionCardView;
    private CardView indicatorsCardView;


    private RefreshGeowidgetReceiver refreshGeowidgetReceiver = new RefreshGeowidgetReceiver();

    private boolean hasRequestedLocation;

    private Snackbar syncProgressSnackbar;

    private BaseDrawerContract.View drawerView;

    private RevealJsonFormUtils jsonFormUtils;

    private BoundaryLayer boundaryLayer;

    private RevealMapHelper revealMapHelper;

    private ImageButton myLocationButton;

    private LinearLayout progressIndicatorsGroupView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_tasks);

        jsonFormUtils = new RevealJsonFormUtils();
        drawerView = new DrawerMenuView(this);

        revealMapHelper = new RevealMapHelper();

        listTaskPresenter = new ListTaskPresenter(this, drawerView.getPresenter());

        rootView = findViewById(R.id.content_frame);

        initializeProgressIndicatorViews();

        initializeMapView(savedInstanceState);

        drawerView.initializeDrawerLayout();
        initializeProgressDialog();

        findViewById(R.id.btn_add_structure).setOnClickListener(this);
        findViewById(R.id.drawerMenu).setOnClickListener(this);

        initializeCardViews();

        syncProgressSnackbar = Snackbar.make(rootView, getString(org.smartregister.R.string.syncing), Snackbar.LENGTH_INDEFINITE);
    }

    private void initializeCardViews() {
        sprayCardView = findViewById(R.id.spray_card_view);
        sprayCardView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //intercept clicks and interaction of map below card view
                return true;
            }
        });

        mosquitoCollectionCardView = findViewById(R.id.mosquito_collection_card_view);

        larvalBreedingCardView = findViewById(R.id.larval_breeding_card_view);

        potentialAreaOfTransmissionCardView = findViewById(R.id.potential_area_of_transmission_card_view);

        findViewById(R.id.btn_add_structure).setOnClickListener(this);

        findViewById(R.id.btn_collapse_spray_card_view).setOnClickListener(this);

        tvReason = findViewById(R.id.reason);

        findViewById(R.id.change_spray_status).setOnClickListener(this);

        findViewById(R.id.register_family).setOnClickListener(this);

        findViewById(R.id.task_register).setOnClickListener(this);

        findViewById(R.id.btn_collapse_mosquito_collection_card_view).setOnClickListener(this);

        findViewById(R.id.btn_record_mosquito_collection).setOnClickListener(this);

        findViewById(R.id.btn_collapse_larval_breeding_card_view).setOnClickListener(this);

        findViewById(R.id.btn_record_larval_dipping).setOnClickListener(this);

        findViewById(R.id.btn_collapse_paot_card_view).setOnClickListener(this);

        findViewById(R.id.btn_edit_paot_details).setOnClickListener(this);

        indicatorsCardView = findViewById(R.id.indicators_card_view);
        indicatorsCardView.setOnClickListener(this);

        findViewById(R.id.btn_collapse_indicators_card_view).setOnClickListener(this);
    }

    @Override
    public void closeCardView(int id) {
        if (id == R.id.btn_collapse_spray_card_view) {
            setViewVisibility(sprayCardView, false);
        } else if (id == R.id.btn_collapse_mosquito_collection_card_view) {
            setViewVisibility(mosquitoCollectionCardView, false);
        } else if (id == R.id.btn_collapse_larval_breeding_card_view) {
            setViewVisibility(larvalBreedingCardView, false);
        } else if (id == R.id.btn_collapse_paot_card_view) {
            setViewVisibility(potentialAreaOfTransmissionCardView, false);
        } else if (id == R.id.btn_collapse_indicators_card_view) {
            setViewVisibility(indicatorsCardView, false);
        }
    }

    @Override
    public void closeAllCardViews() {
        setViewVisibility(sprayCardView, false);
        setViewVisibility(mosquitoCollectionCardView, false);
        setViewVisibility(larvalBreedingCardView, false);
        setViewVisibility(potentialAreaOfTransmissionCardView, false);
        setViewVisibility(indicatorsCardView, false);
    }

    private void setViewVisibility(View view, boolean isVisible) {
        view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void initializeMapView(Bundle savedInstanceState) {
        kujakuMapView = findViewById(R.id.kujakuMapView);

        myLocationButton = findViewById(R.id.ib_mapview_focusOnMyLocationIcon);

        kujakuMapView.getMapboxLocationComponentWrapper().setOnLocationComponentInitializedCallback(this);

        kujakuMapView.onCreate(savedInstanceState);

        kujakuMapView.showCurrentLocationBtn(true);

        kujakuMapView.setDisableMyLocationOnMapMove(true);

        Float locationBufferRadius = getLocationBuffer();

        kujakuMapView.setLocationBufferRadius(locationBufferRadius / getPixelsPerDPI(getResources()));

        kujakuMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                String mapBoxStyle = AssetHandler.readFileFromAssetsFolder(getString(R.string.reveal_satellite_style), ListTasksActivity.this);
                Style.Builder builder = new Style.Builder().fromJson(mapBoxStyle.replace(DIGITAL_GLOBE_CONNECT_ID, BuildConfig.DG_CONNECT_ID));
                mapboxMap.setStyle(builder, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        geoJsonSource = style.getSourceAs(getString(R.string.reveal_datasource_name));

                        selectedGeoJsonSource = style.getSourceAs(getString(R.string.selected_datasource_name));
                        RevealMapHelper.addCustomLayers(style, ListTasksActivity.this);
                    }
                });

                mMapboxMap = mapboxMap;
                mapboxMap.setMinZoomPreference(10);
                mapboxMap.setMaxZoomPreference(21);

                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .zoom(16)
                        .build();
                mapboxMap.setCameraPosition(cameraPosition);


                listTaskPresenter.onMapReady();
                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public boolean onMapClick(@NonNull LatLng point) {
                        listTaskPresenter.onMapClicked(mapboxMap, point);
                        return false;
                    }
                });

                displayMyLocationAtButton();
            }
        });

    }

    private void displayMyLocationAtButton() {
        if (myLocationButton != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) myLocationButton.getLayoutParams();
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.bottomMargin = org.smartregister.reveal.util.Utils.getInterventionLabel() == R.string.irs ? progressIndicatorsGroupView.getHeight() + 40 : params.topMargin;
            params.topMargin = 0;
            myLocationButton.setLayoutParams(params);
        }
    }

    private void initializeProgressIndicatorViews() {
        progressIndicatorsGroupView = findViewById(R.id.progressIndicatorsGroupView);
        progressIndicatorsGroupView.setBackgroundColor(this.getResources().getColor(R.color.transluscent_white));
        progressIndicatorsGroupView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_add_structure) {
            listTaskPresenter.onAddStructureClicked(revealMapHelper.isMyLocationComponentActive(this, myLocationButton));
        } else if (v.getId() == R.id.change_spray_status) {
            listTaskPresenter.onChangeInterventionStatus(IRS);
        } else if (v.getId() == R.id.btn_record_mosquito_collection) {
            listTaskPresenter.onChangeInterventionStatus(MOSQUITO_COLLECTION);
        } else if (v.getId() == R.id.btn_record_larval_dipping) {
            listTaskPresenter.onChangeInterventionStatus(LARVAL_DIPPING);
        } else if (v.getId() == R.id.btn_edit_paot_details) {
            listTaskPresenter.onChangeInterventionStatus(PAOT);
        } else if (v.getId() == R.id.btn_collapse_spray_card_view) {
            setViewVisibility(tvReason, false);
            closeCardView(v.getId());
        } else if (v.getId() == R.id.register_family) {
            registerFamily();
        } else if (v.getId() == R.id.btn_collapse_mosquito_collection_card_view
                || v.getId() == R.id.btn_collapse_larval_breeding_card_view
                || v.getId() == R.id.btn_collapse_paot_card_view
                || v.getId() == R.id.btn_collapse_indicators_card_view) {
            closeCardView(v.getId());
        } else if (v.getId() == R.id.task_register) {
            openTaskRegister();
        } else if (v.getId() == R.id.drawerMenu) {
            drawerView.openDrawerLayout();
        } else if (v.getId() == R.id.progressIndicatorsGroupView) {
            openIndicatorsCardView();
        }
    }

    private void openIndicatorsCardView() {

        setViewVisibility(indicatorsCardView, true);
    }

    private void openTaskRegister() {
        Intent intent = new Intent(this, TaskRegisterActivity.class);
        intent.putExtra(TaskRegister.INTERVENTION_TYPE, getString(listTaskPresenter.getInterventionLabel()));
        if (getUserCurrentLocation() != null) {
            intent.putExtra(TaskRegister.LAST_USER_LOCATION, getUserCurrentLocation());
        }
        startActivity(intent);
    }


    @Override
    public void openStructureProfile(CommonPersonObjectClient family) {

        Intent intent = new Intent(getActivity(), Utils.metadata().profileActivity);
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_BASE_ENTITY_ID, family.getCaseId());
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_HEAD, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.FAMILY_HEAD, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.PRIMARY_CAREGIVER, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.PRIMARY_CAREGIVER, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_NAME, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.FIRST_NAME, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.GO_TO_DUE_PAGE, false);


        intent.putExtra(Properties.LOCATION_UUID, listTaskPresenter.getSelectedFeature().id());
        intent.putExtra(Properties.TASK_IDENTIFIER, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_IDENTIFIER));
        intent.putExtra(Properties.TASK_BUSINESS_STATUS, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_BUSINESS_STATUS));
        intent.putExtra(Properties.TASK_STATUS, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_STATUS));

        startActivity(intent);
    }


    @Override
    public void registerFamily() {
        clearSelectedFeature();
        Intent intent = new Intent(this, FamilyRegisterActivity.class);
        intent.putExtra(START_REGISTRATION, true);
        Feature feature = listTaskPresenter.getSelectedFeature();
        intent.putExtra(Properties.LOCATION_UUID, feature.id());
        intent.putExtra(Properties.TASK_IDENTIFIER, feature.getStringProperty(Properties.TASK_IDENTIFIER));
        intent.putExtra(Properties.TASK_BUSINESS_STATUS, feature.getStringProperty(Properties.TASK_BUSINESS_STATUS));
        intent.putExtra(Properties.TASK_STATUS, feature.getStringProperty(Properties.TASK_STATUS));
        if (feature.hasProperty(Properties.STRUCTURE_NAME))
            intent.putExtra(Properties.STRUCTURE_NAME, feature.getStringProperty(Properties.STRUCTURE_NAME));
        startActivity(intent);

    }

    @Override
    public void onLocationComponentInitialized() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            LocationComponent locationComponent = kujakuMapView.getMapboxLocationComponentWrapper()
                    .getLocationComponent();
            locationComponent.applyStyle(getApplicationContext(), R.style.LocationComponentStyling);
            locationComponent.setRenderMode(RenderMode.COMPASS);
        }
    }

    @Override
    public void setGeoJsonSource(@NonNull FeatureCollection featureCollection, Feature operationalArea, boolean isChangeMapPosition) {
        if (geoJsonSource != null) {
            double currentZoom = mMapboxMap.getCameraPosition().zoom;
            geoJsonSource.setGeoJson(featureCollection);
            if (operationalArea != null) {
                CameraPosition cameraPosition = mMapboxMap.getCameraForGeometry(operationalArea.geometry());

                if (listTaskPresenter.getInterventionLabel() == R.string.focus_investigation) {
                    Feature indexCase = revealMapHelper.getIndexCase(featureCollection);
                    if (indexCase != null) {
                        Location center = new RevealMappingHelper().getCenter(indexCase.geometry().toJson());
                        cameraPosition = new CameraPosition.Builder()
                                .target(new LatLng(center.getLatitude(), center.getLongitude())).zoom(currentZoom).build();
                    }
                }

                if (cameraPosition != null && (boundaryLayer == null || isChangeMapPosition)) {
                    mMapboxMap.setCameraPosition(cameraPosition);
                }

                Boolean drawOperationalAreaBoundaryAndLabel = getDrawOperationalAreaBoundaryAndLabel();
                if (drawOperationalAreaBoundaryAndLabel) {
                    if (boundaryLayer == null) {
                        boundaryLayer = createBoundaryLayer(operationalArea);
                        kujakuMapView.addLayer(boundaryLayer);
                    } else {
                        boundaryLayer.updateFeatures(FeatureCollection.fromFeature(operationalArea));
                    }
                }

                if (listTaskPresenter.getInterventionLabel() == R.string.focus_investigation && revealMapHelper.getIndexCaseLineLayer() == null) {
                    revealMapHelper.addIndexCaseLayers(mMapboxMap, getContext(), featureCollection);
                } else {
                    revealMapHelper.updateIndexCaseLayers(mMapboxMap, featureCollection, this);
                }
            }
        }
    }

    private BoundaryLayer createBoundaryLayer(Feature operationalArea) {
        return new BoundaryLayer.Builder(FeatureCollection.fromFeature(operationalArea))
                .setLabelProperty(org.smartregister.reveal.util.Constants.Map.NAME_PROPERTY)
                .setLabelTextSize(getResources().getDimension(R.dimen.operational_area_boundary_text_size))
                .setLabelColorInt(Color.WHITE)
                .setBoundaryColor(Color.WHITE)
                .setBoundaryWidth(getResources().getDimension(R.dimen.operational_area_boundary_width)).build();
    }

    @Override
    public void displayNotification(int title, int message, Object... formatArgs) {
        AlertDialogUtils.displayNotification(this, title, message, formatArgs);
    }

    @Override
    public void displayNotification(String message) {
        AlertDialogUtils.displayNotification(this, message);
    }

    @Override
    public void openCardView(CardDetails cardDetails) {
        CardDetailsUtil cardDetailsUtil = new CardDetailsUtil();
        if (cardDetails instanceof SprayCardDetails) {
            cardDetailsUtil.populateSprayCardTextViews((SprayCardDetails) cardDetails, this);
            sprayCardView.setVisibility(View.VISIBLE);
        } else if (cardDetails instanceof MosquitoHarvestCardDetails) {
            cardDetailsUtil.populateAndOpenMosquitoHarvestCard((MosquitoHarvestCardDetails) cardDetails, this);
        }
    }

    @Override
    public void startJsonForm(JSONObject form) {
        jsonFormUtils.startJsonForm(form, this);
    }

    @Override
    public void displaySelectedFeature(Feature feature, LatLng point) {
        displaySelectedFeature(feature, point, mMapboxMap.getCameraPosition().zoom);
    }

    @Override
    public void displaySelectedFeature(Feature feature, LatLng clickedPoint, double zoomlevel) {
        adjustFocusPoint(clickedPoint);
        kujakuMapView.centerMap(clickedPoint, ANIMATE_TO_LOCATION_DURATION, zoomlevel);
        if (selectedGeoJsonSource != null) {
            selectedGeoJsonSource.setGeoJson(FeatureCollection.fromFeature(feature));
        }
    }

    private void adjustFocusPoint(LatLng point) {
        int screenSize = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (screenSize == Configuration.SCREENLAYOUT_SIZE_NORMAL || screenSize == Configuration.SCREENLAYOUT_SIZE_SMALL) {
            point.setLatitude(point.getLatitude() + VERTICAL_OFFSET);
        }
    }

    @Override
    public void clearSelectedFeature() {
        if (selectedGeoJsonSource != null) {
            try {
                selectedGeoJsonSource.setGeoJson(new com.cocoahero.android.geojson.FeatureCollection().toJSON().toString());
            } catch (JSONException e) {
                Timber.e(e, "Error clearing selected feature");
            }
        }
    }

    @Override
    public void displayToast(@StringRes int resourceId) {
        Toast.makeText(this, resourceId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_GET_JSON && resultCode == RESULT_OK && data.hasExtra(JSON_FORM_PARAM_JSON)) {
            String json = data.getStringExtra(JSON_FORM_PARAM_JSON);
            Timber.d(json);
            listTaskPresenter.saveJsonForm(json);
        } else if (requestCode == Constants.RequestCode.LOCATION_SETTINGS && hasRequestedLocation) {
            if (resultCode == RESULT_OK) {
                listTaskPresenter.getLocationPresenter().waitForUserLocation();
            } else if (resultCode == RESULT_CANCELED) {
                listTaskPresenter.getLocationPresenter().onGetUserLocationFailed();
            }
            hasRequestedLocation = false;
        }
    }

    private void initializeProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(R.string.fetching_structures_title);
        progressDialog.setMessage(getString(R.string.fetching_structures_message));
    }

    @Override
    public void showProgressDialog(@StringRes int title, @StringRes int message) {
        if (progressDialog != null) {
            progressDialog.setTitle(title);
            progressDialog.setMessage(getString(message));
            progressDialog.show();
        }
    }

    @Override
    public void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    public Location getUserCurrentLocation() {
        return kujakuMapView.getLocationClient() == null ? null : kujakuMapView.getLocationClient().getLastLocation();
    }

    @Override
    public void requestUserLocation() {
        kujakuMapView.setWarmGps(true, getString(R.string.location_service_disabled), getString(R.string.location_services_disabled_spray));
        hasRequestedLocation = true;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void onDestroy() {
        listTaskPresenter = null;
        super.onDestroy();
    }

    @Override
    public void onSyncStart() {
        if (SyncStatusBroadcastReceiver.getInstance().isSyncing()) {
            syncProgressSnackbar.show();
        }
    }

    @Override
    public void onSyncInProgress(FetchStatus fetchStatus) {
        syncProgressSnackbar.dismiss();
        if (fetchStatus.equals(FetchStatus.fetchedFailed)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_failed, Snackbar.LENGTH_LONG).show();
        } else if (fetchStatus.equals(FetchStatus.fetched)
                || fetchStatus.equals(FetchStatus.nothingFetched)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_complete, Snackbar.LENGTH_LONG).show();
        } else if (fetchStatus.equals(FetchStatus.noConnection)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_failed_no_internet, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSyncComplete(FetchStatus fetchStatus) {
        onSyncInProgress(fetchStatus);
    }

    @Override
    public void onResume() {
        super.onResume();
        SyncStatusBroadcastReceiver.getInstance().addSyncStatusListener(this);
        IntentFilter filter = new IntentFilter(Action.STRUCTURE_TASK_SYNCED);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(refreshGeowidgetReceiver, filter);
        drawerView.onResume();
        listTaskPresenter.onResume();
    }

    @Override
    public void onPause() {
        SyncStatusBroadcastReceiver.getInstance().removeSyncStatusListener(this);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(refreshGeowidgetReceiver);
        RevealApplication.getInstance().setMyLocationComponentEnabled(revealMapHelper.isMyLocationComponentActive(this, myLocationButton));
        super.onPause();
    }

    @Override
    public void onDrawerClosed() {
        listTaskPresenter.onDrawerClosed();
    }

    @Override
    public AppCompatActivity getActivity() {
        return this;
    }

    @Override
    public RevealJsonFormUtils getJsonFormUtils() {
        return jsonFormUtils;
    }

    @Override
    public void focusOnUserLocation(boolean focusOnUserLocation) {
        kujakuMapView.focusOnUserLocation(focusOnUserLocation);
    }

    @Override
    public boolean isMyLocationComponentActive() {
        return revealMapHelper.isMyLocationComponentActive(this, myLocationButton);
    }

    private class RefreshGeowidgetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            boolean localSyncDone;
            if (extras != null && extras.getBoolean(UPDATE_LOCATION_BUFFER_RADIUS)) {
                float bufferRadius = getLocationBuffer() / getPixelsPerDPI(getResources());
                kujakuMapView.setLocationBufferRadius(bufferRadius);
            } else {
                localSyncDone = extras != null && extras.getBoolean(LOCAL_SYNC_DONE);
                listTaskPresenter.refreshStructures(localSyncDone);
            }
        }
    }
}
