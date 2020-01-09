package org.smartregister.reveal.contract;

import org.smartregister.domain.Location;
import org.smartregister.reveal.model.OfflineMapModel;

import java.util.List;

public interface AvailableOfflineMapsContract extends OfflineMapsFragmentContract {

    interface Presenter {

        void onDownloadAreaSelected(OfflineMapModel offlineMapModel);

        void fetchOperationalAreas();

        void onFetchOperationalAreas(List<Location> locations);

        void onDownloadStarted(String operationalAreaname);

        void onDownloadComplete(String operationalAreaName);

    }

    interface View {

        void setOfflineMapModelList(List<OfflineMapModel> offlineMapModelList);

        void updateOperationalAreasToDownload(Location operationalAreasToDownload);

        void disableCheckBox(String operationalAreaName);

        void moveDownloadedOAToDownloadedList(String operationalAreaName);
    }

    interface Interactor {

        void fetchOperationalAreas();
    }
}
