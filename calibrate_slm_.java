import java.util.*; 
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.lang.*;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.plugin.filter.*;

/** 
* Calibrate SLM by choosing two points in a calibration image used to generate an SLM pattern
* and two points in the image of the SLM created pattern on a coverslip. These points will
* be used to generate a targetting calibration pattern.
*
* Note that the order in which the points are selected in each image matters!
* This means the first point in the camera image must correspond to the first point
* in the calibration pattern.
*
* @author Tim Machado, Columbia University
* @date 5/14/2010
*/

public class calibrate_slm_ implements PlugIn {

   private int chosenCameraIndex;
   private int chosenCalibrationIndex;
   private ImagePlus[] admissibleImageList;
   private boolean flipVertical = true;
   private boolean flipHorizontal = false;

   // Size of output calibration image
   public static final int OUTPUT_SIZE = 213;

   public void run(String arg) {

      // Get list of open images
      admissibleImageList = createAdmissibleImageList();
      if (admissibleImageList.length < 2) {
        IJ.error("At least two grayscale or RGB-stack images are required");
        return;
      }
      final GenericDialog gd = new GenericDialog("Calibrate SLM", IJ.getInstance());
      int nTargets = admissibleImageList[0].getRoi() != null ? 
         admissibleImageList[0].getRoi().getPolygon().npoints : 0;

      // Add widgets to GUI
      final Choice calibration = new Choice();
      final Choice camera = new Choice();
      final Panel panel = new Panel(new GridLayout(8,1));
      final Label cameraMessage = new Label(nTargets + " (of 2) Targets Selected.");
      final Label calibrationMessage = new Label(nTargets + " (of 2) Targets Selected.");


      gd.add(panel);
      panel.add(new Label("Choose images to use:\n"));
      panel.add(new Label("Camera Image:"));
      panel.add(camera);
      panel.add(cameraMessage);
      panel.add(new Label("Calibration Image:"));
      panel.add(calibration);
      panel.add(calibrationMessage);
      gd.addMessage("\n");
      gd.addCheckbox("Flip Vertical", flipVertical);
      gd.addCheckbox("Flip Horizontal", flipHorizontal);

      String[] choices = new String[admissibleImageList.length];
      for(int i=0; i < choices.length; i++) {
         choices[i] = admissibleImageList[i].getTitle();
         camera.add(choices[i]);
         calibration.add(choices[i]);
      }
      
      // Update appropriately if an image selection has changed
	camera.addItemListener(
           new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  // Update chosen index
                  chosenCameraIndex = camera.getSelectedIndex();
                  
                  // Update GUI
                  int tTargets = admissibleImageList[chosenCameraIndex].getRoi() != null ? 
                     admissibleImageList[chosenCameraIndex].getRoi().getPolygon().npoints : 0;
                  cameraMessage.setText(tTargets + " (of 2) Targets Selected.");
                }
           }
      );
     calibration.addItemListener(
           new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                  // Update chosen index
                  chosenCalibrationIndex = calibration.getSelectedIndex();
                  
                  // Update GUI
                  int tTargets = admissibleImageList[chosenCalibrationIndex].getRoi() != null ? 
                     admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().npoints : 0;
                  calibrationMessage.setText(tTargets + " (of 2) Targets Selected.");
                }
           }
      );

      // Show it
      gd.showDialog();
      
      // Exit if desired
      if (gd.wasCanceled())
         return;

      // Check there are two targets in each image
      int calTargets = admissibleImageList[chosenCalibrationIndex].getRoi() != null ? 
                     admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().npoints : 0;
      int camTargets = admissibleImageList[chosenCameraIndex].getRoi() != null ? 
                     admissibleImageList[chosenCameraIndex].getRoi().getPolygon().npoints : 0;
      if (calTargets != 2 && camTargets != 2) {
         IJ.error("Exactly two targets must be selected in both the camera and calibration images");
         return;
      }

      // Get target coordinates:
      // [xCam1 yCam1 xCam2 yCam2]
      double camPoints[] = {admissibleImageList[chosenCameraIndex].getRoi().getPolygon().xpoints[0],
                            admissibleImageList[chosenCameraIndex].getRoi().getPolygon().ypoints[0],
                            admissibleImageList[chosenCameraIndex].getRoi().getPolygon().xpoints[1],
                            admissibleImageList[chosenCameraIndex].getRoi().getPolygon().ypoints[1]};
      // [xCal1 yCal1 xCal2 yCal2]
      double calPoints[] = {admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().xpoints[0],
                            admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().ypoints[0],
                            admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().xpoints[1],
                            admissibleImageList[chosenCalibrationIndex].getRoi().getPolygon().ypoints[1]};

      // Get flipping preferences
      Vector checkBoxes = gd.getCheckboxes();
      Checkbox cb = (Checkbox) checkBoxes.elementAt(0);
      flipVertical = cb.getState();
      cb = (Checkbox) checkBoxes.elementAt(1);
      flipHorizontal = cb.getState();

      // Do calibration, get array: [xScale yScale xOffset yOffset calibrationImageSize outputPatternSize]
      double[] calibrationOutput = calibrate(calPoints,camPoints,
                                  admissibleImageList[chosenCameraIndex].getWidth());

      // Write calibration file
      writeCalibration(calibrationOutput, flipVertical, flipHorizontal);
   }

  // Actually compute the calibration offsets and scale factors based on the selected points
  private double[] calibrate(double[] calPoints, double[] camPoints, double calSize) {
      double[] calibrationOutput = new double[6];
      // xScale
      calibrationOutput[0] = (calPoints[0]-calPoints[2]) / (camPoints[0] - camPoints[2]);
      // yScale
      calibrationOutput[1] = (calPoints[1]-calPoints[3]) / (camPoints[1] - camPoints[3]);
      // xOffset
      calibrationOutput[2] = calPoints[0]-camPoints[0]*calibrationOutput[0];
      // yOffset
      calibrationOutput[3] = calPoints[1]-camPoints[1]*calibrationOutput[1];
      // size of camera image
      calibrationOutput[4] = calSize;
      // size of desired output mask
      calibrationOutput[5] = OUTPUT_SIZE;

      // For debugging purposes you might want to show the output here
      if(false)
         IJ.error("xScale: " + Double.toString(calibrationOutput[0]) + " yScale: " + Double.toString(calibrationOutput[1]) 
                  + " xOffset: " + Double.toString(calibrationOutput[2]) + " yOffset: " + Double.toString(calibrationOutput[3]));
      return calibrationOutput;
  }

  // Generate output calibration file
  private void writeCalibration(double[] calibrationOutput, boolean fVertical, boolean fHorizontal) {
     
     // Ask the user where to save the file
     SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
     String date = sdf.format(Calendar.getInstance().getTime());
     SaveDialog sd = new SaveDialog("Save Calibration...", "calibration-" + date, ".txt");
     String outPath = sd.getDirectory() + sd.getFileName();

      // Exit if the user hit cancel
      if(sd.getFileName() == null || sd.getFileName().equalsIgnoreCase("null")) {
         return;
      }

      // Write out the file
      try {
           PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outPath)));
           // Print comments in first five lines
           out.println("SLM calibration file -- " + date);
           out.println("-------------------------------------------------------------");
           out.println("1. xScale, 2. yScale, 3. xOffset, 4. yOffset");
           out.println("5. calSize, 6. outputSize, 7. flipVertical, 8. flipHorizontal");
           out.println("-------------------------------------------------------------");

           // Write out calibration data
           for (int i=0; i < calibrationOutput.length; i++)
             out.println(calibrationOutput[i]);

           // Write default flipping values
           out.println(flipVertical); 
           out.println(flipHorizontal);

           // Close the file stream
           out.close();
     } catch(Exception e) {
        IJ.showMessage("Error writing calibration file!");
        return;
     }
  }

  // From TurboReg (Philippe Thevenaz): get list of open images for the user to select from
  private ImagePlus[] createAdmissibleImageList () {
     final int[] windowList = WindowManager.getIDList();
     final Stack<ImagePlus> stack = new Stack<ImagePlus>();
     for (int k = 0; ((windowList != null) && (k < windowList.length)); k++) {
             final ImagePlus imp = WindowManager.getImage(windowList[k]);
             if (imp != null) {
                     stack.push(imp);
            }
     }
     final ImagePlus[] admissibleImageList = new ImagePlus[stack.size()];
     int k = 0;
     while (!stack.isEmpty()) {
              admissibleImageList[k++] = (ImagePlus)stack.pop();
     }
     return(admissibleImageList);
  }
}	
