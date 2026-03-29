package com.hackathon.logic;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import nu.pattern.OpenCV;

public class VisionManager {
    private VideoCapture camera;
    private CascadeClassifier faceCascade;
    private CascadeClassifier eyeCascade;
    
    private boolean isTracking = false;
    
    // NEW: The Hardware Kill-Switch (volatile ensures thread-safety)
    private volatile boolean hardwareAvailable = true; 
    
    private int distractionFrames = 0;
    private final int DISTRACTION_THRESHOLD = 30; // 3 seconds at 100ms per frame
    private Runnable onDistractedCallback;

    public VisionManager(Runnable onDistractedCallback) {
        this.onDistractedCallback = onDistractedCallback;
        OpenCV.loadLocally(); 
        
        faceCascade = new CascadeClassifier("haarcascade_frontalface_default.xml");
        eyeCascade = new CascadeClassifier("haarcascade_eye.xml");
        System.out.println("[VisionManager] Initialized and ready.");
    }

    public void startTracking() {
        // --- GRACEFUL DEGRADATION ---
        // If we already know there's no camera, instantly ignore the request
        if (!hardwareAvailable) {
            System.out.println("[VisionManager] Eye-tracker ignored (No camera hardware detected).");
            return;
        }

        if (isTracking) return;
        
        isTracking = true;
        distractionFrames = 0;
        System.out.println("[VisionManager] Booting up webcam...");
        
        new Thread(() -> {
            camera = new VideoCapture(0); 
            
            // --- THE HARDWARE CHECK ---
            if (!camera.isOpened()) {
                System.out.println("[VisionManager] WARNING: No webcam detected! Disabling tracker permanently for this session.");
                hardwareAvailable = false; // Throw the kill-switch
                isTracking = false;
                return; // Safely exit the thread
            }
            
            System.out.println("[VisionManager] Webcam LIVE. Tracking started.");
            try { Thread.sleep(1000); } catch (InterruptedException e) {}

            Mat frame = new Mat();
            Mat grayFrame = new Mat();
            
            while (isTracking && camera.isOpened()) {
                camera.read(frame);
                if (frame.empty()) continue;

                Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(grayFrame, grayFrame);

                MatOfRect faces = new MatOfRect();
                faceCascade.detectMultiScale(grayFrame, faces);

                boolean eyesDetected = false;

                for (Rect face : faces.toArray()) {
                    Mat faceROI = grayFrame.submat(face);
                    MatOfRect eyes = new MatOfRect();
                    eyeCascade.detectMultiScale(faceROI, eyes);
                    
                    if (eyes.toArray().length > 0) {
                        eyesDetected = true;
                        break;
                    }
                }

                if (!eyesDetected) {
                    distractionFrames++;
                    if (distractionFrames >= DISTRACTION_THRESHOLD) {
                        System.out.println("[VisionManager] DISTRACTION THRESHOLD REACHED! Firing penalty.");
                        isTracking = false; 
                        onDistractedCallback.run(); 
                    }
                } else {
                    distractionFrames = 0; 
                }

                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
            
            if (camera != null && camera.isOpened()) {
                camera.release();
                System.out.println("[VisionManager] Webcam safely closed.");
            }
        }).start();
    }

    public void stopTracking() {
        isTracking = false;
    }
}