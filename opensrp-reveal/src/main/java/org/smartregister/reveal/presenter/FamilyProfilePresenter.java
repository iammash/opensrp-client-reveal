package org.smartregister.reveal.presenter;

import android.content.Context;
import android.content.DialogInterface;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.commonregistry.CommonPersonObject;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.domain.Location;
import org.smartregister.domain.Task;
import org.smartregister.family.domain.FamilyEventClient;
import org.smartregister.family.presenter.BaseFamilyProfilePresenter;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.FamilyOtherMemberProfileContract;
import org.smartregister.reveal.contract.FamilyProfileContract;
import org.smartregister.reveal.dao.StructureDao;
import org.smartregister.reveal.interactor.RevealFamilyOtherMemberInteractor;
import org.smartregister.reveal.interactor.RevealFamilyProfileInteractor;
import org.smartregister.reveal.model.FamilyProfileModel;
import org.smartregister.reveal.util.AlertDialogUtils;
import org.smartregister.reveal.util.AppExecutors;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.Country;
import org.smartregister.reveal.util.FamilyConstants;
import org.smartregister.reveal.util.FamilyConstants.DatabaseKeys;
import org.smartregister.reveal.util.FamilyConstants.JSON_FORM;
import org.smartregister.reveal.util.FamilyJsonFormUtils;
import org.smartregister.reveal.util.NativeFormProcessor;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.reveal.util.TaskUtils;
import org.smartregister.reveal.util.Utils;
import org.smartregister.util.CallableInteractor;
import org.smartregister.util.CallableInteractorCallBack;
import org.smartregister.util.GenericInteractor;

import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.Callable;

import timber.log.Timber;

import static org.smartregister.family.util.Constants.INTENT_KEY.BASE_ENTITY_ID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.STRUCTURE_ID;
import static org.smartregister.reveal.util.FamilyConstants.TABLE_NAME.FAMILY;

/**
 * Created by samuelgithengi on 4/10/19.
 */
public class FamilyProfilePresenter extends BaseFamilyProfilePresenter implements FamilyProfileContract.Presenter, FamilyOtherMemberProfileContract.BasePresenter {
    private AppExecutors appExecutors;
    private SQLiteDatabase database;
    private String structureId;
    private PreferencesUtil preferencesUtil;

    private FamilyJsonFormUtils familyJsonFormUtils;

    private FamilyOtherMemberProfileContract.Interactor otherMemberInteractor;

    private TaskUtils taskUtils = TaskUtils.getInstance();

    private CallableInteractor _interactor;


    public FamilyProfilePresenter(FamilyProfileContract.View view, FamilyProfileContract.Model model, String familyBaseEntityId, String familyHead, String primaryCaregiver, String familyName) {
        super(view, model, familyBaseEntityId, familyHead, primaryCaregiver, familyName);
        appExecutors = RevealApplication.getInstance().getAppExecutors();
        database = RevealApplication.getInstance().getRepository().getReadableDatabase();
        preferencesUtil = PreferencesUtil.getInstance();
        getStructureId(familyBaseEntityId);
        setInteractor(new RevealFamilyProfileInteractor(this));
        try {
            familyJsonFormUtils = new FamilyJsonFormUtils(getView().getApplicationContext());
        } catch (Exception e) {
            Timber.e(e, "error Initializing FamilyJsonFormUtils ");
        }
        otherMemberInteractor = new RevealFamilyOtherMemberInteractor();
    }

    @Override
    public void refreshProfileTopSection(CommonPersonObjectClient client) {
        super.refreshProfileTopSection(client);
        getView().setProfileDetailOne(preferencesUtil.getCurrentOperationalArea());
        getView().setProfileDetailTwo(preferencesUtil.getCurrentDistrict());
    }


    private void getStructureId(String familyId) {
        appExecutors.diskIO().execute(() -> {
            Cursor cursor = null;
            try {
                cursor = database.rawQuery(String.format("SELECT DISTINCT %s FROM %S WHERE %s = ?",
                        STRUCTURE_ID, FAMILY, BASE_ENTITY_ID), new String[]{familyId});
                if (cursor.moveToNext()) {
                    structureId = cursor.getString(0);
                }
            } catch (Exception e) {
                Timber.e(e, "Error getting residence for" + familyId);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
            //if (structureId != null) {
                appExecutors.mainThread().execute(() -> {
                    getModel().setStructureId(structureId);
                    getView().setStructureId(structureId);
                });
            //}
        });
    }

    private FamilyProfileModel getModel() {
        return (FamilyProfileModel) model;
    }

    public String getStructureId() {
        return structureId;
    }

    @Override
    public FamilyProfileContract.View getView() {
        return (FamilyProfileContract.View) super.getView();
    }

    @Override
    public void onRegistrationSaved(boolean editMode, boolean isSaved, FamilyEventClient eventClient) {
        if (!editMode && isSaved && Utils.isFocusInvestigationOrMDA()) {
            getInteractor().generateTasks(getView().getApplicationContext(),
                    eventClient.getEvent().getBaseEntityId(), structureId);
            return;
        } else if (editMode && isSaved) {
            for (Obs obs : eventClient.getEvent().getObs()) {
                if (obs.getFieldCode().equals(DatabaseKeys.OLD_FAMILY_NAME)) {
                    String oldSurname = obs.getValue().toString();
                    if (!eventClient.getClient().getFirstName().equals(oldSurname)) {  //family name was changed
                        getInteractor().updateFamilyMemberName(eventClient.getClient(), eventClient.getEvent(), oldSurname);
                        getView().updateFamilyName(eventClient.getClient().getFirstName());
                        return;
                    }
                }
            }
        }
        super.onRegistrationSaved(editMode, isSaved, eventClient);
    }

    @Override
    public void onTasksGenerated() {
        super.onRegistrationSaved(false, true, null);
        getView().refreshTasks(structureId);

    }

    @Override
    public void onMembersUpdated() {
        onTasksGenerated();
    }

    @Override
    public void onAddFamilyMember() {
        if (getModel().getFamilyHeadPersonObject() == null) {
            otherMemberInteractor.getFamilyHead(this, familyHead);
        } else {
            openAddMemberForm();
        }
    }

    @Override
    public void onArchiveFamilyClicked() {
        AlertDialogUtils.displayNotificationWithCallback(getView().getContext(),
                R.string.confirm_archive_family, R.string.confirm_archive_family_message, R.string.confirm, R.string.cancel, (dialog, buttonClicked) -> {
                    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
                        archiveFamily();
                    }
                    dialog.dismiss();
                });
    }

    @Override
    public void onArchiveFamilyCompleted(boolean isSuccessfulSaved, Task task) {
        getView().hideProgressDialog();
        if (!isSuccessfulSaved) {
            AlertDialogUtils.displayNotification(getView().getContext(), R.string.archive_family,
                    R.string.archive_family_failed, familyName);
        } else {
            RevealApplication.getInstance().setRefreshMapOnEventSaved(true);
            getView().returnToMapView(structureId, task);
        }
    }

    private void archiveFamily() {
        getView().showProgressDialog(org.smartregister.family.R.string.saving_dialog_title);
        getInteractor().archiveFamily(familyBaseEntityId, structureId);
    }

    private FamilyProfileContract.Interactor getInteractor() {
        return (FamilyProfileContract.Interactor) interactor;
    }

    @Override
    public void startFormForEdit(CommonPersonObjectClient client) {
        String formName;
        if (BuildConfig.BUILD_COUNTRY == Country.THAILAND) {
            formName = JSON_FORM.THAILAND_FAMILY_UPDATE;
        } else if (BuildConfig.BUILD_COUNTRY == Country.ZAMBIA) {
            formName = JSON_FORM.ZAMBIA_FAMILY_UPDATE;
        } else if (BuildConfig.BUILD_COUNTRY == Country.REFAPP) {
            formName = JSON_FORM.REFAPP_FAMILY_UPDATE;
        } else {
            formName = JSON_FORM.FAMILY_UPDATE;
        }
        JSONObject form = familyJsonFormUtils.getAutoPopulatedJsonEditFormString(formName,
                client, RevealApplication.getInstance().getMetadata().familyRegister.updateEventType);
        try {
            getView().startFormActivity(form);

        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public void updateFamilyMember(JSONObject jsonObject, String familyEntityId, String familyName){
        CallableInteractor myInteractor = getCallableInteractor();

        Callable<Void> callable = () -> {

            // save the child
            NativeFormProcessor processor = NativeFormProcessor.createInstance(jsonObject);

            // prevent form submission if consent was not granted
            String consentRelationship = processor.getFieldValue("consentRelationship");
            if (consentRelationship.contains("not_eligible"))
                throw new IllegalAccessException("Attempting to save a child with no consent");


            Location operationalArea = processor.getCurrentOperationalArea();
            String entityId = UUID.randomUUID().toString();

            String age = processor.getFieldValue("age");
            String same_as_fam_name = processor.getFieldValue("same_as_fam_name");

            // update metadata
            processor.withBindType(FamilyConstants.TABLE_NAME.FAMILY_MEMBER)
                    .withEncounterType(FamilyConstants.EventType.FAMILY_MEMBER_REGISTRATION)
                    .withEntityId(entityId)
                    .tagLocationData(operationalArea)
                    .tagEventMetadata()

                    // create and save client
                    .hasClient(true)
                    .saveClient(client -> {
                        client.addRelationship("family", familyEntityId);
                        client.setBirthdateApprox(true);

                        if (same_as_fam_name.contains("same_as_fam_name"))
                            client.setLastName(familyName);

                        if (StringUtils.isNotBlank(age)) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.add(Calendar.YEAR, -1 * Integer.parseInt(age));

                            client.setBirthdate(calendar.getTime());
                        }
                    })
                    // create and save event to db
                    .saveEvent()

                    // execute client processing
                    .clientProcessForm();

            return null;
        };

        getView().setLoadingState(true);
        myInteractor.execute(callable, new CallableInteractorCallBack<Void>() {
            @Override
            public void onResult(Void aVoid) {
                FamilyProfileContract.View view = getView();
                if (view != null) {
                    view.setLoadingState(false);
                    view.refreshViews(structureId);
                }
            }

            @Override
            public void onError(Exception ex) {
                FamilyProfileContract.View view = getView();
                if (view == null) return;
                view.onError(ex);
                view.setLoadingState(false);
            }
        });
    }

    public void  saveFamilyMember(Context context, JSONObject jsonObject, String familyEntityId, String familyName) {
        CallableInteractor myInteractor = getCallableInteractor();

        Callable<Void> callable = () -> {

            // save the child
            NativeFormProcessor processor = NativeFormProcessor.createInstance(jsonObject);

            // prevent form submission if consent was not granted
            String consentRelationship = processor.getFieldValue("consentRelationship");
            if (consentRelationship.contains("not_eligible"))
                throw new IllegalAccessException("Attempting to save a child with no consent");

            String structureId = StructureDao.getInstance().getStructureIDFromFamilyID(familyEntityId);

            Location operationalArea = processor.getCurrentOperationalArea();
            String entityId = UUID.randomUUID().toString();

            String age = processor.getFieldValue("age");
            String same_as_fam_name = processor.getFieldValue("same_as_fam_name");

            // update metadata
            processor.withBindType(FamilyConstants.TABLE_NAME.FAMILY_MEMBER)
                    .withEncounterType(FamilyConstants.EventType.FAMILY_MEMBER_REGISTRATION)
                    .withEntityId(entityId)
                    .tagLocationData(operationalArea)
                    .tagEventMetadata()

                    // create and save client
                    .hasClient(true)
                    .saveClient(client -> {
                        client.addRelationship("family", familyEntityId);
                        client.setBirthdateApprox(true);

                        if (same_as_fam_name.contains("same_as_fam_name"))
                            client.setLastName(familyName);

                        if (StringUtils.isNotBlank(age)) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.add(Calendar.YEAR, -1 * Integer.parseInt(age));

                            client.setBirthdate(calendar.getTime());
                        }
                    })
                    // create and save event to db
                    .saveEvent()

                    // execute client processing
                    .clientProcessForm()

                    // close client id
                    .closeRegistrationID(Constants.DatabaseKeys.UNIQUE_ID);


            taskUtils.generateTask(context, entityId, structureId, Constants.BusinessStatus.NOT_VISITED, Constants.Intervention.NTD_MDA_DISPENSE,
                    R.string.mass_drug_administration);

            return null;
        };

        getView().setLoadingState(true);
        myInteractor.execute(callable, new CallableInteractorCallBack<Void>() {
            @Override
            public void onResult(Void aVoid) {
                FamilyProfileContract.View view = getView();
                if (view != null) {
                    view.setLoadingState(false);
                    view.refreshViews(structureId);
                }
            }

            @Override
            public void onError(Exception ex) {
                FamilyProfileContract.View view = getView();
                if (view == null) return;
                view.onError(ex);
                view.setLoadingState(false);
            }
        });
    }


    public CallableInteractor getCallableInteractor() {
        if (_interactor == null)
            _interactor = new GenericInteractor();

        return _interactor;
    }

    @Override
    public void onFetchFamilyHead(CommonPersonObject familyHeadPersonObject) {
        getModel().setFamilyHeadPersonObject(familyHeadPersonObject);
        openAddMemberForm();
    }

    @Override
    public void onArchiveMemberCompleted(boolean isSuccessful) {
        //not used
    }

    private void openAddMemberForm() {
        try {
            startForm(RevealApplication.getInstance().getMetadata().familyMemberRegister.formName, null, null, RevealApplication.getInstance().getContext().allSharedPreferences().getPreference(AllConstants.CURRENT_LOCATION_ID));
        } catch (Exception e) {
            Timber.e(e, "Error opening add member form");
        }
    }
}
