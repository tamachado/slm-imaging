import java.util.*; 
import java.awt.*;
import java.awt.event.*;
import java.lang.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.plugin.filter.*;

/** 
* Choose targets for SLM phase mask
* @author Tim Machado, Columbia University
* @date 4/8/2010
*/

public class choose_targets_ implements PlugIn {	
    
    //////////////////////////////
    // Default Calibration Values
    //////////////////////////////

    // fat 20x, 0.95 NA (4/16/10)
    private double xScale  = -0.7576;
    private double yScale  =  0.7937;
    private double xOffset = 199.2424;
    private double yOffset = -1.5873;
    // How large was the calibration image   (pixels squared)
    private int calSize = 256;
    // How large was the calibration pattern (pixels squared)
    private int outSize = 213;
    private int threshold = 1;
    // Flip image if desired
    private boolean flipVertical   = true;
    private boolean flipHorizontal = true;

    ///////////////////////////
    // Mask generation function
    ///////////////////////////

    // Main function for choosing targets
    public void run(String arg) {

      // Check if an image is open
	final ImagePlus img = ij.WindowManager.getCurrentImage();
	if (img == null) {
	    IJ.error ("No image present!");
                return;
	}

     if (img.getHeight() != img.getWidth()) {
	    IJ.error ("Image must be square!");
                return;
	}

      // Open up calibration file
      OpenDialog od = new OpenDialog("Choose a calibration file", null);
      String name = od.getFileName();
      String dir  = od.getDirectory();
      if (name==null) {
         IJ.showMessage("Using arbitrary calibration values!");
      } else {
        try {
           BufferedReader dis = new BufferedReader(new FileReader(dir+name));
           // Skip the first five lines
           int N_COMMENT = 5;
           for(int i=0; i < N_COMMENT; i++)
            dis.readLine();
           // Read in the calibration data on the following lines
           xScale   = Double.parseDouble(dis.readLine());
           yScale   = Double.parseDouble(dis.readLine());
           xOffset  = Double.parseDouble(dis.readLine());
           yOffset  = Double.parseDouble(dis.readLine());
           calSize  = (int) Double.parseDouble(dis.readLine());
           outSize  = (int) Double.parseDouble(dis.readLine());
           flipVertical = Boolean.parseBoolean(dis.readLine());
           flipHorizontal = Boolean.parseBoolean(dis.readLine());
           dis.close();
        } catch(Exception e) {
           IJ.showMessage("Error parsing calibration file!");
           return;
        }
      }

      // Get roi if targets already exist
      Roi roi = img.getRoi();
      int nTargets = 0;
      int sOffset  = 1;
      Polygon poly;

      if (img.getRoi() != null) {
	   roi = img.getRoi();
         poly = roi.getPolygon();
         nTargets = poly.npoints;      
        sOffset = calSize/img.getHeight();
      }

      // Setup dialog box with calibration parameters
      final GenericDialog gd = new GenericDialog("SLM Target Selection", IJ.getInstance());
      final Panel maximaPanel = new Panel(new GridLayout(4,1));
      gd.add(maximaPanel);
      gd.addMessage("\n");
      gd.addNumericField("Maxima Threshold:", threshold, 1);
      gd.addMessage("\n");
      gd.addMessage("SLM Calibration Parameters:\n");
      gd.addNumericField("xScale:", xScale, 4);
      gd.addNumericField("yScale:", yScale, 4);
      gd.addNumericField("xOffset:", xOffset, 4);
      gd.addNumericField("yOffset:", yOffset, 4);
      gd.addMessage("\n");
      gd.addNumericField("Calibration Image Size:", calSize, 0);
      gd.addNumericField("Calibration Pattern Size:", outSize, 0);
      gd.addMessage("\n");
      gd.addCheckbox("Flip Vertical:", flipVertical);
      gd.addCheckbox("Flip Horizontal:", flipHorizontal);
      updateFields(gd);
      final Button maximaButton = new Button("Find Maxima...");
      final Label targetMessage = new Label(nTargets + " Targets Selected. Scale offset = " + sOffset);

      // If "Find Maxima" button is pressed, update chosen targets
	maximaButton.addActionListener(
           new ActionListener() {
                public void actionPerformed(ActionEvent e) {

                  // Update ROI
                  updateFields(gd);
                  IJ.run(img,"Find Maxima...","noise=" + threshold + " output=[Point Selection]");
                  int tTargets = img.getRoi().getPolygon().npoints;
                  int tOffset  = calSize/img.getHeight();

                  // Update GUI
                  targetMessage.setText(tTargets + " Targets Selected. Scale offset = " + tOffset + "\n");
                }
           }
      );

      // Add widgets to panel
      maximaPanel.add(maximaButton);  maximaPanel.add(new Label(""));
      maximaPanel.add(targetMessage); maximaPanel.add(new Label(""));

      // Show the dialog
      gd.showDialog();

      // Exit if desired
      if (gd.wasCanceled())
         return;

      // Get selected points
      if (img.getRoi() == null) {
	    IJ.error ("Select targets first!");
                return;
      }

      // Get user field values
      updateFields(gd);
      roi = img.getRoi();
      poly = roi.getPolygon();
      nTargets = poly.npoints;      
      sOffset = calSize/img.getHeight();
      int[] xpoints = poly.xpoints;
      int[] ypoints = poly.ypoints;

      if (nTargets == 0) {
	    IJ.error ("Select targets first!");
                return;
	}

      // Make the mask
      PointRoi maskRoi = generateMask(sOffset, xpoints, ypoints);

      // Add it to a new image
      IJ.run("Image...", "fill=White width=" + outSize + " height=" + outSize);
      ImagePlus mask = ij.WindowManager.getCurrentImage();
      ImageProcessor ip = mask.getChannelProcessor();
      maskRoi.drawPixels(ip);
      ip.invert();
      if(flipHorizontal)
         IJ.run(mask,"Flip Horizontally","");
      if(flipVertical)
         IJ.run(mask,"Flip Vertically","");

      // Show it
	mask.updateAndDraw();
	mask.show();
      
      // Save it
      SaveDialog sd = new SaveDialog("Save Mask...", "mask", ".bmp");
      String outPath = sd.getDirectory() + sd.getFileName();
      if(sd.getFileName()!= null && !sd.getFileName().equalsIgnoreCase("null")) {
         IJ.saveAs("BMP", outPath);
      }
    }

    // Get fields from the dialog
    private void updateFields(final GenericDialog gd) {
      
      // This is messy and could be rewritten in a more slick way, but it's just GUI garbage

      // Handle numeric fields
      Vector fields = gd.getNumericFields();
      TextField tf = (TextField) fields.elementAt(0);
      threshold = (int) Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(1);
      xScale  = Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(2);
      yScale  = Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(3);
      xOffset = Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(4);
      yOffset = Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(5);
      calSize = (int) Double.parseDouble(tf.getText());
      tf = (TextField) fields.elementAt(6);
      outSize = (int) Double.parseDouble(tf.getText());

      // Handle binary fields
      Vector checkBoxes = gd.getCheckboxes();
      Checkbox cb = (Checkbox) checkBoxes.elementAt(0);
      flipVertical = cb.getState();
      tf = (TextField) fields.elementAt(1);
      flipHorizontal = cb.getState();
    }

    // Iterate over all targets and transform them to the calibrated space
    private PointRoi generateMask(int sOffset, int[] xpoints, int[] ypoints) {

      int[] nxpoints = new int[xpoints.length];
      int[] nypoints = new int[ypoints.length]; 
      double xc, yc;

      for (int i = 0; i < xpoints.length; i++) {
         xc = xScale*sOffset*xpoints[i] + xOffset;
         yc = yScale*sOffset*ypoints[i] + yOffset;
         nxpoints[i] = (int)Math.round(xc);
         nypoints[i] = (int)Math.round(yc);
      }
      
      PointRoi maskRoi = new PointRoi(nxpoints,nypoints,xpoints.length);

      return maskRoi;
    }
}
