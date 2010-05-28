Imaging Scripts
5/28/2010
tamachado, Columbia University

***

scripts for use with imagej. you might like the compress stack script if you
do calcium imaging and want to look at your data online.if you use an slm
the other scripts might be useful for you.

***

installation:
put these scripts into your imagej folder/plugins/SLM and restart the program.
the scripts will be found in Plugins->SLM

***

file descriptions:
read the description at the top of each file for more information.

1. calibrate slm: find mapping between a calibration image and the projection of the
calibration image through the slm as seen by the camera. then save out the 
registration info in a text file. to make this work, you need to choose two points
in the camera image and the exact same points in the calibration image. note that
the order in which you choose the points matters because the script assumes that 
point 1 in the calibration image matches with point 2 in the projected image.

2. choose targets: take a selection of points and use them to generate an excitation
pattern for use with the slm. use calibrate slm to generate a calibration file.

3. compress stack: extract fluorescence traces across a tif stack and plot them. 
you must select the points where the cells are prior to running this script.
to do that, use the Process->Binary->Find Maxima function built into imagej. 
you have to set the output type dropbox in Find Maxima to 
"Point Selection" in order to get points.  