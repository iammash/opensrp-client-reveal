package org.smartregister.reveal.view;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
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
import com.mapbox.pluginscalebar.ScaleBarOptions;
import com.mapbox.pluginscalebar.ScaleBarPlugin;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.domain.FetchStatus;
import org.smartregister.domain.SyncProgress;
import org.smartregister.domain.Task;
import org.smartregister.family.activity.FamilyWizardFormActivity;
import org.smartregister.family.util.DBConstants;
import org.smartregister.family.util.Utils;
import org.smartregister.receiver.SyncProgressBroadcastReceiver;
import org.smartregister.receiver.SyncStatusBroadcastReceiver;
import org.smartregister.reporting.view.ProgressIndicatorView;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.BaseDrawerContract;
import org.smartregister.reveal.contract.ListTaskContract;
import org.smartregister.reveal.contract.UserLocationContract.UserLocationView;
import org.smartregister.reveal.exception.QRCodeAssignException;
import org.smartregister.reveal.exception.QRCodeSearchException;
import org.smartregister.reveal.model.CardDetails;
import org.smartregister.reveal.model.FamilyCardDetails;
import org.smartregister.reveal.model.IRSVerificationCardDetails;
import org.smartregister.reveal.model.MosquitoHarvestCardDetails;
import org.smartregister.reveal.model.SprayCardDetails;
import org.smartregister.reveal.model.TaskFilterParams;
import org.smartregister.reveal.presenter.ListTaskPresenter;
import org.smartregister.reveal.repository.RevealMappingHelper;
import org.smartregister.reveal.util.AlertDialogUtils;
import org.smartregister.reveal.util.CardDetailsUtil;
import org.smartregister.reveal.util.Constants.Action;
import org.smartregister.reveal.util.Constants.Properties;
import org.smartregister.reveal.util.Constants.TaskRegister;
import org.smartregister.reveal.util.Country;
import org.smartregister.reveal.util.FamilyConstants;
import org.smartregister.reveal.util.RevealJsonFormUtils;
import org.smartregister.reveal.util.RevealMapHelper;
import org.smartregister.reveal.util.ViewGroupUtils;
import org.smartregister.util.PermissionUtils;
import org.smartregister.view.activity.BarcodeScanActivity;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

import io.ona.kujaku.callbacks.OnLocationComponentInitializedCallback;
import io.ona.kujaku.layers.BoundaryLayer;
import io.ona.kujaku.listeners.OnFeatureLongClickListener;
import io.ona.kujaku.utils.Constants;
import timber.log.Timber;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static org.smartregister.reveal.util.Constants.ANIMATE_TO_LOCATION_DURATION;
import static org.smartregister.reveal.util.Constants.CONFIGURATION.LOCAL_SYNC_DONE;
import static org.smartregister.reveal.util.Constants.CONFIGURATION.UPDATE_LOCATION_BUFFER_RADIUS;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.STRUCTURE_ID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.TASK_ID;
import static org.smartregister.reveal.util.Constants.Filter.FILTER_SORT_PARAMS;
import static org.smartregister.reveal.util.Constants.Intervention.IRS;
import static org.smartregister.reveal.util.Constants.Intervention.LARVAL_DIPPING;
import static org.smartregister.reveal.util.Constants.Intervention.MOSQUITO_COLLECTION;
import static org.smartregister.reveal.util.Constants.Intervention.PAOT;
import static org.smartregister.reveal.util.Constants.JSON_FORM_PARAM_JSON;
import static org.smartregister.reveal.util.Constants.JsonForm.ENCOUNTER_TYPE;
import static org.smartregister.reveal.util.Constants.RequestCode.REQUEST_CODE_FAMILY_PROFILE;
import static org.smartregister.reveal.util.Constants.RequestCode.REQUEST_CODE_FILTER_TASKS;
import static org.smartregister.reveal.util.Constants.RequestCode.REQUEST_CODE_GET_JSON;
import static org.smartregister.reveal.util.Constants.RequestCode.REQUEST_CODE_ISSUE_QR;
import static org.smartregister.reveal.util.Constants.RequestCode.REQUEST_CODE_TASK_LISTS;
import static org.smartregister.reveal.util.Constants.VERTICAL_OFFSET;
import static org.smartregister.reveal.util.FamilyConstants.Intent.START_REGISTRATION;
import static org.smartregister.reveal.util.Utils.displayDistanceScale;
import static org.smartregister.reveal.util.Utils.getDrawOperationalAreaBoundaryAndLabel;
import static org.smartregister.reveal.util.Utils.getLocationBuffer;
import static org.smartregister.reveal.util.Utils.getPixelsPerDPI;

/**
 * Created by samuelgithengi on 11/20/18.
 */
public class ListTasksActivity extends BaseMapActivity implements ListTaskContract.ListTaskView,
        View.OnClickListener, SyncStatusBroadcastReceiver.SyncStatusListener, UserLocationView, OnLocationComponentInitializedCallback, SyncProgressBroadcastReceiver.SyncProgressListener {

    private ListTaskPresenter listTaskPresenter;

    private View rootView;

    private GeoJsonSource geoJsonSource;

    private GeoJsonSource selectedGeoJsonSource;

    private ProgressDialog progressDialog;

    private MapboxMap mMapboxMap;

    private CardView sprayCardView;

    private CardView eligibilityCardView;

    private TextView tvReason;

    private CardView mosquitoCollectionCardView;
    private CardView larvalBreedingCardView;
    private CardView potentialAreaOfTransmissionCardView;
    private CardView indicatorsCardView;
    private CardView irsVerificationCardView;

    private RefreshGeowidgetReceiver refreshGeowidgetReceiver = new RefreshGeowidgetReceiver();

    private SyncProgressBroadcastReceiver syncProgressBroadcastReceiver = new SyncProgressBroadcastReceiver(this);

    private boolean hasRequestedLocation;

    private Snackbar syncProgressSnackbar;

    private BaseDrawerContract.View drawerView;

    private RevealJsonFormUtils jsonFormUtils;

    private BoundaryLayer boundaryLayer;

    private RevealMapHelper revealMapHelper;

    private ImageButton myLocationButton;

    private ImageButton layerSwitcherFab;

    private ImageButton filterTasksFab;

    private FrameLayout filterCountLayout;

    private TextView filterCountTextView;

    private EditText searchView;

    private CardDetailsUtil cardDetailsUtil = new CardDetailsUtil();

    private String nextAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.BUILD_COUNTRY == Country.THAILAND || BuildConfig.BUILD_COUNTRY == Country.THAILAND_EN) {
            setContentView(R.layout.thailand_activity_list_tasks);
        } else if (BuildConfig.BUILD_COUNTRY == Country.NTD_COMMUNITY) {
            setContentView(R.layout.ntd_community_activity_list);
        } else {
            setContentView(R.layout.activity_list_tasks);
        }

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

        boolean hasQRSearch = BuildConfig.BUILD_COUNTRY.equals(Country.NTD_COMMUNITY);
        findViewById(R.id.btn_qr_code).setVisibility(hasQRSearch ? View.VISIBLE : View.GONE);
        if (hasQRSearch)
            findViewById(R.id.btn_qr_code).setOnClickListener(v -> scanQRCode());

        initializeCardViews();

        initializeToolbar();

        syncProgressSnackbar = Snackbar.make(rootView, getString(org.smartregister.R.string.syncing), Snackbar.LENGTH_INDEFINITE);
    }

    private void scanQRCode() {
        if (PermissionUtils.isPermissionGranted(this, Manifest.permission.CAMERA, AllConstants.BARCODE.BARCODE_REQUEST_CODE)) {
            try {
                Intent intent = new Intent(this, BarcodeScanActivity.class);
                startActivityForResult(intent, AllConstants.BARCODE.BARCODE_REQUEST_CODE);
            } catch (SecurityException e) {
                Utils.showToast(this, getString(R.string.allow_camera_management));
            }
        }
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

        eligibilityCardView = findViewById(R.id.mark_eligibility_status);

        findViewById(R.id.tv_eligible).setOnClickListener(this);

        findViewById(R.id.tv_not_eligible).setOnClickListener(this);

        findViewById(R.id.btn_collapse_eligibility_card_view).setOnClickListener(this);

        mosquitoCollectionCardView = findViewById(R.id.mosquito_collection_card_view);

        larvalBreedingCardView = findViewById(R.id.larval_breeding_card_view);

        potentialAreaOfTransmissionCardView = findViewById(R.id.potential_area_of_transmission_card_view);

        irsVerificationCardView = findViewById(R.id.irs_verification_card_view);

        findViewById(R.id.btn_add_structure).setOnClickListener(this);

        findViewById(R.id.btn_collapse_spray_card_view).setOnClickListener(this);

        tvReason = findViewById(R.id.reason);

        findViewById(R.id.change_spray_status).setOnClickListener(this);

        findViewById(R.id.btn_undo_spray).setOnClickListener(this);

        findViewById(R.id.register_family).setOnClickListener(this);

        findViewById(R.id.task_register).setOnClickListener(this);

        findViewById(R.id.btn_collapse_mosquito_collection_card_view).setOnClickListener(this);

        findViewById(R.id.btn_record_mosquito_collection).setOnClickListener(this);

        findViewById(R.id.btn_undo_mosquito_collection).setOnClickListener(this);

        findViewById(R.id.btn_collapse_larval_breeding_card_view).setOnClickListener(this);

        findViewById(R.id.btn_record_larval_dipping).setOnClickListener(this);

        findViewById(R.id.btn_undo_larval_dipping).setOnClickListener(this);

        findViewById(R.id.btn_collapse_paot_card_view).setOnClickListener(this);

        findViewById(R.id.btn_edit_paot_details).setOnClickListener(this);

        findViewById(R.id.btn_undo_paot_details).setOnClickListener(this);

        findViewById(R.id.btn_collapse_irs_verification_card_view).setOnClickListener(this);

        indicatorsCardView = findViewById(R.id.indicators_card_view);
        indicatorsCardView.setOnClickListener(this);

        findViewById(R.id.btn_collapse_indicators_card_view).setOnClickListener(this);

        findViewById(R.id.register_family).setOnClickListener(this);

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
        } else if (id == R.id.btn_collapse_irs_verification_card_view) {
            setViewVisibility(irsVerificationCardView, false);
        } else if (id == R.id.btn_collapse_eligibility_card_view) {
            setViewVisibility(eligibilityCardView, false);
        }
    }

    @Override
    public void closeAllCardViews() {
        setViewVisibility(sprayCardView, false);
        setViewVisibility(mosquitoCollectionCardView, false);
        setViewVisibility(larvalBreedingCardView, false);
        setViewVisibility(potentialAreaOfTransmissionCardView, false);
        setViewVisibility(indicatorsCardView, false);
        setViewVisibility(irsVerificationCardView, false);
        setViewVisibility(eligibilityCardView, false);
    }

    private void setViewVisibility(View view, boolean isVisible) {
        view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void initializeMapView(Bundle savedInstanceState) {
        kujakuMapView = findViewById(R.id.kujakuMapView);

        myLocationButton = findViewById(R.id.ib_mapview_focusOnMyLocationIcon);

        if(getBuildCountry() == Country.NTD_COMMUNITY)
            ViewGroupUtils.replaceView(findViewById(R.id.bt_current_location), findViewById(R.id.ib_mapview_focusOnMyLocationIcon));


        layerSwitcherFab = findViewById(R.id.fab_mapview_layerSwitcher);

        kujakuMapView.getMapboxLocationComponentWrapper().setOnLocationComponentInitializedCallback(this);

        kujakuMapView.onCreate(savedInstanceState);

        kujakuMapView.showCurrentLocationBtn(true);

        kujakuMapView.setDisableMyLocationOnMapMove(true);

        Float locationBufferRadius = getLocationBuffer();

        kujakuMapView.setLocationBufferRadius(locationBufferRadius / getPixelsPerDPI(getResources()));

        kujakuMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                Style.Builder builder = new Style.Builder().fromUri(getString(R.string.reveal_satellite_style));
                mapboxMap.setStyle(builder, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {

                        geoJsonSource = style.getSourceAs(getString(R.string.reveal_datasource_name));

                        selectedGeoJsonSource = style.getSourceAs(getString(R.string.selected_datasource_name));
                        RevealMapHelper.addCustomLayers(style, ListTasksActivity.this);

                        RevealMapHelper.addBaseLayers(kujakuMapView, style, ListTasksActivity.this);

                        if (getBuildCountry() != Country.ZAMBIA) {
                            layerSwitcherFab.setVisibility(View.GONE);
                        }

                        initializeScaleBarPlugin(mapboxMap);

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
                        listTaskPresenter.onMapClicked(mapboxMap, point, false);
                        return false;
                    }
                });

                mapboxMap.addOnMapLongClickListener(new MapboxMap.OnMapLongClickListener() {
                    @Override
                    public boolean onMapLongClick(@NonNull LatLng point) {
                        listTaskPresenter.onMapClicked(mapboxMap, point, true);
                        return false;
                    }
                });

                positionMyLocationAndLayerSwitcher();
            }
        });

    }

    protected void initializeScaleBarPlugin(MapboxMap mapboxMap) {
        if (displayDistanceScale()) {
            ScaleBarPlugin scaleBarPlugin = new ScaleBarPlugin(kujakuMapView, mapboxMap);
            // Create a ScaleBarOptions object to use custom styling
            ScaleBarOptions scaleBarOptions = new ScaleBarOptions(getContext());
            scaleBarOptions.setTextColor(R.color.distance_scale_text);
            scaleBarOptions.setTextSize(R.dimen.distance_scale_text_size);

            scaleBarPlugin.create(scaleBarOptions);
        }
    }

    private void positionMyLocationAndLayerSwitcher(FrameLayout.LayoutParams myLocationButtonParams, int bottomMargin) {

        if (myLocationButton != null) {
            myLocationButtonParams.gravity = Gravity.BOTTOM | Gravity.END;
            myLocationButtonParams.bottomMargin = bottomMargin;
            myLocationButtonParams.topMargin = 0;
            myLocationButton.setLayoutParams(myLocationButtonParams);
        }

    }

    public void positionMyLocationAndLayerSwitcher() {
        if(getBuildCountry() == Country.NTD_COMMUNITY) return;

        FrameLayout.LayoutParams myLocationButtonParams = (FrameLayout.LayoutParams) myLocationButton.getLayoutParams();
        if (getBuildCountry() != Country.ZAMBIA) {
            positionMyLocationAndLayerSwitcher(myLocationButtonParams, myLocationButtonParams.topMargin);
        } else {
            int progressHeight = getResources().getDimensionPixelSize(R.dimen.progress_height);

            int bottomMargin = org.smartregister.reveal.util.Utils.getInterventionLabel() == R.string.irs ? progressHeight + 40 : 40;
            positionMyLocationAndLayerSwitcher(myLocationButtonParams, bottomMargin);

            if (layerSwitcherFab != null) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layerSwitcherFab.getLayoutParams();
                //position the layer selector above location button and with similar bottom margin
                if (org.smartregister.reveal.util.Utils.getInterventionLabel() == R.string.irs)
                    params.bottomMargin = myLocationButton.getMeasuredHeight() + progressHeight + 80;
                else
                    params.bottomMargin = myLocationButton.getMeasuredHeight() + bottomMargin + 40;
                //Make the layer selector is same size as my location button
                params.height = myLocationButton.getMeasuredHeight();
                params.width = myLocationButton.getMeasuredWidth();
                params.rightMargin = getResources().getDimensionPixelOffset(R.dimen.my_location_btn_margin);
                layerSwitcherFab.setScaleType(FloatingActionButton.ScaleType.CENTER);
                layerSwitcherFab.setLayoutParams(params);
            }
        }
    }

    private void initializeProgressIndicatorViews() {
        LinearLayout progressIndicatorsGroupView = findViewById(R.id.progressIndicatorsGroupView);
        progressIndicatorsGroupView.setBackgroundColor(this.getResources().getColor(R.color.transluscent_white));
        progressIndicatorsGroupView.setOnClickListener(this);
    }

    private void initializeToolbar() {
        searchView = findViewById(R.id.edt_search);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { //do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {//do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                listTaskPresenter.searchTasks(s.toString());
            }
        });
        filterTasksFab = findViewById(R.id.filter_tasks_fab);
        filterCountLayout = findViewById(R.id.filter_tasks_count_layout);
        filterCountTextView = findViewById(R.id.filter_tasks_count);

        filterTasksFab.setOnClickListener(this);
        filterCountLayout.setOnClickListener(this);
    }

    private void onAddItemClicked() {
        if (BuildConfig.BUILD_COUNTRY.equals(Country.NTD_COMMUNITY)) {
            AlertDialogUtils.displayNotificationWithCallback(getContext(), R.string.registration_type,
                    R.string.registration_type_message, R.string.registration_type_structure, R.string.registration_type_family, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == BUTTON_POSITIVE) {
                                listTaskPresenter.onAddStructureClicked(revealMapHelper.isMyLocationComponentActive(getContext(), myLocationButton));
                            } else if (which == BUTTON_NEGATIVE) {
                                listTaskPresenter.clearSelectedFeature();
                                registerFamily();
                            }
                            dialog.dismiss();
                        }
                    });
        } else {
            listTaskPresenter.onAddStructureClicked(revealMapHelper.isMyLocationComponentActive(this, myLocationButton));
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_add_structure) {
            onAddItemClicked();
        } else if (v.getId() == R.id.change_spray_status) {
            listTaskPresenter.onChangeInterventionStatus(IRS);
        } else if (v.getId() == R.id.btn_undo_spray) {
            displayResetInterventionTaskDialog(IRS);
        } else if (v.getId() == R.id.btn_record_mosquito_collection) {
            listTaskPresenter.onChangeInterventionStatus(MOSQUITO_COLLECTION);
        } else if (v.getId() == R.id.btn_undo_mosquito_collection) {
            displayResetInterventionTaskDialog(MOSQUITO_COLLECTION);
        } else if (v.getId() == R.id.btn_record_larval_dipping) {
            listTaskPresenter.onChangeInterventionStatus(LARVAL_DIPPING);
        } else if (v.getId() == R.id.btn_undo_larval_dipping) {
            displayResetInterventionTaskDialog(LARVAL_DIPPING);
        } else if (v.getId() == R.id.btn_edit_paot_details) {
            listTaskPresenter.onChangeInterventionStatus(PAOT);
        } else if (v.getId() == R.id.btn_undo_paot_details) {
            displayResetInterventionTaskDialog(PAOT);
        } else if (v.getId() == R.id.btn_collapse_spray_card_view) {
            setViewVisibility(tvReason, false);
            closeCardView(v.getId());
        } else if (v.getId() == R.id.register_family) {
            registerFamily();
            closeCardView(R.id.btn_collapse_spray_card_view);
        } else if (v.getId() == R.id.btn_collapse_mosquito_collection_card_view
                || v.getId() == R.id.btn_collapse_larval_breeding_card_view
                || v.getId() == R.id.btn_collapse_paot_card_view
                || v.getId() == R.id.btn_collapse_indicators_card_view
                || v.getId() == R.id.btn_collapse_irs_verification_card_view
                || v.getId() == R.id.btn_collapse_eligibility_card_view) {
            closeCardView(v.getId());
        } else if (v.getId() == R.id.task_register) {
            listTaskPresenter.onOpenTaskRegisterClicked();
        } else if (v.getId() == R.id.drawerMenu) {
            drawerView.openDrawerLayout();
        } else if (v.getId() == R.id.progressIndicatorsGroupView) {
            openIndicatorsCardView();
        } else if (v.getId() == R.id.filter_tasks_fab || v.getId() == R.id.filter_tasks_count_layout) {
            listTaskPresenter.onFilterTasksClicked();
        } else if (v.getId() == R.id.tv_eligible) {
            startEligibilityForm();
            closeCardView(R.id.btn_collapse_eligibility_card_view);
        } else if (v.getId() == R.id.tv_not_eligible) {
            closeCardView(R.id.btn_collapse_eligibility_card_view);
            listTaskPresenter.onMarkStructureInEligible(listTaskPresenter.getSelectedFeature());
        }
    }

    @Override
    public void openFilterTaskActivity(TaskFilterParams filterParams) {
        Intent intent = new Intent(getContext(), FilterTasksActivity.class);
        intent.putExtra(FILTER_SORT_PARAMS, filterParams);
        startActivityForResult(intent, REQUEST_CODE_FILTER_TASKS);
    }

    private void openIndicatorsCardView() {
        setViewVisibility(indicatorsCardView, true);
    }

    @Override
    public void openTaskRegister(TaskFilterParams filterParams) {
        Intent intent = new Intent(this, TaskRegisterActivity.class);
        intent.putExtra(TaskRegister.INTERVENTION_TYPE, getString(listTaskPresenter.getInterventionLabel()));
        if (getUserCurrentLocation() != null) {
            intent.putExtra(TaskRegister.LAST_USER_LOCATION, getUserCurrentLocation());
        }
        if (filterParams != null) {
            filterParams.setSearchPhrase(searchView.getText().toString());
            intent.putExtra(FILTER_SORT_PARAMS, filterParams);
        } else if (StringUtils.isNotBlank(searchView.getText())) {
            intent.putExtra(FILTER_SORT_PARAMS, new TaskFilterParams(searchView.getText().toString()));
        }
        startActivityForResult(intent, REQUEST_CODE_TASK_LISTS);
    }


    @Override
    public void openStructureProfile(CommonPersonObjectClient family) {

        Intent intent = new Intent(getActivity(), Utils.metadata().profileActivity);
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_BASE_ENTITY_ID, family.getCaseId());
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_HEAD, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.FAMILY_HEAD, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.PRIMARY_CAREGIVER, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.PRIMARY_CAREGIVER, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.FAMILY_NAME, Utils.getValue(family.getColumnmaps(), DBConstants.KEY.FIRST_NAME, false));
        intent.putExtra(org.smartregister.family.util.Constants.INTENT_KEY.GO_TO_DUE_PAGE, false);

        if (listTaskPresenter.getSelectedFeature() != null) {
            intent.putExtra(Properties.LOCATION_UUID, listTaskPresenter.getSelectedFeature().id());
            intent.putExtra(Properties.TASK_IDENTIFIER, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_IDENTIFIER));
            intent.putExtra(Properties.TASK_BUSINESS_STATUS, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_BUSINESS_STATUS));
            intent.putExtra(Properties.TASK_STATUS, listTaskPresenter.getSelectedFeature().getStringProperty(Properties.TASK_STATUS));
        }

        startActivityForResult(intent, REQUEST_CODE_FAMILY_PROFILE);
    }


    @Override
    public void registerFamily() {
        clearSelectedFeature();

        if (Country.NTD_COMMUNITY == BuildConfig.BUILD_COUNTRY) {
            listTaskPresenter.startFamilyRegistrationForm(getContext());
        } else {
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

    }

    @Override
    public void onLocationComponentInitialized() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            LocationComponent locationComponent = kujakuMapView.getMapboxLocationComponentWrapper()
                    .getLocationComponent();
            locationComponent.applyStyle(getApplicationContext(), R.style.LocationComponentStyling);
        }
    }

    @Override
    public void setGeoJsonSource(@NonNull FeatureCollection featureCollection, Feature operationalArea, boolean isChangeMapPosition) {
        if (geoJsonSource != null) {
            geoJsonSource.setGeoJson(featureCollection);
            if (operationalArea != null) {
                CameraPosition cameraPosition = mMapboxMap.getCameraForGeometry(operationalArea.geometry());
                if (listTaskPresenter.getInterventionLabel() == R.string.focus_investigation) {
                    Feature indexCase = revealMapHelper.getIndexCase(featureCollection);
                    if (indexCase != null) {
                        Location center = new RevealMappingHelper().getCenter(indexCase.geometry().toJson());
                        double currentZoom = mMapboxMap.getCameraPosition().zoom;
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

                        kujakuMapView.setOnFeatureLongClickListener(new OnFeatureLongClickListener() {
                            @Override
                            public void onFeatureLongClick(List<Feature> features) {
                                listTaskPresenter.onFociBoundaryLongClicked();
                            }
                        }, boundaryLayer.getLayerIds());

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
    public void displayNotification(String title, String message) {
        AlertDialogUtils.displayNotification(this, title, message);
    }

    @Override
    public void openCardView(CardDetails cardDetails) {
        if (Country.NTD_COMMUNITY == BuildConfig.BUILD_COUNTRY) {
            eligibilityCardView.setVisibility(View.VISIBLE);
            return;
        }
        if (cardDetails instanceof SprayCardDetails) {
            cardDetailsUtil.populateSprayCardTextViews((SprayCardDetails) cardDetails, this);
            sprayCardView.setVisibility(View.VISIBLE);
        } else if (cardDetails instanceof MosquitoHarvestCardDetails) {
            cardDetailsUtil.populateAndOpenMosquitoHarvestCard((MosquitoHarvestCardDetails) cardDetails, this);
        } else if (cardDetails instanceof IRSVerificationCardDetails) {
            cardDetailsUtil.populateAndOpenIRSVerificationCard((IRSVerificationCardDetails) cardDetails, this);
        } else if (cardDetails instanceof FamilyCardDetails) {
            cardDetailsUtil.populateFamilyCard((FamilyCardDetails) cardDetails, this);
            sprayCardView.setVisibility(View.VISIBLE);
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

    public void onQRCodeSucessfullyScanned(String qrCode) {
        Timber.i("QR code: %s", qrCode);
        if (StringUtils.isNotBlank(qrCode)) {
            listTaskPresenter.searchQRCode(qrCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_GET_JSON && resultCode == RESULT_OK && data.hasExtra(JSON_FORM_PARAM_JSON)) {


            String json = data.getStringExtra(JSON_FORM_PARAM_JSON);
            Timber.d(json);

            try {
                JSONObject jsonForm = new JSONObject(json);
                String encounterType = jsonForm.getString(ENCOUNTER_TYPE);
                if (encounterType.equalsIgnoreCase(org.smartregister.reveal.util.Constants.EventType.STRUCTURE_ELIGIBILITY)) {
                    listTaskPresenter.saveEligibilityForm(jsonForm, listTaskPresenter.getSelectedFeature());
                } else if (encounterType.equalsIgnoreCase(FamilyConstants.EventType.FAMILY_REGISTRATION)) {
                    listTaskPresenter.saveFamilyRegistration(jsonForm, getContext());
                } else {
                    listTaskPresenter.saveJsonForm(json);
                }
            } catch (JSONException e) {
                Timber.e(e);
            }

        } else if (requestCode == Constants.RequestCode.LOCATION_SETTINGS && hasRequestedLocation) {
            if (resultCode == RESULT_OK) {
                listTaskPresenter.getLocationPresenter().waitForUserLocation();
            } else if (resultCode == RESULT_CANCELED) {
                listTaskPresenter.getLocationPresenter().onGetUserLocationFailed();
            }
            hasRequestedLocation = false;
        } else if (requestCode == REQUEST_CODE_FAMILY_PROFILE && resultCode == RESULT_OK && data.hasExtra(STRUCTURE_ID)) {
            String structureId = data.getStringExtra(STRUCTURE_ID);
            Task task = (Task) data.getSerializableExtra(TASK_ID);
            listTaskPresenter.resetFeatureTasks(structureId, task);
        } else if (requestCode == REQUEST_CODE_FILTER_TASKS && resultCode == RESULT_OK && data.hasExtra(FILTER_SORT_PARAMS)) {
            TaskFilterParams filterParams = (TaskFilterParams) data.getSerializableExtra(FILTER_SORT_PARAMS);
            listTaskPresenter.filterTasks(filterParams);
        } else if (requestCode == REQUEST_CODE_TASK_LISTS && resultCode == RESULT_OK && data.hasExtra(FILTER_SORT_PARAMS)) {
            TaskFilterParams filterParams = (TaskFilterParams) data.getSerializableExtra(FILTER_SORT_PARAMS);
            listTaskPresenter.setTaskFilterParams(filterParams);
        } else if (requestCode == AllConstants.BARCODE.BARCODE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Barcode barcode = data.getParcelableExtra(AllConstants.BARCODE.BARCODE_KEY);
                Timber.d("Scanned QR Code %s", barcode.displayValue);
                onQRCodeSucessfullyScanned(barcode.displayValue);
            } else
                Timber.i("NO RESULT FOR QR CODE");
        } else if (requestCode == REQUEST_CODE_ISSUE_QR && resultCode == RESULT_OK) {
            if (data != null) {
                Barcode barcode = data.getParcelableExtra(AllConstants.BARCODE.BARCODE_KEY);
                Timber.d("Scanned QR Code %s", barcode.displayValue);

                if (nextAction.equalsIgnoreCase(org.smartregister.reveal.util.Constants.BusinessStatus.WAITING_FOR_QR_AND_REGISTRATION)) {
                    listTaskPresenter.assignQRCodeToStructure(getContext(), listTaskPresenter.getSelectedFeature(), barcode.displayValue, this::registerFamily);
                } else {
                    listTaskPresenter.assignQRCodeToStructure(getContext(), listTaskPresenter.getSelectedFeature(), barcode.displayValue, null);
                }
                // either start registration or assign structure and die


            } else
                Timber.i("NO RESULT FOR QR CODE");
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
        if (SyncStatusBroadcastReceiver.getInstance().isSyncing() && org.smartregister.reveal.util.Utils.isNetworkAvailable(getContext())) {
            syncProgressSnackbar.show();
        }
        toggleProgressBarView(true);
    }

    @Override
    public void onSyncInProgress(FetchStatus fetchStatus) {
        if (FetchStatus.fetched.equals(fetchStatus)) {
            syncProgressSnackbar.show();
            return;
        }
        syncProgressSnackbar.dismiss();
        if (fetchStatus.equals(FetchStatus.fetchedFailed)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_failed, Snackbar.LENGTH_LONG).show();
        } else if (fetchStatus.equals(FetchStatus.nothingFetched)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_complete, Snackbar.LENGTH_LONG).show();
        } else if (fetchStatus.equals(FetchStatus.noConnection)) {
            Snackbar.make(rootView, org.smartregister.R.string.sync_failed_no_internet, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSyncComplete(FetchStatus fetchStatus) {
        onSyncInProgress(fetchStatus);
        //Check sync status and Update UI to show sync status
        drawerView.checkSynced();
        // revert to sync status view
        toggleProgressBarView(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        SyncStatusBroadcastReceiver.getInstance().addSyncStatusListener(this);
        IntentFilter filter = new IntentFilter(Action.STRUCTURE_TASK_SYNCED);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(refreshGeowidgetReceiver, filter);
        IntentFilter syncProgressFilter = new IntentFilter(AllConstants.SyncProgressConstants.ACTION_SYNC_PROGRESS);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(syncProgressBroadcastReceiver, syncProgressFilter);
        drawerView.onResume();
        listTaskPresenter.onResume();

        if (SyncStatusBroadcastReceiver.getInstance().isSyncing()) {
            syncProgressSnackbar.show();
            toggleProgressBarView(true);
        }
    }

    @Override
    public void onPause() {
        SyncStatusBroadcastReceiver.getInstance().removeSyncStatusListener(this);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(refreshGeowidgetReceiver);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(syncProgressBroadcastReceiver);
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
        kujakuMapView.focusOnUserLocation(focusOnUserLocation, RenderMode.COMPASS);
    }

    @Override
    public boolean isMyLocationComponentActive() {
        return revealMapHelper.isMyLocationComponentActive(this, myLocationButton);
    }

    @Override
    public void displayMarkStructureInactiveDialog() {
        AlertDialogUtils.displayNotificationWithCallback(this, R.string.mark_location_inactive,
                R.string.confirm_mark_location_inactive, R.string.confirm, R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == BUTTON_POSITIVE)
                            listTaskPresenter.onMarkStructureInactiveConfirmed();
                        dialog.dismiss();
                    }
                });
    }

    @Override
    public void setNumberOfFilters(int numberOfFilters) {
        if (numberOfFilters > 0) {
            filterTasksFab.setVisibility(View.GONE);
            filterCountLayout.setVisibility(View.VISIBLE);
            filterCountTextView.setText(String.valueOf(numberOfFilters));
        } else {
            filterTasksFab.setVisibility(View.VISIBLE);
            filterCountLayout.setVisibility(View.GONE);
        }

    }

    @Override
    public void setSearchPhrase(String searchPhrase) {
        searchView.setText(searchPhrase);
    }

    @Override
    public void startEligibilityForm() {
        try {
            Form form = new Form();
            form.setActionBarBackground(org.smartregister.family.R.color.family_actionbar);
            form.setNavigationBackground(org.smartregister.family.R.color.family_navigation);
            form.setHomeAsUpIndicator(org.smartregister.family.R.mipmap.ic_cross_white);
            form.setPreviousLabel(getResources().getString(org.smartregister.family.R.string.back));
            form.setWizard(false);

            String jsonForm = readAssetContents(getContext(), org.smartregister.reveal.util.Constants.JsonForm.NTD_COMMUNITY_ELIGIBILITY);
            JSONObject jsonObject = new JSONObject(jsonForm);

            startJSONForm(jsonObject, form);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void issueStructureQRCode(String structureId, String pendingTask) {
        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setTitle("Scan QR Code")
                .setMessage("You are required to issue a QR code, scan a new QR code patch")
                .setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == BUTTON_POSITIVE) {
                            // open a scan qr code activity
                            nextAction = pendingTask;

                            startQrCodeScanner();
                        }

                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Maybe Later", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
        alertDialog.setCancelable(false);
    }

    public void startQrCodeScanner() {
        if (PermissionUtils.isPermissionGranted(this, Manifest.permission.CAMERA, PermissionUtils.CAMERA_PERMISSION_REQUEST_CODE)) {
            try {
                Intent intent = new Intent(this, BarcodeScanActivity.class);
                startActivityForResult(intent, REQUEST_CODE_ISSUE_QR);
            } catch (SecurityException e) {
                org.smartregister.util.Utils.showToast(this, getString(org.smartregister.R.string.allow_camera_management));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PermissionUtils.CAMERA_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        Intent intent = new Intent(this, BarcodeScanActivity.class);
                        startActivityForResult(intent, REQUEST_CODE_ISSUE_QR);
                    } catch (SecurityException e) {
                        org.smartregister.util.Utils.showToast(this, getString(org.smartregister.R.string.allow_camera_management));
                    }
                } else {
                    org.smartregister.util.Utils.showToast(this, getString(org.smartregister.R.string.allow_camera_management));
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void setLoadingState(boolean state) {
        if (state) {
            showProgressDialog(R.string.please_wait, R.string.loading);
        } else {
            hideProgressDialog();
        }
    }

    @Override
    public void onError(Exception e) {
        if (e instanceof QRCodeSearchException) {
            QRCodeSearchException ex = (QRCodeSearchException) e;
            AlertDialogUtils.displayNotification(this, ex.getSearchMessage(),
                    "QR code : " + ex.getQrCode());
        } else if (e instanceof QRCodeAssignException) {

            QRCodeAssignException ex = (QRCodeAssignException) e;

            AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                    .setTitle(ex.getAssignErrorMessage())
                    .setMessage(ex.getQrCode() + " is already assigned to another structure, Would you like to scan another another QR Code?")
                    .setPositiveButton("Scan New QR", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == BUTTON_POSITIVE) {
                                // open a scan qr code activity
                                startQrCodeScanner();
                            }

                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
            alertDialog.setCancelable(false);

        } else {
            Toast.makeText(getBaseContext(), R.string.an_error_occured, Toast.LENGTH_SHORT).show();
            Timber.e(e);
        }
    }

    @Override
    public void onEligibilityStatusConfirmed(String status) {
        switch (status) {
            case org.smartregister.reveal.util.Constants.BusinessStatus.WAITING_FOR_QR_CODE:
            case org.smartregister.reveal.util.Constants.BusinessStatus.WAITING_FOR_QR_AND_REGISTRATION:
                issueStructureQRCode(listTaskPresenter.getSelectedFeature().id(), status);
                break;
            case org.smartregister.reveal.util.Constants.BusinessStatus.ELIGIBLE_WAITING_REGISTRATION:
                registerFamily();
                break;
            default:
                break;
        }
    }

    @Override
    public void promptFamilyRegistration(String structureID) {
        AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                .setTitle("Complete Family Registration")
                .setMessage("Would you like to complete family registration for this structure")
                .setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == BUTTON_POSITIVE) {
                            // open a scan qr code activity
                            registerFamily();
                        }

                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Maybe Later", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
        alertDialog.setCancelable(false);
    }

    @Override
    public void startJSONForm(JSONObject jsonObject, Form form) {
        Intent intent = new Intent(this, FamilyWizardFormActivity.class);
        intent.putExtra(org.smartregister.family.util.Constants.JSON_FORM_EXTRA.JSON, jsonObject.toString());
        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
        startActivityForResult(intent, REQUEST_CODE_GET_JSON);
    }

    public String readAssetContents(Context context, String path) {
        return org.smartregister.util.Utils.readAssetContents(context, path);
    }

    public void toggleProgressBarView(boolean syncing) {
        drawerView.toggleProgressBarView(syncing);
    }

    @Override
    public void onReportCountReloaded(Map<String, Double> reportCounts) {
        LinearLayout progressIndicatorsGroupView = findViewById(R.id.progressIndicatorsGroupView);

        ProgressIndicatorView progressIndicatorView = progressIndicatorsGroupView.findViewById(R.id.progressIndicatorViewTitle);

        Double coverage = reportCounts.get(org.smartregister.reveal.util.Constants.ReportCounts.FOUND_COVERAGE);
        progressIndicatorView.setProgress(toInt(coverage));
        progressIndicatorView.setTitle(getString(R.string.n_percent, toInt(coverage)));

        View detailedReportCardView = findViewById(R.id.indicators_card_view);

        TextView tvStructuresUnvisited = detailedReportCardView.findViewById(R.id.tvStructuresUnvisited);
        tvStructuresUnvisited.setText(getIntMapValue(reportCounts, org.smartregister.reveal.util.Constants.ReportCounts.UNVISITED_STRUCTURES));

        TextView tvPZQDistributed = detailedReportCardView.findViewById(R.id.tvPZQDistributed);
        tvPZQDistributed.setText(getMapValue(reportCounts, org.smartregister.reveal.util.Constants.ReportCounts.PZQ_DISTRIBUTED));

        TextView tvPZQRemaining = detailedReportCardView.findViewById(R.id.tvPZQRemaining);
        tvPZQRemaining.setText(getMapValue(reportCounts, org.smartregister.reveal.util.Constants.ReportCounts.PZQ_REMAINING));

        TextView tvSuccessRate = detailedReportCardView.findViewById(R.id.tvSuccessRate);
        tvSuccessRate.setText(getMapValue(reportCounts, org.smartregister.reveal.util.Constants.ReportCounts.SUCCESS_RATE) + "%");
    }

    private Integer toInt(Double value) {
        try {
            if (value != null)
                return value.intValue();
        } catch (Exception e) {
            Timber.v(e);
        }
        return 0;
    }

    private String getIntMapValue(Map<String, Double> reportCounts, String key) {
        Double value = reportCounts.get(key);
        return value == null ? "0" : Integer.toString(value.intValue());
    }

    private String getMapValue(Map<String, Double> reportCounts, String key) {
        DecimalFormat df2 = new DecimalFormat("#.##");
        df2.setRoundingMode(RoundingMode.UP);

        Double value = reportCounts.get(key);
        return value == null ? "0" : df2.format(value);
    }

    @Override
    public void onSyncProgress(SyncProgress syncProgress) {
        int progress = syncProgress.getPercentageSynced();
        while (progress > 100){
            progress  = progress / 10;
        }

        String entity = syncProgress.getSyncEntity().toString();
        ProgressBar syncProgressBar = findViewById(R.id.sync_progress_bar);
        TextView syncProgressBarLabel = findViewById(R.id.sync_progress_bar_label);
        String labelText = String.format(getResources().getString(R.string.progressBarLabel), entity, progress);
        syncProgressBar.setProgress(progress);
        syncProgressBarLabel.setText(labelText);
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

    protected Country getBuildCountry() {
        return BuildConfig.BUILD_COUNTRY;
    }


    public void displayResetInterventionTaskDialog(String interventionType) {
        AlertDialogUtils.displayNotificationWithCallback(this, R.string.undo_task_title,
                R.string.undo_task_msg, R.string.confirm, R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == BUTTON_POSITIVE)
                            listTaskPresenter.onUndoInterventionStatus(interventionType);
                        dialog.dismiss();
                    }
                });
    }
}
