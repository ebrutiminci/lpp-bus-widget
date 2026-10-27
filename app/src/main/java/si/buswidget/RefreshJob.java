package si.buswidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

/** Runs about every 15 minutes; refreshes the widget only during your usual hours. */
public class RefreshJob extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        if (!Config.isSetUp(app) || !BusWidget.hasWidgets(app) || !Schedule.isActiveNow(app)) {
            return false; // nothing to do this time
        }
        new Thread(() -> {
            try {
                BusWidget.refresh(app, 10000);
            } finally {
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
