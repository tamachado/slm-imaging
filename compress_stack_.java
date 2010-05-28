import java.util.*; 
import java.awt.*;
import java.lang.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.PlugIn;

/** 
* For each selected point, draw a box around it that is of length and width
* cell radius * 2 + 1. Extract the pixel values from this box and store them
* in a vector sorted by intensity (if desired). The vector from each cell is
* a column in a larger matrix. This process is repeated for all images in the stack.
* The resultant nMaxima x nNeuron x nImages matrix is then displayed along with
* all of the extracted traces and the numerical index of each target.
* @author Tim Machado, Columbia University
* @date 4/8/2010
*/

public class compress_stack_ implements PlugIn {	
   
    private double nScale;
    private int cRadius, nMaxima;
    private boolean displayMatrix, displayTraces, displayIds, sortTraces;

    // Main function for choosing targets
    public void run(String arg) {
	ImagePlus img = ij.WindowManager.getCurrentImage();
	if (img == null) {
	    IJ.error ("No image present!");
                return;
	}

     if (img.getHeight() != img.getWidth()) {
	    IJ.error ("Image must be square!");
                return;
	}

      // Get selected points
      if (img.getRoi() == null) {
	    IJ.error ("Select targets first!");
                return;
      }

      Roi roi = img.getRoi();
      Polygon poly = roi.getPolygon();
      int nTargets = poly.npoints;
      int[] xpoints = poly.xpoints;
      int[] ypoints = poly.ypoints;

      if (nTargets == 0) {
	    IJ.error ("Select targets first!");
                return;
	}

      // Initialize parameters
      cRadius = 2;    // area around each target to use
      nMaxima = 25;   // pixels from each target to use
      nScale  = 100;  // pixel scale for plotting traces

      // Set up dialog box with parameters
      GenericDialog gd = new GenericDialog("Cell Extraction Options", IJ.getInstance());
      gd.addMessage("Cell Extraction Parameters:\n");
      gd.addNumericField("Cell Radius (px):", cRadius, 4);
      gd.addNumericField("Pixels/ROI to save:", nMaxima, 4);
      gd.addNumericField("Plot Using Pixels/Trace:", nScale, 4);
      gd.addCheckbox("Display Data Matrix?", false);
      gd.addCheckbox("Display Data Traces?", true);
      gd.addCheckbox("Show Cell IDs?", true);
      gd.addCheckbox("Sort Data Matrix?", false);

      // Print how many targets have been selected
      gd.addMessage(nTargets + " Targets Selected.");

      // Show the dialog
      gd.showDialog();

      // Exit if desired
      if (gd.wasCanceled())
         return;

      // Get user field values
      cRadius = (int) gd.getNextNumber();
      nMaxima = (int) gd.getNextNumber();
      nScale  = (int) gd.getNextNumber();
      displayMatrix =  gd.getNextBoolean();
      displayTraces =  gd.getNextBoolean();
      displayIds    =  gd.getNextBoolean();
      sortTraces    =  gd.getNextBoolean();

      // Check if the ROI size is nonzero
      if (cRadius < 1) {
         IJ.error("Choose a larger radius!");
         return;
      }

      // Check if the number of pixels to plot is larger than the
      // total number of pixels in the total ROI
      int maxPixels = ((cRadius*2)+1)*((cRadius*2)+1);
      if (nMaxima > maxPixels) nMaxima = maxPixels;

      // Create a new image stack
      ImageStack input = img.getStack();
      int nFrames = input.getSize();
      ImageStack stack = new ImageStack(nTargets,nMaxima);

      // Save a projection across each cell
      double[][] dPoints = new double[nTargets][nFrames];
      double[] xPoints = new double[nFrames];
      int[][] frame = new int[img.getWidth()][img.getHeight()];

      // Extract appropriate pixels
      double normConst = 1;
      for (int f=1; f <= nFrames; f++) {

         // Get the current image processor
         ImageProcessor cip = input.getProcessor(f) ;
         frame = cip.getIntArray();
         xPoints[f-1] = f;

         // For each target get a square around it of size (cRadius*2 + 1)^2
         int[] rawPoints = new int[((cRadius*2)+1)*((cRadius*2)+1)];
         int[][] framePoints = new int[nTargets][nMaxima];
         for (int i = 0; i < nTargets; i++) {
             // Reset everything to zeros
             int j = 0;
             Arrays.fill(rawPoints,0);
             // Extract pixels
             for (int k = xpoints[i]-cRadius; k <= xpoints[i]+cRadius; k++) {
                for (int l = ypoints[i]-cRadius; l <= ypoints[i]+cRadius; l++) {
                    if (k < cip.getWidth() && k >= 0 && l < cip.getHeight() && l >= 0)
                       rawPoints[j++] = frame[k][l];
                }
             }

             // Save maxima
             if (sortTraces) Arrays.sort(rawPoints);
             double total = 0;
             for (int m = 0; m < nMaxima; m++) {
                framePoints[i][m] = rawPoints[rawPoints.length-1-m];
                // Get the trace across each neuron for plotting, normalize it, and shift on Y
                total = total + framePoints[i][m];
             }
             dPoints[i][f-1] = total;
         }

         // Draw it
         ImageProcessor ip = new ShortProcessor(nTargets,nMaxima);
         ip.setIntArray(framePoints);
         
         // Add it to a new image to end of stack
         stack.addSlice("Slice",ip);
      }

      
      if (displayTraces) {
         // Normalize each trace
         for (int i=0; i < nTargets; i++) {
            double max = 0; double min = Double.MAX_VALUE;
            for(int j=0; j < nFrames; j++) {
               max = dPoints[i][j] > max ? dPoints[i][j] : max;
               min = dPoints[i][j] < min ? dPoints[i][j] : min;
            }
            for(int j=0; j < nFrames; j++)
               dPoints[i][j] = (dPoints[i][j]-min) * (1/(max-min)) * nScale + i*nScale;
         }

         // Plot it  
         Plot plot = new Plot("profile","Time (Frames)", "Fluorescence", xPoints, dPoints[0]);
         plot.setSize(nFrames,nTargets*(int)nScale);
         plot.setLimits(-100,nFrames,0,(nTargets+1)*nScale);

         for (int i=0; i< nTargets; i++) {
            plot.addPoints(xPoints,dPoints[i],Plot.LINE);
            plot.addLabel(0,((double)i+1)/(nTargets+1), "Neuron " + (nTargets-1-i)); 
         }
         plot.show();
      }

      if (displayIds) {
         // Display cell numbers
         int scale = 4;
         ImageProcessor nImgProc = new ColorProcessor(img.getWidth(),img.getHeight());
         nImgProc.setIntArray(frame);
         ImageProcessor sImgProc = nImgProc.resize(img.getWidth()*scale);
         ImagePlus nImg = new ImagePlus("Cell IDs",sImgProc);
         sImgProc.setColor(Color.RED);

         for (int i=0; i < nTargets; i++) {
            TextRoi tRoi = new TextRoi(xpoints[i]*scale,ypoints[i]*scale,nImg);
            tRoi.setFont("SansSerif", 6*scale, Font.BOLD, true);
            String str = Integer.toString(i);
            for (int j=0; j < str.length(); j++)
               tRoi.addChar(str.charAt(j));
            tRoi.drawPixels(sImgProc);
         }
         nImg.show();
         IJ.run(nImg,"Brightness/Contrast...","Auto");
      }

      if (displayMatrix) {
         // Display the data matrix containing the fluorescence traces
         ImagePlus output = new ImagePlus("Data Matrix",stack);
         output.show();
      }
    }
}
