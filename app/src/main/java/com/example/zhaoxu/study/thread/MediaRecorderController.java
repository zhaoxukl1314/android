
class MediaRecorderController implements RecorderInterface {
    public static final String TAG = "MediaRecorderController";

    private static final int STOP_TIMEOUT_IN_MILLI = 10000;
    private static final int MIN_DURATION_FOR_PAUSE_IN_MILLI = 3000;
    private static final int MIN_DURATION_FOR_INTELLIGENT_ACTIVE_IN_MILLI = 3000;
    private static final int MIN_DURATION_FOR_NOT_INTELLIGENT_ACTIVE_IN_MILLI = 1000;
    private static final int TIME_OF_START_SOUND_TO_COMPLETE_IN_MILLI = 500;

    private static final int MEDIA_RECORDER_INFO_MASK = 0x0FFFFFFF;
    private static final int MEDIA_RECORDER_INFO_KIND_MASK = 0x0000000F;
    private static final int MEDIA_RECORDER_INFO_KIND_SHIFT = 28;
    private static final int MEDIA_RECORDER_INFO_KIND_VIDEO = 1;
    private static final int MEDIA_RECORDER_INFO_KIND_VIDEO_AUDIO = 2;

    private final Context mContext;
    private final RecorderListener mListener;

    private MediaPlayer mMediaPlayer;
    private MediaRecorder mMediaRecorder;
    private BypassCamera mBypassCamera = null;
    private StartVideoRecordingCallbackImpl mStartVideoRecordingCallback;
    private StopVideoRecordingCallbackImpl mStopVideoRecordingCallback;
    private PrepareVideoRecordingCallbackImpl mPrepareVideoRecordingCallback;

    private final Handler mCallbackHandler;
    private final ReferenceClock mRecordingTime;

    private final ExecutorService mTaskExecutor;
    private Future<Boolean> mStopTask;
    private Future<Boolean> mPauseTask;

    // This lock object guards mState.
    private State mState;
    private boolean mIsWaitingStopFinished;
    private boolean mIsWaitingStopAborted;

    private final Object mStateLock = new Object();

    // This lock object guards MediaPlayer play and release.
    private final Object mMediaPlayerLock = new Object();

    // if true, intelligent active is enable.
    private boolean mIsIntelligentActive = false;

    // if true, shutter sound is enable.
    private boolean mIsShutterSoundOn = false;

    /**
     * if true, intelligent active is enabled.
     * intelligent active is enabled, recording stop sound is
     * ring by {@link OnInfoListener#onInfo(MediaRecorder, int, int)}.
     */
    private boolean mIsManualRecordingSoundNeeded = false;

    // ----------------------------------------------------
    // Hide API of MediaRecorder
    // These values must follow to MediaRecorder.
    // ----------------------------------------------------
    /**
     * NOTE:Quote from {@link android.media.MediaRecorder#mInfoReadyForStopSound}.
     * Signal that the notification sound at recording stop can be played.
     */
    private final Integer mInfoReadyForStopSound;
    private static final String MEDIA_RECORDER_INFO_READY_FOR_STOP_SOUND =
            "MEDIA_RECORDER_INFO_READY_FOR_STOP_SOUND";
    /**
     * NOTE:Quote from {@link android.media.MediaRecorder#mInfoDuration}.
     * Provide the duration information.
     */
    private final Integer mInfoDuration;
    private static final String MEDIA_RECORDER_INFO_DURATION_MS =
            "MEDIA_RECORDER_INFO_DURATION_MS";

    /**
     * Vanilla API {@link MediaRecorder#pause()} is supported in Android L.
     * This is still hide API.
     *
     * {@link mMethodPause} adapt to this hide API of MediaRecorder
     * using reflection. And {@link MediaRecorderController#isPauseAndResumeSupported()}
     * returns false unless the platform has this API.
     *
     * It is implemented to work in the platform which doesn't support
     * {@link MediaRecorder#pause()}.
     */
    private final Method mMethodPause;

    /**
     * SOMC add a method, {@link MediaRecorder#setExtendedInfoNotifications()} as a patch.
     *
     * It is implemented to work in the platform which doesn't support
     * {@link MediaRecorder#setExtendedInfoNotifications()}.
     */
    private final Method mMethodSetExtendedInfoNotifications;

    private CountDownLatch mCanStopRec = null;
    private boolean mIsAlreadyCheckCanStopRec = false;
    private boolean mIsMicrophoneEnabled = false;
    // This is true after mMediaRecorder.start().
    private boolean mIsRecorderStarted = false;

    public MediaRecorderController(
            Context context,
            RecorderListener listener,
            Handler callbackHandler,
            int progressNotificationIntervalMillis,
            boolean isIntelligentActive,
            boolean isShutterSoundOn) {
        mContext = context;
        mListener = listener;
        mCallbackHandler = callbackHandler;
        mIsIntelligentActive = isIntelligentActive;
        mIsShutterSoundOn = isShutterSoundOn;
        mState = State.IDLE;
        mRecordingTime = new ReferenceClock(
                mCallbackHandler,
                mOnTickCallback,
                progressNotificationIntervalMillis);
        mTaskExecutor = Executors.newSingleThreadExecutor();
        mIsWaitingStopFinished = false;

        mMethodPause = findMethod("pause");
        mMethodSetExtendedInfoNotifications = findMethod("setExtendedInfoNotifications",
                boolean.class);
        mInfoReadyForStopSound = getStaticValueByReflect(MEDIA_RECORDER_INFO_READY_FOR_STOP_SOUND);
        mInfoDuration = getStaticValueByReflect(MEDIA_RECORDER_INFO_DURATION_MS);

        mIsManualRecordingSoundNeeded = mIsIntelligentActive && isShutterSoundOn;
    }

    private enum State {
        RECORDING,
        PAUSED,
        PREPARED,
        STOPPING,
        IDLE
    }

    @Override
    public boolean isPaused() {
        synchronized (mStateLock) {
            return (mState == State.PAUSED);
        }
    }

    @Override
    public boolean isRecordingOrPaused() {
        synchronized (mStateLock) {
            return (mState == State.RECORDING || mState == State.PAUSED);
        }
    }

    @Override
    public boolean isRecording() {
        synchronized (mStateLock) {
            return (mState == State.RECORDING);
        }
    }

    @Override
    public boolean isStopping() {
        synchronized (mStateLock) {
            return (mState == State.STOPPING);
        }
    }

    @Override
    public boolean canTakeSnapshot() {
        return isRecordingOrPaused() && mIsRecorderStarted;
    }

    @Override
    public long getRecordingTimeMillis() {
        return mRecordingTime.elapsedTimeMillis();
    }

    @Override
    public boolean isPauseAndResumeSupported() {
        return (mMethodPause != null);
    }

    @Override
    public boolean prepare(Camera camera, RecorderParameters parameters) {
        return prepare(camera, null, parameters);
    }

    private boolean isBypassCameraUsed() {
        return mBypassCamera != null;
    }

    @Override
    public boolean prepare(Camera camera, BypassCamera bypassCamera, RecorderParameters parameters) {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "prepare() E");
        mBypassCamera = bypassCamera;

        parameters.dump();

        synchronized (mStateLock) {
            if (mState != State.IDLE) {
                return false;
            }

            if (mIsManualRecordingSoundNeeded || isBypassCameraUsed()) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaPlayer create start:");
                synchronized (mMediaPlayerLock) {
                    mMediaPlayer = new MediaPlayer();
                }
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaPlayer created:" + mMediaPlayer);
            }

            mMediaRecorder = new MediaRecorder();
            if (!prepareReceiveRecordingInfo(mMediaRecorder)) {
                release();
                return false;
            }

            if (!isBypassCameraUsed()) {
                mMediaRecorder.setCamera(camera);
            }

            if (!setupParameters(mContext, mMediaRecorder, parameters)) {
                release();
                return false;
            }

            mMediaRecorder.setOnErrorListener(mOnErrorListener);
            mMediaRecorder.setOnInfoListener(mOnInfoListener);

            try {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#prepare() E");
                mMediaRecorder.prepare();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#prepare() X");

                if (isBypassCameraUsed()) {
                    if (prepareBypassCamera(camera, parameters) != true) {
                        release();
                        return false;
                    }
                }

            } catch (IllegalStateException e) {
                CameraLogger.e(TAG, "prepare() is failed:" + e.getMessage());
                release();
                return false;

            } catch (IOException e) {
                CameraLogger.e(TAG, "prepare() is failed:" + e.getMessage());
                release();
                return false;
            }

            mState = State.PREPARED;
        }

        // Enable SOMC extended info notifications.
        // - MEDIA_RECORDER_INFO_READY_FOR_STOP_SOUND
        // - MEDIA_RECORDER_INFO_DURATION_MS
        setExtendedInfoNotifications(true);

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "prepare() X");
        return true;
    }

    @Override
    public void start() throws RecorderException {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "start() E");

        synchronized (mStateLock) {
            if (mState != State.PREPARED) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "start() X State is illegal.");
                return;
            }

            mIsWaitingStopFinished = false;
            mState = State.RECORDING;
            mIsAlreadyCheckCanStopRec = false;
            mCanStopRec = new CountDownLatch(1);

            if (isBypassCameraUsed()) {
                mTaskExecutor.submit(new StartTask());
            } else {
                // TODO:This is a work around.
                // If using the android standard of camera api,(e.g. Time shift video)
                // error occurs when set the camera parameters on the main thread
                // during start recording on the worker thread.
                // (Because to operate the same camera instance at the same time)
                try {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() E");
                    if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                            "MediaRecorder#start() E");
                    mMediaRecorder.start();
                    mIsRecorderStarted = true;
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() X");
                    if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                            "MediaRecorder#start() X");
                    mRecordingTime.start();

                } catch (IllegalStateException e) {
                    CameraLogger.d(TAG, "start() is failed:" + e.getMessage());
                    mIsManualRecordingSoundNeeded = false;
                    synchronized (mStateLock) {
                        mState = State.IDLE;
                    }
                    throw new RecorderException(e);
                }
            }
        }
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "start() X");
    }

    private class StartTask implements Runnable {

        @Override
        public void run() {
            try {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "startTask() E");
                // Play recording sound if needed.
                playRecordingSoundIfNeeded();

                // TODO:This is a work around.
                // Will have to sleep for a certain period of time,
                // so that the starting sound will not be saved into
                // the video file that has been recorded.
                Thread.sleep(TIME_OF_START_SOUND_TO_COMPLETE_IN_MILLI);

                if (!startBypassCamera()) {
                    CameraLogger.e(TAG, "start() X startBypassCamera failed.");
                    notifyFinishResult(Result.FAIL);
                }

                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() E");
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#start() E");
                mMediaRecorder.start();
                mIsRecorderStarted = true;
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() X");
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#start() X");
                mRecordingTime.start();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "startTask() : success X");

            } catch (IllegalStateException | InterruptedException e) {
                CameraLogger.d(TAG, "startTask() is failed:" + e.getMessage());
                mIsManualRecordingSoundNeeded = false;
                synchronized (mStateLock) {
                    mState = State.IDLE;
                }
                notifyFinishResult(Result.FAIL);
            }
        }
    }

    @Override
    public void stop() throws RecorderException {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stop() E");

        synchronized (mStateLock) {
            if (mState != State.RECORDING && mState != State.PAUSED) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stop() X State is illegal.");
                return;
            }
            mState = State.STOPPING;
            mStopTask = mTaskExecutor.submit(new StopTask());
        }

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stop() X");
    }

    private class StopTask implements Callable<Boolean> {

        @Override
        public Boolean call() throws Exception {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stopTask() E");
            boolean success = false;
            try {

                if (mCanStopRec.await(STOP_TIMEOUT_IN_MILLI, TimeUnit.MILLISECONDS) != true) {
                    CameraLogger.e(TAG, "MediaRecorder StopTask await error");
                }

                if (!isBypassCameraUsed() || mIsWaitingStopAborted) {
                    long duration = mRecordingTime.elapsedTimeMillis();
                    long waitTime = MIN_DURATION_FOR_NOT_INTELLIGENT_ACTIVE_IN_MILLI - duration;
                    // if intelligent is active, blocking time for stop should be round 3 sec.
                    // otherwise, blocking time for stop should be round 1 sec.
                    if (mIsIntelligentActive) {
                        waitTime = MIN_DURATION_FOR_INTELLIGENT_ACTIVE_IN_MILLI - duration;
                    }
                    if (waitTime > 0) {
                        Thread.sleep(waitTime, 0);
                    }
                    mIsWaitingStopAborted = false;
                }

                if (mPauseTask != null) {
                    mPauseTask.cancel(true);
                    mPauseTask = null;
                }

                mRecordingTime.stop();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "mMediaRecorder.stop()...");
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#stop() E");
                mMediaRecorder.stop();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "mMediaRecorder.stop() FINISHED");
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#stop() X");

                if (isBypassCameraUsed()) {
                    stopBypassCamera();
                }

                success = true;
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stopTask() : success X");

            } catch (RuntimeException e) {
                // MediaRecorder.stop can throw RuntimeException.
                // The exception is thrown when MediaRecorder.stop() is called as soon as possible
                // after MediaRecorder.start(). Users can cause it easily by double tap of
                // Record button. First tap means MediaRecorder.start(), and second one means
                // MediaRecorder.stop(). It is hard to avoid, so this catch() is added.
                CameraLogger.e(TAG, "stop() is failed:" + e.getMessage());
            } finally {
                releaseMediaRecorder();
                synchronized (mStateLock) {
                    mStopTask = null;
                    notifyFinishResult(success? Result.SUCCESS: Result.FAIL);
                }
                // Play recording sound if needed.
                playRecordingSoundIfNeeded();
                releaseMediaPlayer();
                releaseBypassCamera();
            }
            return success;
        }
    }

    @Override
    public void pause() throws RecorderException {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pause() E");

        synchronized (mStateLock) {
            if (mState != State.RECORDING) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pause() X State is illegal.");
                return;
            }

            if (mPauseTask == null) {
                mPauseTask = mTaskExecutor.submit(new PauseTask());
            }
        }

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pause() X");
    }


    private class PauseTask implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pauseTask E");
            boolean success = false;

            try {
                long duration = mRecordingTime.elapsedTimeMillis();
                long waitTime;
                if (isBypassCameraUsed()) {
                    waitTime = MIN_DURATION_FOR_PAUSE_IN_MILLI - duration;
                } else {
                    waitTime = MIN_DURATION_FOR_NOT_INTELLIGENT_ACTIVE_IN_MILLI - duration;
                    // if intelligent is active, blocking time for pause should be round 3 sec.
                    // otherwise, blocking time for pause should be round 1 sec.
                    if (mIsIntelligentActive) {
                        waitTime = MIN_DURATION_FOR_INTELLIGENT_ACTIVE_IN_MILLI - duration;
                    }
                }

                if (waitTime > 0) {
                    Thread.sleep(waitTime, 0);
                }

                mRecordingTime.stop();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#pause() E");
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#pause() E");
                mMethodPause.invoke(mMediaRecorder);
                if (CameraLogger.isTimeDebug) CameraLogger.p(TAG,
                        "MediaRecorder#pause() X");

                success = true;
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#pause() X");
            } catch (IllegalArgumentException e) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                        "pause() X an exception occurs. " + e.getMessage());
                throw new RecorderException(e);
            } catch (IllegalAccessException e) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                        "pause() X an exception occurs. " + e.getMessage());
                throw new RecorderException(e);
            } catch (InvocationTargetException e) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                        "pause() X an exception occurs. " + e.getMessage());
                throw new RecorderException(e);
            } catch (RuntimeException e) {
                // MediaRecorder.pause can throw RuntimeException.
                // The exception is thrown when MediaRecorder.pause() is called as soon as possible
                // after MediaRecorder.start(). Users can cause it easily by double tap of
                // Record button. First tap means MediaRecorder.start(), and second one means
                // MediaRecorder.pause(). It is hard to avoid, so this catch() is added.
                if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                        "pause() X an exception occurs. " + e.getMessage());
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pauseTask() : failed X");
            } finally {
                synchronized (mStateLock) {
                    mState = State.PAUSED;
                    mPauseTask = null;
                    notifyPauseResult(success ? Result.SUCCESS : Result.FAIL);
                }
            }
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "pauseTask X");
            return success;
        }
    }

    @Override
    public void resume() throws RecorderException {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "resume() E");
        synchronized (mStateLock) {
            if (mState != State.PAUSED) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "resume() X State is illegal.");
                return;
            }
            mState = State.RECORDING;
        }

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() E");
        mMediaRecorder.start();
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MediaRecorder#start() X");
        mRecordingTime.resume();

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "resume() X");
    }

    @Override
    public boolean awaitFinish(boolean stopImmediately) {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "awaitFinish() E");

        Future<Boolean> stopTask = null;
        synchronized (mStateLock) {
            if (mState == State.RECORDING || mState == State.PAUSED) {
                try {
                    stop();
                } catch (RecorderException e) {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                            "stop() is failed. " + e.getMessage());
                    return false;
                }
            }

            stopTask = mStopTask;
            if (stopTask == null) {
                // Play recording sound if needed.
                playRecordingSoundIfNeeded();
                return true;
            }
            mIsWaitingStopFinished = true;
        }

        if (stopImmediately) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                    "awaitFinish is called with force stop. ");
            stopImmediately();
        }

        try {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "StopTask waiting...");
            boolean success = stopTask.get();
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "StopTask waiting FINISHED");
            return success;

        } catch (InterruptedException e) {
            return false;
        } catch (ExecutionException e) {
            return false;
        } finally {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "awaitFinish() X");
        }
    }

    private boolean setupParameters(
            Context context,
            MediaRecorder recorder,
            RecorderParameters parameters) {
        if (parameters.isMicrophoneEnabled()) {
            mIsMicrophoneEnabled = true;
            if (isBypassCameraUsed()) {
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            } else {
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            // Note: before invoke MediaRecorder.setProfile(), the following two
            //       methods MUST be invoked both.
            //       1) MediaRecorder.setAudioSource()
            //       2) MediaRecorder.setVideoSource()
            recorder.setProfile(parameters.profile());

        } else {
            mIsMicrophoneEnabled = false;
            if (isBypassCameraUsed()) {
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            } else {
                recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            }
            // In this case, AudioSource is not set. So set each parameters instead of setProfile.
            recorder.setOutputFormat(parameters.profile().fileFormat);
            recorder.setVideoFrameRate(parameters.profile().videoFrameRate);
            recorder.setVideoSize(
                    parameters.profile().videoFrameWidth,
                    parameters.profile().videoFrameHeight);
            recorder.setVideoEncodingBitRate(parameters.profile().videoBitRate);
            recorder.setVideoEncoder(parameters.profile().videoCodec);
        }

        // If you pass a 0 or negative or a small integer to this function. a RuntimeException
        // error will occur. But all the integer should be valid values here. Please refer to
        // this function's java doc.
        if (parameters.hasMaxDuration()) {
            try {
                recorder.setMaxDuration(parameters.maxDuration());
            } catch (RuntimeException e) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, e.getMessage());
            }
        }
        if (parameters.hasMaxFileSize()) {
            try {
                recorder.setMaxFileSize(parameters.maxFileSize());
            } catch (RuntimeException e) {
                if (CameraLogger.DEBUG) CameraLogger.e(TAG, e.getMessage());
            }
        }

        if (parameters.hasLocation()) {
            recorder.setLocation(
                    (float) parameters.location().getLatitude(),
                    (float) parameters.location().getLongitude());
        }

        if (parameters.hasOrientationHint()) {
            recorder.setOrientationHint(parameters.orientationHint());
        }

        if (setMediaRecorderOutput(mContext, recorder, parameters.outputUri())) {
            return true;
        } else {
            return false;
        }
    }

    private void release() {
        releaseMediaRecorder();
        releaseMediaPlayer();
        releaseBypassCamera();
    }

    private void releaseMediaRecorder() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseMediaRecorder() E : "
                + mMediaRecorder);
        if (mMediaRecorder != null) {
            setExtendedInfoNotifications(false);
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseMediaRecorder() X");
    }

    private void releaseMediaPlayer() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseMediaPlayer() E : "
                + mMediaPlayer);
        synchronized (mMediaPlayerLock) {
            if (mMediaPlayer != null) {
                mTaskExecutor.execute(new ReleaseMediaPlayerTask(mMediaPlayer));
                mMediaPlayer = null;
            }
        }
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseMediaPlayer() X");
    }

    private void releaseBypassCamera() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseBypassCamera() E : " + mBypassCamera);
        // BypassCamera life-cycle is same as vanilla camera.
        // So Only remove reference of BypassCamera in this class.
        mBypassCamera = null;
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "releaseBypassCamera() X");
    }

    private void setExtendedInfoNotifications(boolean enable) {
        if (mMethodSetExtendedInfoNotifications != null) {
            try {
                mMethodSetExtendedInfoNotifications.invoke(mMediaRecorder, enable);
            } catch (IllegalAccessException e) {
                CameraLogger.e(TAG, "Extended info notifications is not supported.");
            } catch (IllegalArgumentException e) {
                CameraLogger.e(TAG, "Extended info notifications is not supported.");
            } catch (InvocationTargetException e) {
                CameraLogger.e(TAG, "Extended info notifications is not supported.");
            }
        }
    }

    /**
     * Return false if the specified uri is invalid.
     */
    private static boolean setMediaRecorderOutput(
            Context context, MediaRecorder recorder, Uri uri) {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "setOutputFile(" + uri + ")");

        if (uri.getScheme().equals("content")) {
            ParcelFileDescriptor pfd = null;
            try {
                pfd = context.getContentResolver().openFileDescriptor(uri, "rw");
            } catch (FileNotFoundException e) {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "openFileDescriptor failed.", e);
                return false;
            }
            if (pfd != null) {
                recorder.setOutputFile(pfd.getFileDescriptor());
            } else {
                return false;
            }

        } else if (uri.getScheme().equals("file")) {
            recorder.setOutputFile(uri.getPath());
        }

        return true;
    }

    private final OnErrorListener mOnErrorListener = new MyOnErrorListener();
    private class MyOnErrorListener implements OnErrorListener {

        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onError(" + nameError(what) + ") E");
            final int postWhat = what;
            final int postExtra = extra;

            mCallbackHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onRecordError() E");
                    if (mIsWaitingStopFinished) {
                        return;
                    }
                    // Play recording sound if needed.
                    playRecordingSoundIfNeeded();
                    mListener.onRecordError(postWhat, postExtra);
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onRecordError() X");
                }
            });
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onError() X");
        }
    };

    private final OnInfoListener mOnInfoListener = new MyOnInfoListener();
    private class MyOnInfoListener implements OnInfoListener {

        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onInfo("
                    + nameInfo(what) + " extra:" + extra + ") E");

            int mediaRecorderInfo = (what & MEDIA_RECORDER_INFO_MASK);
            int mediaRecorderInfoKind = ((what >> MEDIA_RECORDER_INFO_KIND_SHIFT) &
                                            MEDIA_RECORDER_INFO_KIND_MASK);

            if (mIsAlreadyCheckCanStopRec == false) {
                if (mediaRecorderInfo == MediaRecorder.MEDIA_RECORDER_TRACK_INFO_PROGRESS_IN_TIME) {
                    if ((mIsMicrophoneEnabled == true) &&
                        (mediaRecorderInfoKind == MEDIA_RECORDER_INFO_KIND_VIDEO_AUDIO)) {
                        mCanStopRec.countDown();
                        mIsAlreadyCheckCanStopRec = true;
                    }
                    else if ((mIsMicrophoneEnabled == false) &&
                        (mediaRecorderInfoKind == MEDIA_RECORDER_INFO_KIND_VIDEO)) {
                        mCanStopRec.countDown();
                        mIsAlreadyCheckCanStopRec = true;
                    }
                }
            }

            switch (what) {
                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    if (mCanStopRec != null) {
                        mCanStopRec.countDown();
                        mIsAlreadyCheckCanStopRec = true;
                    }
                    notifyFinishResult(Result.MAX_DURATION_REACHED);
                    break;

                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                    if (mCanStopRec != null) {
                        mCanStopRec.countDown();
                        mIsAlreadyCheckCanStopRec = true;
                    }
                    notifyFinishResult(Result.MAX_FILESIZE_REACHED);
                    break;

                default:
                    if (mInfoReadyForStopSound != null && mInfoReadyForStopSound == what) {
                        notifyReadyForSound();

                    } else if (mInfoDuration != null && mInfoDuration == what) {
                        notifyDuration((int) extra);
                    }
                    break;
            }
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onInfo() X");
        }
    };

    private void playRecordingSoundIfNeeded() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "playRecordingSoundIfNeeded() E");
        synchronized (mMediaPlayerLock) {
            if ((mMediaPlayer != null) &&
                    (mIsManualRecordingSoundNeeded ||
                            (isBypassCameraUsed() && mIsShutterSoundOn))) {
                playRecordingSound();
                mIsManualRecordingSoundNeeded = false;
            }
        }
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "playRecordingSoundIfNeeded() X");
    }

    private void notifyFinishResult(final Result result) {
        mCallbackHandler.post(new Runnable() {

            @Override
            public void run() {
                if (CameraLogger.DEBUG)
                    CameraLogger.d(TAG, "onRecordFinished() : " + result.name() + " E");
                if (!mIsWaitingStopFinished) {
                    mListener.onRecordFinished(result);
                }

                if (result == Result.SUCCESS || result == Result.FAIL) {
                    synchronized (mStateLock) {
                        mState = State.IDLE;
                    }
                }

                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onRecordFinished() X");
            }
        });
    }

    private void notifyPauseResult(final Result result) {
        mCallbackHandler.post(new Runnable() {

            @Override
            public void run() {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onRecordPaused() : "
                        + result.name() + " E");
                mListener.onRecordPaused(result);
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onRecordPaused() X");
            }
        });
    }

    private void notifyReadyForSound() {
        mCallbackHandler.post(new Runnable() {

            @Override
            public void run() {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onReadyForSound() E");
                if (!mIsManualRecordingSoundNeeded && mIsWaitingStopFinished) {
                    return;
                }
                // Play recording sound if needed.
                playRecordingSoundIfNeeded();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onReadyForSound() X");
            }
        });
    }

    private void notifyDuration(int durationMillis) {
        mRecordingTime.reset(durationMillis);
        if (!mRecordingTime.isMeasuring()) {
            // Now ReferenceClock is not running and doesn't notify onTick.
            // So notify a recording time at this.
            mListener.onRecordProgress(durationMillis);
        }
    }

    private final TickCallback mOnTickCallback = new TickCallback() {

        @Override
        public void onTick(long elapsedTimeMillis) {
            if (mIsWaitingStopFinished) {
                return;
            }
            mListener.onRecordProgress(elapsedTimeMillis);
        }
    };

    private static Method findMethod(String name, Class<?>... argumentTypes) {
        try {
            return MediaRecorder.class.getMethod(name, argumentTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Integer getStaticValueByReflect(String name) {
        try {
            Field field = MediaRecorder.class.getField(name);
            return field.getInt(null);

        } catch (NoSuchFieldException e) {
            //NOP
        } catch (IllegalAccessException e) {
            //NOP
        } catch (IllegalArgumentException e) {
            //NOP
        }
        return null;
    }

    private String nameInfo(int what) {
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                return "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED";

            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                return "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED";

            default:
                if (mInfoReadyForStopSound != null && mInfoReadyForStopSound == what) {
                    return MEDIA_RECORDER_INFO_READY_FOR_STOP_SOUND;

                } else if (mInfoDuration != null && mInfoDuration == what) {
                    return MEDIA_RECORDER_INFO_DURATION_MS;

                } else {
                    return "unknown:" + what;
                }
        }
    }

    private String nameError(int what) {
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
                return "MEDIA_RECORDER_ERROR_UNKNOWN";

            case MediaRecorder.MEDIA_ERROR_SERVER_DIED:
                return "MEDIA_ERROR_SERVER_DIED";

            default:
                return "unknown:" + what;
        }
    }

    /**
     * Play recording sound.
     */
    private void playRecordingSound() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "Play: ");
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(CommonConstants.VANILLA_VIDEO_RECORD_SOUND_FILE_PATH);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM_ENFORCED);
            mMediaPlayer.setVolume(CommonConstants.MAX_VOLUME, CommonConstants.MAX_VOLUME);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        } catch (IllegalArgumentException e) {
            mMediaPlayer.reset();
            if (CameraLogger.DEBUG) CameraLogger.w(TAG, "Play sound failed.", e);
        } catch (IllegalStateException e) {
            mMediaPlayer.reset();
            if (CameraLogger.DEBUG) CameraLogger.w(TAG, "Play sound failed.", e);
        } catch (IOException e) {
            mMediaPlayer.reset();
            if (CameraLogger.DEBUG) CameraLogger.w(TAG, "Play sound failed.", e);
        }
    }

    private static class ReleaseMediaPlayerTask implements Runnable {
        private final MediaPlayer mPlayer;
        private static final int RING_END_WAIT_MILLISEC = 1000;

        private ReleaseMediaPlayerTask(MediaPlayer player) {
            mPlayer = player;
        }

        @Override
        public void run() {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "ReleaseMediaPlayerTask.run in" + mPlayer);
            try {
                // Wait for sound ring end.
                Thread.sleep(RING_END_WAIT_MILLISEC);
            } catch (InterruptedException e) {
                // NOP
            }
            mPlayer.release();
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "ReleaseMediaPlayerTask.run out");
        }
    }

    /**
     * Vanilla API {@link MediaRecorder#setParameter()} is supported in Android M.
     * This is still hide API.
     *
     * {@link mSetParameter} adapt to this hide API of MediaRecorder
     * using reflection.
     *
     * This API is called in order to receive recording start via onInfo() callback.
     * After receiving that, Camera app can call MediaRecorder.stop()
     * when setting intelligent active. And if not do it, error will occur.
     */
    private boolean prepareReceiveRecordingInfo(MediaRecorder mediaRecorder) {

        // setParameter() is called in order to receive recording start via onInfo() callback.
        try {
            Method setParameter;
            setParameter = MediaRecorder.class.getDeclaredMethod("setParameter", String.class);
            setParameter.setAccessible(true);
            setParameter.invoke(mediaRecorder, "param-track-time-status=1000000"); // ms
        } catch(NoSuchMethodException e) {
            if (CameraLogger.DEBUG) CameraLogger.e(TAG, e.getMessage());
            return false;
        } catch (InvocationTargetException e) {
            if (CameraLogger.DEBUG) CameraLogger.e(TAG, e.getMessage());
            return false;
        } catch(IllegalAccessException e) {
            if (CameraLogger.DEBUG) CameraLogger.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    private boolean prepareBypassCamera(Camera camera, RecorderParameters parameters) {
        // Register bypassCamera instance.
        mPrepareVideoRecordingCallback = new PrepareVideoRecordingCallbackImpl();
        mStartVideoRecordingCallback = new StartVideoRecordingCallbackImpl();
        mStopVideoRecordingCallback = new StopVideoRecordingCallbackImpl();

        mBypassCamera.setVideoCallbacks(
                mPrepareVideoRecordingCallback,
                mStartVideoRecordingCallback,
                mStopVideoRecordingCallback);

        try {
            CountDownLatch latch = mPrepareVideoRecordingCallback.requestLatch();
            mBypassCamera.requestPrepareVideoRecording(mMediaRecorder.getSurface());
            latch.await();

        } catch (RuntimeException | InterruptedException e) {
            CameraLogger.e(TAG, "requestPrepareVideoRecording() is failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean startBypassCamera() {
        try {
            CountDownLatch latch = mStartVideoRecordingCallback.requestLatch();
            mBypassCamera.requestStartVideoRecording();
            latch.await();

        } catch (RuntimeException | InterruptedException e) {
            CameraLogger.e(TAG, "startBypassCamera() is failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void stopBypassCamera() {
        try {
            CountDownLatch latch = mStopVideoRecordingCallback.requestLatch();
            mBypassCamera.requestStopVideoRecording();
            latch.await();

        } catch (InterruptedException e) {
            CameraLogger.e(TAG, "stopBypassCamera() is failed: " + e.getMessage());
            throw new RuntimeException(e);

        } catch (RuntimeException e) {
            CameraLogger.e(TAG, "stopBypassCamera() is failed: " + e.getMessage());
            throw e;
        }
    }

    private static class PrepareVideoRecordingCallbackImpl implements
            BypassCamera.PrepareVideoRecordingCallback {
        private CountDownLatch mLatch;

        public CountDownLatch requestLatch() {
            synchronized (this) {
                if (mLatch != null) {
                    CameraLogger.e(TAG, "requestLock() Lock object already exists.");
                } else {
                    mLatch = new CountDownLatch(1);
                }
                return mLatch;
            }
        }

        @Override
        public void onPrepareVideoRecordingDone() {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onPrepareVideoRecordingDone() E");
            synchronized (this) {
                if (mLatch == null) {
                    return;
                }
                mLatch.countDown();
                mLatch = null;
            }
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onPrepareVideoRecordingDone() X");
        }
    }

    private static class StartVideoRecordingCallbackImpl implements
            BypassCamera.StartVideoRecordingCallback {
        private CountDownLatch mLatch;

        public CountDownLatch requestLatch() {
            synchronized (this) {
                if (mLatch != null) {
                    CameraLogger.e(TAG, "requestLock() Lock object already exists.");
                } else {
                    mLatch = new CountDownLatch(1);
                }
                return mLatch;
            }
        }

        @Override
        public void onStartVideoRecordingDone() {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onStartVideoRecordingDone() E");
            synchronized (this) {
                if (mLatch == null) {
                    return;
                }
                mLatch.countDown();
                mLatch = null;
            }
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onStartVideoRecordingDone() X");
        }
    }

    private static class StopVideoRecordingCallbackImpl implements
            BypassCamera.StopVideoRecordingCallback {
        private CountDownLatch mLatch;

        public CountDownLatch requestLatch() {
            synchronized (this) {
                if (mLatch != null) {
                    CameraLogger.e(TAG, "requestLock() Lock object already exists.");
                } else {
                    mLatch = new CountDownLatch(1);
                }
                return mLatch;
            }
        }

        @Override
        public void onStopVideoRecordingDone() {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onStopVideoRecordingDone() E");
            synchronized (this) {
                if (mLatch == null) {
                    return;
                }
                mLatch.countDown();
                mLatch = null;
            }
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "onStopVideoRecordingDone() X");
        }
    }

    public void stopImmediately() {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stopImmediately() E");
        // Stop waiting by mCanStopRec and sleep instead in StopTask.
        // StopTask waits by sleep if mIsWaitingStopAborted == true;
        mIsWaitingStopAborted = true;
        mCanStopRec.countDown();
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "stopImmediately() X");
    }
}
