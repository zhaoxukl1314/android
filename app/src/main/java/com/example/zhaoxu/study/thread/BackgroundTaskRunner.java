
public class BackgroundTaskRunner {
    private static final String TAG = BackgroundTaskRunner.class.getSimpleName();

    private final ExecutorService mExecutor;
    private final List<BackgroundTaskContainer> mTaskList;

    private final Object mLock = new Object();

    public interface BackgroundTask extends Runnable {
        void onCanceled();
    }

    private class BackgroundTaskContainer implements Runnable {
        private final BackgroundTask mTask;
        private boolean mIsCanceled;

        BackgroundTaskContainer(BackgroundTask task) {
            mTask = task;
            mIsCanceled = false;
        }

        public void cancel() {
            mIsCanceled = true;
        }

        @Override
        public void run() {
            boolean canceled = false;
            synchronized (mLock) {
                canceled = mIsCanceled;
                if (!mTaskList.remove(this)) {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG, "This task is not registered.");
                }
            }

            if (canceled) {
                mTask.onCanceled();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "This task is canceled. tasks:"
                        + mTaskList.size());
            } else {
                mTask.run();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "This task is completed. tasks:"
                        + mTaskList.size());
            }
        }
    }

    public BackgroundTaskRunner(ExecutorService executor) {
        mExecutor = executor;
        mTaskList = new ArrayList<BackgroundTaskContainer>();
    }

    public void put(BackgroundTask task) {
        synchronized (mLock) {
            BackgroundTaskContainer taskContainer = new BackgroundTaskContainer(task);
            mTaskList.add(taskContainer);
            mExecutor.execute(taskContainer);
        }
    }

    public int getTaskCount() {
        synchronized (mLock) {
            return mTaskList.size();
        }
    }

    public void cancelAll() {
        synchronized (mLock) {
            for (BackgroundTaskContainer i : mTaskList) {
                i.cancel();
            }
        }
    }
}