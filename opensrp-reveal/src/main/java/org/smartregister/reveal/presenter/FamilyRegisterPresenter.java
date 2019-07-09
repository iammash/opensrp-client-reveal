package org.smartregister.reveal.presenter;


import org.smartregister.clientandeventmodel.Client;
import org.smartregister.family.domain.FamilyEventClient;
import org.smartregister.family.presenter.BaseFamilyRegisterPresenter;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.FamilyRegisterContract;
import org.smartregister.reveal.interactor.RevealFamilyRegisterInteractor;
import org.smartregister.reveal.model.FamilyRegisterModel;
import org.smartregister.reveal.util.FamilyConstants;

/**
 * Created by samuelgithengi on 4/14/19.
 */
public class FamilyRegisterPresenter extends BaseFamilyRegisterPresenter implements FamilyRegisterContract.Presenter {

    private FamilyRegisterContract.View view;

    public FamilyRegisterPresenter(FamilyRegisterContract.View view, FamilyRegisterContract.Model model) {
        super(view, model);
        this.view = view;
        setInteractor(new RevealFamilyRegisterInteractor(this));
    }


    @Override
    public void onRegistrationSaved(boolean isEdit) {
        if (isEdit) {
            onTasksGenerated();
        } else {
            getInteractor().generateTasks(getModel().getEventClientList(), getModel().getStructureId(), view.getContext());
        }
    }

    @Override
    public void onTasksGenerated() {
        view.hideProgressDialog();
        openProfileActivity();
        RevealApplication.getInstance().setRefreshMapAfterFeatureSelect(true);
    }

    private void openProfileActivity() {
        for (FamilyEventClient eventClient : getModel().getEventClientList()) {
            if (eventClient.getClient().getLastName().equals("Family")) {
                Client family = eventClient.getClient();
                view.startProfileActivity(family.getBaseEntityId(),
                        family.findRelatives(FamilyConstants.RELATIONSHIP.FAMILY_HEAD).get(0),
                        family.findRelatives(FamilyConstants.RELATIONSHIP.PRIMARY_CAREGIVER).get(0),
                        family.getFirstName());
                break;
            }
        }
    }

    private FamilyRegisterModel getModel() {
        return (FamilyRegisterModel) model;
    }

    private FamilyRegisterContract.Interactor getInteractor() {
        return (FamilyRegisterContract.Interactor) interactor;
    }
}
