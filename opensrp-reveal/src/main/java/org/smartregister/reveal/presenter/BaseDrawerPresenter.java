package org.smartregister.reveal.presenter;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;

import com.google.android.material.navigation.NavigationView;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.domain.PlanDefinition;
import org.smartregister.domain.PlanDefinition.PlanStatus;
import org.smartregister.domain.form.FormLocation;
import org.smartregister.domain.jsonmapping.Location;
import org.smartregister.domain.jsonmapping.util.TreeNode;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.BaseDrawerContract;
import org.smartregister.reveal.dao.StructureDao;
import org.smartregister.reveal.interactor.BaseDrawerInteractor;
import org.smartregister.reveal.util.Country;
import org.smartregister.reveal.util.LocationHierarchyUtil;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.util.AssetHandler;
import org.smartregister.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

import static org.smartregister.AllConstants.OPERATIONAL_AREAS;
import static org.smartregister.reveal.util.Constants.Tags.CANTON;
import static org.smartregister.reveal.util.Constants.Tags.COUNTRY;
import static org.smartregister.reveal.util.Constants.Tags.DISTRICT;
import static org.smartregister.reveal.util.Constants.Tags.HEALTH_CENTER;
import static org.smartregister.reveal.util.Constants.Tags.OPERATIONAL_AREA;
import static org.smartregister.reveal.util.Constants.Tags.PROVINCE;
import static org.smartregister.reveal.util.Constants.Tags.REGION;
import static org.smartregister.reveal.util.Constants.Tags.SCHOOL;
import static org.smartregister.reveal.util.Constants.Tags.SUB_DISTRICT;
import static org.smartregister.reveal.util.Constants.Tags.VILLAGE;
import static org.smartregister.reveal.util.Constants.Tags.ZONES;
import static org.smartregister.reveal.util.Constants.UseContextCode.INTERVENTION_TYPE;

/**
 * Created by samuelgithengi on 3/21/19.
 */
public class BaseDrawerPresenter implements BaseDrawerContract.Presenter {

    private BaseDrawerContract.View view;
    private BaseDrawerContract.DrawerActivity drawerActivity;

    private PreferencesUtil prefsUtil;

    private LocationHelper locationHelper;

    private boolean changedCurrentSelection;

    private BaseDrawerContract.Interactor interactor;

    private static TextView syncLabel;
    private static TextView syncBadge;


    private boolean viewInitialized = false;

    private RevealApplication revealApplication;

    public BaseDrawerPresenter(BaseDrawerContract.View view, BaseDrawerContract.DrawerActivity drawerActivity) {
        this.view = view;
        this.drawerActivity = drawerActivity;
        this.prefsUtil = PreferencesUtil.getInstance();
        this.locationHelper = LocationHelper.getInstance();
        interactor = new BaseDrawerInteractor(this);
        revealApplication = RevealApplication.getInstance();
    }


    private void initializeDrawerLayout() {

        view.setOperator();

        if (StringUtils.isBlank(prefsUtil.getCurrentOperationalArea())) {
            ArrayList<String> operationalAreaLevels = new ArrayList<>();
            operationalAreaLevels.add(DISTRICT);
            operationalAreaLevels.add(HEALTH_CENTER);
            operationalAreaLevels.add(VILLAGE);
            operationalAreaLevels.add(CANTON);
            operationalAreaLevels.add(SUB_DISTRICT);
            List<String> defaultLocation = locationHelper.generateDefaultLocationHierarchy(operationalAreaLevels);

            if (defaultLocation != null) {
                view.setDistrict(defaultLocation.get(0));
                ArrayList<String> levels = new ArrayList<>();
                levels.add(CANTON);
                String level;
                if (locationHelper.generateLocationHierarchyTree(false, levels).isEmpty()) {
                    level = HEALTH_CENTER;
                } else {
                    level = CANTON;
                }
                if (defaultLocation.size() > 1)
                    view.setFacility(defaultLocation.get(1), level);
            }
        } else {
            populateLocationsFromPreferences();
        }

        view.setPlan(prefsUtil.getCurrentPlan());

    }


    @Override
    public void onPlansFetched(Set<PlanDefinition> planDefinitions) {
        List<String> ids = new ArrayList<>();
        List<FormLocation> formLocations = new ArrayList<>();
        for (PlanDefinition planDefinition : planDefinitions) {
            if (!planDefinition.getStatus().equals(PlanStatus.ACTIVE)) {
                continue;
            }
            ids.add(planDefinition.getIdentifier());
            FormLocation formLocation = new FormLocation();
            formLocation.name = planDefinition.getTitle();
            formLocation.key = planDefinition.getIdentifier();
            formLocation.level = "";
            formLocations.add(formLocation);

            // get intervention type for plan
            for (PlanDefinition.UseContext useContext : planDefinition.getUseContext()) {
                if (useContext.getCode().equals(INTERVENTION_TYPE)) {
                    prefsUtil.setInterventionTypeForPlan(planDefinition.getTitle(), useContext.getValueCodableConcept());
                    break;
                }
            }

        }

        String entireTreeString = "";
        if (formLocations != null && !formLocations.isEmpty()) {
            entireTreeString = AssetHandler.javaToJsonString(formLocations,
                    new TypeToken<List<FormLocation>>() {
                    }.getType());
        }

        view.showPlanSelector(ids, entireTreeString);
    }

    private void populateLocationsFromPreferences() {
        view.setDistrict(prefsUtil.getCurrentDistrict());
        view.setFacility(prefsUtil.getCurrentFacility(), prefsUtil.getCurrentFacilityLevel());

        if (BuildConfig.BUILD_COUNTRY != Country.NTD_SCHOOL) {
            view.setOperationalArea(prefsUtil.getCurrentOperationalArea());
        } else {
            view.setOperationalArea(prefsUtil.getCurrentStructure());
        }
    }

    @Override
    public void onShowOperationalAreaSelector() {
        onExtractedLocations(locationHierarchy -> view.showOperationalAreaSelector(locationHierarchy));
    }

    private void onExtractedLocations(Consumer<Pair<String, ArrayList<String>>> consumer) {
        Pair<String, ArrayList<String>> locationHierarchy = extractLocationHierarchy();
        if (locationHierarchy == null) {//try to evict location hierachy in cache
            revealApplication.getContext().anmLocationController().evict();
            locationHierarchy = extractLocationHierarchy();
        }
        if (locationHierarchy != null) {
            consumer.accept(locationHierarchy);
        } else {
            view.displayNotification(R.string.error_fetching_location_hierarchy_title, R.string.error_fetching_location_hierarchy);
            revealApplication.getContext().userService().forceRemoteLogin(revealApplication.getContext().allSharedPreferences().fetchRegisteredANM());
        }
    }

    private Pair<String, ArrayList<String>> extractLocationHierarchy() {

        ArrayList<String> operationalAreaLevels = new ArrayList<>();
        operationalAreaLevels.add(COUNTRY);
        operationalAreaLevels.add(PROVINCE);
        operationalAreaLevels.add(REGION);
        operationalAreaLevels.add(DISTRICT);
        operationalAreaLevels.add(SUB_DISTRICT);
        operationalAreaLevels.add(ZONES);
        operationalAreaLevels.add(OPERATIONAL_AREA);
        operationalAreaLevels.add(SCHOOL);

        List<String> defaultLocation = locationHelper.generateDefaultLocationHierarchy(operationalAreaLevels,true);

        if (defaultLocation != null) {
            Map<String, TreeNode<String, Location>> map = locationHelper.map();

            // inject structures
            if (BuildConfig.BUILD_COUNTRY == Country.NTD_SCHOOL) {
                Map<String, List<org.smartregister.domain.Location>> listMap = StructureDao.getCachedStructures();
                LocationHierarchyUtil.growTree(map, listMap);
            }

            List<FormLocation> entireTree = locationHelper.generateLocationHierarchyTree(false, operationalAreaLevels, map, true);

            if (BuildConfig.BUILD_COUNTRY != Country.NTD_SCHOOL && BuildConfig.BUILD_COUNTRY != Country.NTD_COMMUNITY) {
                List<String> authorizedOperationalAreas = Arrays.asList(StringUtils.split(prefsUtil.getPreferenceValue(OPERATIONAL_AREAS), ','));
                removeUnauthorizedOperationalAreas(authorizedOperationalAreas, entireTree);
            }

            String entireTreeString = AssetHandler.javaToJsonString(entireTree,
                    new TypeToken<List<FormLocation>>() {
                    }.getType());

            return new Pair<>(entireTreeString, new ArrayList<>(defaultLocation));
        } else {
            return null;
        }
    }

    @Override
    public void onOperationalAreaSelectorClicked(ArrayList<String> name, ArrayList<String> values) {

        Timber.d("Selected Location Hierarchy: " + TextUtils.join(",", name));
        Timber.d("Selected Location Hierarchy Ids: " + TextUtils.join(",", values));
        if (name.size() <= 2)//no operational area was selected, dialog was dismissed
            return;
        ArrayList<String> operationalAreaLevels = new ArrayList<>();
        operationalAreaLevels.add(DISTRICT);
        operationalAreaLevels.add(HEALTH_CENTER);
        operationalAreaLevels.add(SUB_DISTRICT);
        operationalAreaLevels.add(CANTON);
        operationalAreaLevels.add(OPERATIONAL_AREA);
        List<FormLocation> entireTree = locationHelper.generateLocationHierarchyTree(false, operationalAreaLevels);
        int districtOffset = name.get(0).equalsIgnoreCase(Country.BOTSWANA.name()) || name.get(0).equalsIgnoreCase(Country.NAMIBIA.name()) ? 3 : 2;
        try {
            prefsUtil.setCurrentProvince(name.get(1));
            prefsUtil.setCurrentDistrict(name.get(name.size() - districtOffset));
            String operationalArea = name.get(name.size() - 1);
            String operationalAreaId = values.get(values.size() - 1);
            prefsUtil.setCurrentOperationalArea(operationalArea);
            prefsUtil.setCurrentOperationalAreaId(values.get(values.size() - 1));
            Pair<String, String> facility = getFacilityFromOperationalArea(name.get(name.size() - districtOffset), name.get(name.size() - 1), entireTree);
            if (facility != null) {
                prefsUtil.setCurrentFacility(facility.second);
                prefsUtil.setCurrentFacilityLevel(facility.first);
            }
            validateSelectedPlanId(operationalAreaId);
        } catch (NullPointerException e) {
            Timber.e(e);
        }
        changedCurrentSelection = true;
        populateLocationsFromPreferences();

        if(BuildConfig.BUILD_COUNTRY.equals(Country.NTD_COMMUNITY))
            interactor.autoSelectPlan(prefsUtil.getCurrentOperationalArea(), prefsUtil.getCurrentOperationalAreaId());

        unlockDrawerLayout();
    }

    @Override
    public void onShowStructureSelector() {
        onExtractedLocations(locationHierarchy -> view.showStructureSelector(locationHierarchy));
    }

    @Override
    public void onStructureSelectorClicked(ArrayList<String> name, ArrayList<String> values) {
        Timber.d("Selected Structure Location Hierarchy: " + TextUtils.join(",", name));
        if (name.size() <= 2)//no operational area was selected, dialog was dismissed
            return;

        // remove the last element
        ArrayList<String> names_result = new ArrayList<>(name.subList(0, name.size() - 1));
        ArrayList<String> values_result = new ArrayList<>(values.subList(0, values.size() - 1));

        prefsUtil.setCurrentStructure(name.get(name.size() - 1));
        prefsUtil.setCurrentStructureID(values.get(values.size() - 1));
        onOperationalAreaSelectorClicked(names_result,values_result);
    }


    private void removeUnauthorizedOperationalAreas(List<String> operationalAreas, List<FormLocation> entireTree) {

        for (FormLocation countryLocation : entireTree) {
            for (FormLocation provinceLocation : countryLocation.nodes) {
                if (provinceLocation.nodes == null)
                    return;
                for (FormLocation districtLocation : provinceLocation.nodes) {
                    if (districtLocation.nodes == null)
                        return;
                    List<FormLocation> toRemove = new ArrayList<>();
                    for (FormLocation operationalAreaLocation : districtLocation.nodes) {
                        if (!operationalAreas.contains(operationalAreaLocation.name))
                            toRemove.add(operationalAreaLocation);
                    }
                    districtLocation.nodes.removeAll(toRemove);
                }
            }
        }
    }

    private Pair<String, String> getFacilityFromOperationalArea(String district, String operationalArea, List<FormLocation> entireTree) {
        for (FormLocation districtLocation : entireTree) {
            if (!districtLocation.name.equals(district))
                continue;
            for (FormLocation facilityLocation : districtLocation.nodes) {
                if (facilityLocation.nodes == null)
                    continue;
                for (FormLocation operationalAreaLocation : facilityLocation.nodes) {
                    if (operationalAreaLocation.name.equals(operationalArea)) {
                        return new Pair<>(facilityLocation.level, facilityLocation.name);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void onShowPlanSelector() {
        if (StringUtils.isBlank(prefsUtil.getCurrentOperationalArea()) && StringUtils.isBlank(prefsUtil.getCurrentOperationalAreaId())) {
            view.displayNotification(R.string.operational_area, R.string.operational_area_not_selected);
        } else {
            interactor.fetchPlans(prefsUtil.getCurrentOperationalArea(), prefsUtil.getCurrentOperationalAreaId());
        }
    }


    public void onPlanSelectorClicked(ArrayList<String> value, ArrayList<String> name) {
        if (Utils.isEmptyCollection(name) || (name.size() > 1))
            return;
        Timber.d("Selected Plan : " + TextUtils.join(",", name));
        Timber.d("Selected Plan Ids: " + TextUtils.join(",", value));

        prefsUtil.setCurrentPlan(name.get(0));
        prefsUtil.setCurrentPlanId(value.get(0));
        view.setPlan(name.get(0));
        changedCurrentSelection = true;
        unlockDrawerLayout();

    }

    public void onDrawerClosed() {
        drawerActivity.onDrawerClosed();
    }

    private void unlockDrawerLayout() {
        if (isPlanAndOperationalAreaSelected()) {
            view.unlockNavigationDrawer();
        }

    }

    @Override
    public boolean isChangedCurrentSelection() {
        return changedCurrentSelection;
    }

    @Override
    public void setChangedCurrentSelection(boolean changedCurrentSelection) {
        this.changedCurrentSelection = changedCurrentSelection;
    }

    @Override
    public BaseDrawerContract.View getView() {
        return view;
    }

    @Override
    public void onViewResumed() {
        if (viewInitialized) {
            if (!prefsUtil.getCurrentPlan().equals(view.getPlan())
                    || !prefsUtil.getCurrentOperationalArea().equals(view.getOperationalArea())) {
                changedCurrentSelection = true;
                onDrawerClosed();
            }
        } else {
            initializeDrawerLayout();
            viewInitialized = true;
        }
        updateSyncStatusDisplay(revealApplication.getSynced());
    }


    @Override
    public boolean isPlanAndOperationalAreaSelected() {
        String planId = prefsUtil.getCurrentPlanId();
        String operationalArea = prefsUtil.getCurrentOperationalArea();

        return StringUtils.isNotBlank(planId) && StringUtils.isNotBlank(operationalArea);

    }

    @Override
    public void onShowOfflineMaps() {
        getView().openOfflineMapsView();
    }

    private void validateSelectedPlanId(String operationalAreaId) {
        if (!prefsUtil.getCurrentPlanId().isEmpty()) {
            interactor.validateCurrentPlanId(operationalAreaId, prefsUtil.getCurrentPlanId());
        }
    }

    @Override
    public void onPlanValidated(boolean isValid) {
        if (!isValid) {
            prefsUtil.setCurrentPlanId("");
            prefsUtil.setCurrentPlan("");
            view.setPlan("");
            view.lockNavigationDrawerForSelection();
        }
    }

    /**
     * Updates the Hamburger menu of the navigation drawer to display the sync status of the application
     * Updates also the Text view next to the sync button with the sync status of the application
     *
     * @param synced Sync status of the application
     */
    @Override
    public void updateSyncStatusDisplay(boolean synced) {
        Activity activity = view.getContext();
        NavigationView navigationView = activity.findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        syncLabel = headerView.findViewById(R.id.sync_label);
        syncBadge = activity.findViewById(R.id.sync_badge);
        if (syncBadge != null && syncLabel != null) {
            if (synced) {
                syncBadge.setBackground(ContextCompat.getDrawable(activity, R.drawable.badge_green_oval));
                syncLabel.setText("Device data synced");
                syncLabel.setTextColor(ContextCompat.getColor(activity, R.color.alert_complete_green));
                syncLabel.setBackground(ContextCompat.getDrawable(activity, R.drawable.rounded_border_alert_green));
            } else {
                syncBadge.setBackground(ContextCompat.getDrawable(activity, R.drawable.badge_oval));
                syncLabel.setText("Device data not synced");
                syncLabel.setTextColor(ContextCompat.getColor(activity, R.color.alert_urgent_red));
                syncLabel.setBackground(ContextCompat.getDrawable(activity, R.drawable.rounded_border_alert_red));
            }
        }
    }

    @Override
    public void onPlanAutoSelected(Set<PlanDefinition> planDefinitions) {
        if(planDefinitions.size() > 0) {
            PlanDefinition definition = planDefinitions.iterator().next();

            prefsUtil.setCurrentPlan(definition.getName());
            prefsUtil.setCurrentPlanId(definition.getIdentifier());
            unlockDrawerLayout();
        }else{
            view.showPlanSelector(null, null);
        }
    }

}
