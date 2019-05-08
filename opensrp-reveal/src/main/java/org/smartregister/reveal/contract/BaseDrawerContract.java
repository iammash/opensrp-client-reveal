package org.smartregister.reveal.contract;

import android.app.Activity;
import android.support.annotation.StringRes;
import android.support.v4.util.Pair;

import org.smartregister.domain.Campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by samuelgithengi on 3/21/19.
 */
public interface BaseDrawerContract {

    interface DrawerActivity {
        void onDrawerClosed();

        Activity getActivity();
    }

    interface View {

        Activity getContext();

        void initializeDrawerLayout();

        void setCampaign(String campaign);

        void setOperationalArea(String operationalArea);

        String getCampaign();

        String getOperationalArea();

        void setDistrict(String district);

        void setFacility(String facility, String facilityLevel);

        void setOperator();

        void unlockNavigationDrawer();

        void lockNavigationDrawerForSelection();

        void showOperationalAreaSelector(Pair<String, ArrayList<String>> locationHierarchy);

        void showCampaignSelector(List<String> campaigns, String entireTreeString);

        void displayNotification(int title, @StringRes int message, Object... formatArgs);

        void openDrawerLayout();

        Presenter getPresenter();

        void onResume();
    }

    interface Presenter {

        void onDrawerClosed();

        void onShowOperationalAreaSelector();

        void onOperationalAreaSelectorClicked(ArrayList<String> name);

        void onShowCampaignSelector();

        void onCampaignSelectorClicked(ArrayList<String> value, ArrayList<String> name);

        void onCampaignsFetched(List<Campaign> campaigns);

        boolean isChangedCurrentSelection();

        void setChangedCurrentSelection(boolean changedCurrentSelection);

        View getView();

        void onViewResumed();
    }

    interface Interactor {

        void fetchCampaigns();
    }
}
