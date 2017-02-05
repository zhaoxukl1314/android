
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Location;
import android.os.Handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class SoundPhotoContentsIntegrator {
    private static final String TAG = SoundPhotoContentsIntegrator.class.getSimpleName();

    public SoundPhotoContent createContent(
            AudioData audio,
            PreviewFrameData previewFramesData,
            PhotoSavingRequest pictureData) {
        return new SoundPhotoContent(audio, previewFramesData, pictureData);
    }

    private final Context mContext;
    private final Handler mHandler;

    private final BackgroundTaskRunner mMainTaskRunner = new BackgroundTaskRunner(
            Executors.newSingleThreadExecutor());
    // A worker thread to run AudioEncodeTask, and that task is submitted from MainTask.run(),
    // which runs on mMainTaskRunner (meaning not from the UI thread).
    private final ExecutorService mAudioEncodeTaskExecutor = Executors.newSingleThreadExecutor();

    // NOTE: SoundPhotoContent uses a large native heap. And a java heap will be allocated
    // for image data when finishing the process of {@link SoundPhotoContent#store()}.
    // OOM occurs if tapping capture button repeatedly.
    // So sound photo camera must not allow user to capture without limitation.
    private static final int PROCESSING_TASK_NUMBER_LIMIT = 2;

    // NOTE: This value specifies a limit of the number of Yuv2JpegTask which compresses
    // to jpeg data.
    private static final int YUV2JPEG_TASK_NUMBER_LIMIT = 3;

    public SoundPhotoContentsIntegrator(Context context) {
        mContext = context;
        mHandler = new Handler();
    }

    public void cancelAll() {
        mMainTaskRunner.cancelAll();
    }

    public boolean canCreateNewContent() {
        if (CameraLogger.DEBUG) dumpMemoryUsage("REQUEST");
        return (mMainTaskRunner.getTaskCount() < PROCESSING_TASK_NUMBER_LIMIT);
    }

    private static int sDebugIdGen = 0;
    private static final boolean sIsDebugFormat = false;

    private static class EncodedAudioData {
        public final boolean result;
        public final byte[] audioData;
        public final int audioDuration;

        public EncodedAudioData(boolean result, byte[] audioData, int audioDuration) {
            this.result = result;
            this.audioData = audioData;
            this.audioDuration = audioDuration;
        }
    }

    private class AudioEncodeTask implements Callable<EncodedAudioData> {
        public final AudioData audio;

        public AudioEncodeTask(AudioData audioData) {
            audio = audioData;
        }

        @Override
        public EncodedAudioData call() {
            byte[] audioData;
            int audioDuration;
            try {
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "ENCODING AUDIO...");
                audioData = audio.makeMp4();
                audioDuration = (int)audio.duration();
                if (audioData == null) {
                    return new EncodedAudioData(false, null, audioDuration);
                }
                if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                        "ENCODING AUDIO FINISHED" + " duration:" + audioDuration + "ms");
            } finally {
                if (CameraLogger.DEBUG) dumpMemoryUsage("FINISH");
            }
            return new EncodedAudioData(true, audioData, audioDuration);
        }
    }

    /**
     * This class provides the function to operate a content of SoundPhoto.
     * This has the following data.
     * <ol>
     * <li>Sound around shutter
     * <li>Animation just before shutter
     * <li>Taken picture
     * </ol>
     * Each data objects have the function to convert format to store into storage.
     */
    public class SoundPhotoContent {
        public final AudioData audio;
        public final PreviewFrameData previewFrames;
        public final PhotoSavingRequest picture;

        private final int mDebugId;

        private String debugTag() {
            return "CONTENT[" + mDebugId + "] ";
        }

        private void trace(String message) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, debugTag() + message);
        }

        private SoundPhotoContent(
                AudioData audioData,
                PreviewFrameData previewFramesData,
                PhotoSavingRequest pictureData) {
            mDebugId = ++sDebugIdGen;
            this.audio = audioData;
            this.previewFrames = previewFramesData;
            this.picture = pictureData;
            trace("CREATED");
        }

        public void store(SavingTaskManager savingTaskManager) {
            mMainTaskRunner.put(new MainTask(savingTaskManager));
        }

        public void setStoreFinishedListener(StoreDataCallback callback) {
            picture.addCallback(callback);
        }

        private class MainTask implements BackgroundTask {
            private final SavingTaskManager mSavingTaskManager;

            public MainTask(SavingTaskManager savingTaskManager) {
                mSavingTaskManager = savingTaskManager;
                trace("CRETAE MAIN TASK");
            }

            @Override
            public void run() {
                try {
                    Future<EncodedAudioData> audioEncodeResponse = mAudioEncodeTaskExecutor.submit(
                            new AudioEncodeTask(audio));

                    if (picture.common.doPostProcessing) {
                        trace("PROCESS SUPERRESOLUTION...");
                        byte[] processedImageData = processSuperResolution(
                                picture.getImageData(),
                                picture.common.width,
                                picture.common.height);
                        if (processedImageData != null) {
                            picture.setImageData(processedImageData);
                        }
                        trace("PROCESS SUPERRESOLUTION FINISHED");
                    }

                    trace("GENERATING SINGLE PHOTO...");
                    byte[] singlePhoto = makeMpf(
                            picture.getImageData());
                    if (singlePhoto == null) {
                        fail();
                        return;
                    }
                    trace("GENERATING SINGLE PHOTO FINISHED");

                    trace("GENERATING MULTIPLE PHOTO...");
                    byte[] multiplePhoto = makeMpf(
                            previewFrames,
                            picture.getImageData(),
                            picture.getDateTaken(),
                            picture.common.orientation,
                            picture.common.location);
                    if (multiplePhoto == null) {
                        fail();
                        return;
                    }
                    trace("GENERATING MULTIPLE PHOTO FINISHED");

                    trace("WAIT FOR ENCODING AUDIO...");
                    EncodedAudioData encodedAudioData = waitForEncodingAudio(audioEncodeResponse);
                    trace("WAIT FOR ENCODING AUDIO FINISHED");
                    if (encodedAudioData == null || !encodedAudioData.result) {
                        fail();
                        return;
                    }

                    trace("PUT AUDIO DATA INTO JPEG...");
                    byte[] pictureAudioData = makeSpf(
                            singlePhoto,
                            encodedAudioData.audioData,
                            encodedAudioData.audioDuration);
                    trace("PUT AUDIO DATA INTO JPEG FINISHED");

                    trace("PUT AUDIO DATA INTO MPO...");
                    byte[] animationPictureAudioData = makeSpf(
                            multiplePhoto,
                            encodedAudioData.audioData,
                            encodedAudioData.audioDuration);
                    trace("PUT AUDIO DATA INTO MPO FINISHED");

                    picture.setImageData(pictureAudioData);
                    mSavingTaskManager.request(new SoundPhotoSavingTask(
                            mContext,
                            mSavingTaskManager,
                            picture,
                            animationPictureAudioData));

                } finally {
                    release();
                    if (CameraLogger.DEBUG) dumpMemoryUsage("FINISH");
                }
            }

            @Override
            public void onCanceled() {
                trace("CANCELED");
                fail();
                release();
            }

            private void fail() {
                trace("FAILED");
                final PhotoSavingRequest request = picture;
                if (request != null) {
                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            request.notifyStoreFailed(MediaSavingResult.FAIL);
                        }
                    });
                }
            }

            private void release() {
                if (audio != null) {
                    audio.clearSamples();
                }
                if (previewFrames != null) {
                    previewFrames.clear();
                }
            }

            private EncodedAudioData waitForEncodingAudio(Future<EncodedAudioData> response) {
                try {
                    //Synchronize MainTask and MakeAudioTask
                    return response.get();
                } catch (InterruptedException e) {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                            "MakeMusic Fail:InterruptedException occurs:", e);
                    return null;
                } catch (ExecutionException e) {
                    if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                            "MakeMusic Fail:ExecutionException occurs:", e);
                    return null;
                }
            }
        };
    }

    /**
     * This value means length of space which is expanded when inserting a sound data into jpeg
     * image. The space contains metadata for sound data and APP3 segment.
     */
    private static final int EXPANDED_SPACE_LENGTH_FOR_SPF = 5 * 1024;

    /**
     * Add sound data and spf segment into jpeg or mpo data.
     */
    private static byte[] makeSpf(
            byte[] imageData,
            byte[] audioData,
            long audioDuration) {
        NativeByteBufferHolder imageDataBuffer = NativeByteBufferHolder.allocate(
                imageData.length + audioData.length + EXPANDED_SPACE_LENGTH_FOR_SPF);
        if (imageDataBuffer.get() == null) {
            return null;
        }

        try {
            imageDataBuffer.get().limit(imageData.length);
            imageDataBuffer.get().rewind();
            imageDataBuffer.get().put(imageData);

            int preDuration = (int) audioDuration
                    - SoundPhotoConstants.POST_CAPTURE_SOUND_DURATION_IN_SECOND * 1000;
            SoundMetaData metaData = new SoundMetaData();
            metaData.setDuration((int) audioDuration);
            metaData.setPreDuration(Math.max(0, preDuration));
            metaData.setShutterSoundStatus(ShutterSoundStatus.NONE);

            // Put audio data into image data.
            SpfEditor spf = new SpfEditor(imageDataBuffer.get());
            if (spf.addSoundData(SoundCodec.AAC, metaData, audioData)) {
                byte[] result = new byte[imageDataBuffer.get().limit()];
                imageDataBuffer.get().rewind();
                imageDataBuffer.get().get(result);
                spf.release();
                return result;

            } else {
                spf.release();
                if (CameraLogger.DEBUG) CameraLogger.d(TAG, "makeSpf() Fail to add sound data.");
                return null;
            }

        } finally {
            imageDataBuffer.decrementRefCount();
        }
    }

    private static class YuvToJpegTask implements Callable<byte[]> {
        private final int mIndex;
        private final NativeByteBufferHolder mFrame;
        private final PreviewFrameData mPrevireFrames;
        private final Rect mImageRect;
        private final ByteOrder mExifByteOrder;
        private final ExifInfo mExifInfo;
        private final BlockingQueue<byte[]> mWorkBuffers;

        public YuvToJpegTask(
                int index,
                NativeByteBufferHolder frame,
                PreviewFrameData previreFrames,
                Rect imageRect,
                ByteOrder exifByteOrder,
                ExifInfo exifInfo,
                BlockingQueue<byte[]> workBuffers) {
            mFrame = frame;
            mPrevireFrames = previreFrames;
            mIndex = index;
            mImageRect = imageRect;
            mExifByteOrder = exifByteOrder;
            mExifInfo = exifInfo;
            mWorkBuffers = workBuffers;
        }

        @Override
        public byte[] call() {
            byte[] jpegData = null;
            try {
                byte[] yuvData = mWorkBuffers.take();
                mFrame.get().rewind();
                if (CameraLogger.DEBUG) CameraLogger.e(TAG, "MpoMakeTask START"
                        + " index:" + mIndex
                        + " workBuffers:" + mWorkBuffers.size());
                mFrame.get().get(yuvData);

                jpegData = compressToJpeg(yuvData);
                // Recycle work buffer
                mWorkBuffers.put(yuvData);

            } catch (InterruptedException e) {
                CameraLogger.e(TAG, "To take from work buffers is interrupted.");
                return null;
            }

            if (jpegData != null) {
                // Add exif to jpeg
                if (mExifByteOrder != null) {
                    byte[] jpegDataWithExif = Yuv2ExifJpegConvertor.addExifToPlainJpeg(jpegData,
                            mExifInfo);
                    if (sIsDebugFormat) {
                        CommonUtility.dumpFile(jpegData, "jpegData_" + mIndex + ".jpg");
                        CommonUtility.dumpFile(jpegDataWithExif, "jpegDataWithExif_" + mIndex
                                + ".jpg");
                    }

                    if (jpegDataWithExif != null) {
                        jpegData = jpegDataWithExif;
                    } else {
                        // Failed to add EXIF into flame.
                        CameraLogger.e(TAG, "Failed to add EXIF into flame");
                    }
                } else {
                    // Exif byte order of picture data is unknown.
                    // This is irregular case and then Exif is not added.
                    CameraLogger.e(TAG, "Exif byte order of picture data is unknown.");
                }
                return jpegData;
            }
            return null;
        }

        private byte[] compressToJpeg(byte[] yuvData) {
            YuvImage yuvImage = new YuvImage(
                    yuvData,
                    mPrevireFrames.imageFormat,
                    mPrevireFrames.width,
                    mPrevireFrames.height,
                    null);

            ByteArrayOutputStream jpegDataStream = new ByteArrayOutputStream();
            if (yuvImage.compressToJpeg(
                    mImageRect,
                    SoundPhotoConstants.ANIMATION_FRAME_QUALITY,
                    jpegDataStream)) {
                return jpegDataStream.toByteArray();
            } else {
                return null;
            }
        }
    }

    /**
     * Make mpo data which includes 30 preview frame images and a taken photo.
     *
     * @param previewFrames Yuv images and preview information.
     * @param pictureData Jpeg data of a taken picture.
     * @param timeStamp timestamp when taking picture.
     * @param orientation orientation of a taken picture.
     * @param location location of a taken picture.
     */
    private byte[] makeMpf(
            PreviewFrameData previewFrames,
            byte[] pictureData,
            long timeStamp,
            int orientation,
            Location location) {

        BlockingQueue<byte[]> workBuffers = null;
        try {
            workBuffers = createWorkBuffers(
                    previewFrames.width * previewFrames.height * 12 / 8,
                    YUV2JPEG_TASK_NUMBER_LIMIT);
        } catch (InterruptedException e) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "createWorkBuffers is interrupted.", e);
            return null;
        }

        try {
            MpoWriter.startCombineJpegToMpo(
                    previewFrames.images.size() + 1,
                    false);
        } catch (Exception e) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MPO_WRITER: startCombineJpegToMpo.", e);
            return null;
        }

        // Check exif byte order of picture data.
        // Exif of frame data is added by this byte order.
        // Otherwise, exception is thrown at MpoWriter.addCombineJpegData(pictureData),
        // because byte order is inconsistent.
        ByteOrder exifByteOrder = Yuv2ExifJpegConvertor.getExifByteOrder(pictureData);
        ExifInfo exifInfo = new ExifInfo(
                timeStamp,
                orientation,
                location,
                previewFrames.width,
                previewFrames.height,
                exifByteOrder);


        try {
            Rect imageRect = new Rect(0, 0, previewFrames.width, previewFrames.height);
            List<Future<byte[]>> futures = new ArrayList<Future<byte[]>>();
            ExecutorService mpoMakeTaskExecutor = Executors.newFixedThreadPool(
                    workBuffers.size());

            // Combine preview frames.
            int iCnt = 0; // For format debug.
            for (NativeByteBufferHolder frame : previewFrames.images) {
                futures.add(mpoMakeTaskExecutor.submit(new YuvToJpegTask(
                        iCnt++,
                        frame,
                        previewFrames,
                        imageRect,
                        exifByteOrder,
                        exifInfo,
                        workBuffers)));
            }

            for (Future<byte[]> future : futures) {
                try {
                    byte[] jpegData = future.get();
                    if (jpegData != null) {
                        MpoWriter.addCombineJpegData(jpegData, jpegData.length);
                    }

                } catch (InterruptedException e) {
                    if (CameraLogger.DEBUG) CameraLogger.e(TAG,
                            "Exception occurs:" + e.getMessage());
                } catch (ExecutionException e) {
                    if (CameraLogger.DEBUG) CameraLogger.e(TAG,
                            "Exception occurs:" + e.getMessage());
                }
            }

            // Combine picture image.
            if (sIsDebugFormat) CommonUtility.dumpFile(pictureData, "pictureData.jpg");
            MpoWriter.addCombineJpegData(pictureData, pictureData.length);

            // Get output data
            byte[] mpoData = MpoWriter.getOutputData();
            if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                    "Length of mpo data is " + mpoData.length);
            return mpoData;

        } catch (IOException e) {
            if (CameraLogger.DEBUG) CameraLogger.e(TAG, "Exception occurs:" + e.getMessage());
        } finally {
            MpoWriter.endCombineJpegToMpo();
        }

        return null;
    }

    private BlockingQueue<byte[]> createWorkBuffers(int length, int maxCount)
            throws InterruptedException {

        // Apps must not allocate large memory which causes out of memory.
        // This method allocates memory for work buffers within 70% of the limits of java heap.
        // This method allocates more than one and less than 'maxCount'.
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long remain = (Runtime.getRuntime().maxMemory() * 7 / 10) - used;
        int workBufferCount = Math.max(1, Math.min(maxCount, (int) (remain / length)));

        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<byte[]>();
        for (int i = 0; i < workBufferCount; i++) {
            queue.put(new byte[length]);
        }
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "WorkBuffer is allocated:" + queue.size());
        return queue;
    }

    /**
     * Make baseline image which includes only a image.
     * This method adds mpf segment into jpeg data.
     *
     * @param pictureData Jpeg data of a taken picture.
     */
    private static byte[] makeMpf(
            byte[] pictureData) {
        try {
            MpoWriter.startCombineJpegToMpo(1, true);
        } catch (Exception e) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG, "MPO_WRITER: startCombineJpegToMpo.", e);
            return null;
        }

        try {
            // Combine picture image.
            MpoWriter.addCombineJpegData(pictureData, pictureData.length);

            // Get output data
            byte[] mpoData = MpoWriter.getOutputData();
            if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                    "Length of mpf data is " + mpoData.length);
            return mpoData;

        } catch (IOException e) {
            if (CameraLogger.DEBUG) CameraLogger.e(TAG, "Exception occurs:" + e.getMessage());
        } finally {
            MpoWriter.endCombineJpegToMpo();
        }

        return null;
    }

    private static byte[] processSuperResolution(byte[] jpegData, int width, int height) {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "processSuperResolution() E");

        SuperResolutionProcessor processor = SuperResolutionProcessor.create();
        if (processor == null) {
            if (CameraLogger.DEBUG) CameraLogger.d(TAG,
                    "SuperResolution process create complete");
            return null;
        }

        SuperResolutionProcessor.Parameters params = new SuperResolutionProcessor.Parameters(
                width,
                height,
                ImageFormat.JPEG);
        SuperResolutionProcessor.Results results = processor.process(jpegData, params);
        byte[] resultData = results.imageBuffer;
        processor.release();

        if (CameraLogger.DEBUG) CameraLogger.d(TAG, "processSuperResolution() X");
        return resultData;
    }

    private void dumpMemoryUsage(String category) {
        if (CameraLogger.DEBUG) CameraLogger.d(TAG, String.format(Locale.US,
                "#MEMINFO#, %s, %s, %d, %d",
                category,
                CameraLogger.getMemoryUsage(),
                mMainTaskRunner.getTaskCount(),
                NativeByteBufferHolder.debugGetCount()));
    }
}
