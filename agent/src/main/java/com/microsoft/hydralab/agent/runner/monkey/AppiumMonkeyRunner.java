// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.hydralab.agent.runner.monkey;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.img.gif.AnimatedGifEncoder;
import com.microsoft.hydralab.agent.runner.TestTaskRunCallback;
import com.microsoft.hydralab.agent.runner.appium.AppiumRunner;
import com.microsoft.hydralab.common.entity.common.AndroidTestUnit;
import com.microsoft.hydralab.common.entity.common.DeviceInfo;
import com.microsoft.hydralab.common.entity.common.DeviceTestTask;
import com.microsoft.hydralab.common.entity.common.TestTask;
import com.microsoft.hydralab.common.logger.LogCollector;
import com.microsoft.hydralab.common.management.DeviceManager;
import com.microsoft.hydralab.common.screen.ScreenRecorder;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AppiumMonkeyRunner extends AppiumRunner {
    private final AnimatedGifEncoder e = new AnimatedGifEncoder();

    public AppiumMonkeyRunner(DeviceManager deviceManager, TestTaskRunCallback testTaskRunCallback) {
        super(deviceManager, testTaskRunCallback);
    }

    @Override
    protected File runAndGetGif(File appiumJarFile, String appiumCommand, DeviceInfo deviceInfo, TestTask testTask, DeviceTestTask deviceTestTask, File deviceTestResultFolder, Logger reportLogger) {
        String pkgName = testTask.getPkgName();

        long recordingStartTimeMillis = System.currentTimeMillis();
        ScreenRecorder deviceScreenRecorder = deviceManager.getScreenRecorder(deviceInfo, deviceTestResultFolder, reportLogger);
        deviceScreenRecorder.setupDevice();
        deviceScreenRecorder.startRecord(testTask.getTimeOutSecond());

        LogCollector logCollector = deviceManager.getLogCollector(deviceInfo, pkgName, deviceTestTask, reportLogger);
        logCollector.start();
        deviceTestTask.setTotalCount(1);

        AndroidTestUnit ongoingMonkeyTest = new AndroidTestUnit();
        ongoingMonkeyTest.setNumtests(deviceTestTask.getTotalCount());
        ongoingMonkeyTest.setStartTimeMillis(System.currentTimeMillis());
        ongoingMonkeyTest.setRelStartTimeInVideo(ongoingMonkeyTest.getStartTimeMillis() - recordingStartTimeMillis);
        ongoingMonkeyTest.setCurrentIndexNum(1);
        ongoingMonkeyTest.setTestName("Appium_Monkey_Test");
        ongoingMonkeyTest.setTestedClass(pkgName);
        ongoingMonkeyTest.setDeviceTestResultId(deviceTestTask.getId());
        ongoingMonkeyTest.setTestTaskId(deviceTestTask.getTestTaskId());

        reportLogger.info(ongoingMonkeyTest.getTitle());

        deviceTestTask.addNewTimeTag(1 + ". " + ongoingMonkeyTest.getTitle(), System.currentTimeMillis() - recordingStartTimeMillis);
        deviceInfo.setRunningTestName(ongoingMonkeyTest.getTitle());
        File gifFile = new File(deviceTestTask.getDeviceTestResultFolder(), pkgName + ".gif");
        e.start(gifFile.getAbsolutePath());
        e.setDelay(1000);
        e.setRepeat(0);
        deviceManager.updateScreenshotImageAsyncDelay(deviceInfo, TimeUnit.SECONDS.toMillis(5), (imagePNGFile -> {
            if (imagePNGFile == null) {
                return;
            }
            if (!e.isStarted()) {
                return;
            }
            try {
                e.addFrame(ImgUtil.toBufferedImage(ImgUtil.scale(ImageIO.read(imagePNGFile), 0.3f)));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }), reportLogger);
        deviceTestTask.setTestStartTimeMillis(System.currentTimeMillis());
        deviceManager.runAppiumMonkey(deviceInfo, pkgName, testTask.getMaxStepCount(), reportLogger);

        deviceScreenRecorder.finishRecording();
        logCollector.stopAndAnalyse();

        // Success status
        if (logCollector.isCrashFound()) {
            // Fail
            ongoingMonkeyTest.setStatusCode(AndroidTestUnit.StatusCodes.FAILURE);
            ongoingMonkeyTest.setSuccess(false);
            ongoingMonkeyTest.setStack(e.toString());
            deviceTestTask.setSuccess(false);
            deviceTestTask.addNewTimeTagBeforeLast(ongoingMonkeyTest.getTitle() + ".fail", System.currentTimeMillis() - recordingStartTimeMillis);
            deviceTestTask.oneMoreFailure();
        } else {
            // Pass
            ongoingMonkeyTest.setStatusCode(AndroidTestUnit.StatusCodes.OK);
            ongoingMonkeyTest.setSuccess(true);
            deviceTestTask.setSuccess(true);
        }

        // Test finish
        reportLogger.info(ongoingMonkeyTest.getTitle() + ".end");
        ongoingMonkeyTest.setEndTimeMillis(System.currentTimeMillis());
        deviceInfo.setRunningTestName(null);
        deviceTestTask.addNewTestUnit(ongoingMonkeyTest);
        deviceTestTask.addNewTimeTag(ongoingMonkeyTest.getTitle() + ".end", System.currentTimeMillis() - recordingStartTimeMillis);
        deviceTestTask.onTestEnded();
        return gifFile;
    }
}
