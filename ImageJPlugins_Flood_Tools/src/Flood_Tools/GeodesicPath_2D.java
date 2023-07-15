package Flood_Tools;

/*
Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
Input: a 32-bit 2D Geodesic transformed image where the non-transformed phase (the obstacles) are set to zero.
	and at least one point in the GDT phase. DO NOT use on tortuosity mapped images.
Output: The approximated shortest path between the point(s) and the nearest GDT distance = 1 is drawn in the image overlay.
Note:	The paths drawn are not necessarily the same as those a human would draw.  This is a limitation of the
	GDT by propagation method used to construct the underlying GDT distance map. Distances can propagate
	in only eight directions. 0,45, 90, 135, 180, 225, 270 and 315 degrees. so the propagating paths make make
	only 45 and 90 degree turns to either side. 
	Increase the iteration count (see code) to draw long paths.
JHD 12/22/19
 */


import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;

import java.util.ArrayList;
import java.util.Properties;

//import jhd.DistanceMaps.libJ8.Offsets.PointDesc;
//import jhd.DistanceMaps.libJ8.*;
import jhd.FloodFill.GDT3D;
import jhd.FloodFill.Offsets.PointDesc;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.CheckboxField;

/**
 * Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
 * @author John H Dunsmuir
 */
public class GeodesicPath_2D implements PlugInFilter, DialogListener
{

	ImagePlus		imp;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff

	//***********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32+NO_UNDO;
	}

	//***********************************************************************************************
	
	private boolean validateImage()
	{
		boolean imageOK=true;
		Properties props = imp.getImageProperties();
		if(props==null)
		{
			IJ.showMessage("Missing Image Properties","Please select a 2D Geodesic Transform Image");
			imageOK = false;
		}
		else
		{
			String source = props.getProperty("GeodesicSource");
			String type = props.getProperty("GeodesicType");
			if(source!=null && type!=null)
			{
				if(source.indexOf("2D")==-1 || type.indexOf("Distance")==-1)
				{
					IJ.showMessage("Incorrect Image Properties","Please select a 2D Geodesic Transform Image");
					imageOK = false;
				}
			}
			else
			{
				IJ.showMessage("Missing Image Properties","Please select a 2D Geodesic Transform Image");	
				imageOK = false;
			}
		}

		Roi roi = imp.getRoi();

		if(roi==null  || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Select one or more points in a GDT area using the multi-Point ROI tool");
			imageOK = false;
		}
		
		return imageOK;
	}
	
	//***********************************************************************************************
	
	CheckboxField addToRoiCBF, addToOverlayCBF;

	@Override
	public void run(ImageProcessor ip)
	{
		if(!validateImage()) return;

		GenericDialog gd = new GenericDialog("Geodesic Path 2D");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addMessage("Find the shortest path from a point in a 2D GDT\n",myFont);
		gd.addCheckbox("Add to Roi Manager", true);
		addToRoiCBF = gda.getCheckboxField(gd, "addToRoi");
		gd.addCheckbox("Add to image overlay", false);
		addToOverlayCBF = gda.getCheckboxField(gd, "addToOverlay");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return;
		boolean addToRoiMgr= gd.getNextBoolean();
		boolean addToOverlay = gd.getNextBoolean();

		ArrayList<PointDesc> seedPts = getSeedPts(imp);
		if(!seedPts.isEmpty())
		{
			ArrayList<PointDesc> geoPath2D;
			for(PointDesc pt : seedPts)
			{
				Object data2D = imp.getStack().getPixels(pt.z+1);		
				geoPath2D = getPathPoints(data2D,imp.getWidth(),imp.getHeight(),pt);
				PointRoi ptRoi = gdtPathToPointRoi(geoPath2D);
				if(addToRoiMgr)addToRoiManager(ptRoi);	
				if(addToOverlay) addPointRoiToOverlay(ptRoi);					
			}
		}
	}
	
	//***********************************************************************************************

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		boolean dialogOK = true;
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof Checkbox)
			{
				//at least one checkbox must be true
				if(addToRoiCBF.getCheckBox().getState()==false &&
						addToOverlayCBF.getCheckBox().getState()==false)
				{
					dialogOK = false;
				}

			}	
		}
		return dialogOK;
	}
	
	//***********************************************************************************************

	/**
	 * @param imp the imagePlus
	 * @return a list of the seed points in each slice
	 */
	private ArrayList<PointDesc> getSeedPts(ImagePlus imp)
	{
		ArrayList<PointDesc> seedPts = new ArrayList<PointDesc>();
		
		Roi roi = imp.getRoi();
		Polygon p = roi.getPolygon();
		PointRoi pRoi = (PointRoi)roi;
		
		//get the seed points from the current point roi	
		int counterIndex = pRoi.getCounter();
		int nPts = pRoi.getCount(counterIndex);
		int slice=0;
		ImageStack stk = imp.getStack();
		
		for(int i=0;i<nPts;i++)
		{
			int x = p.xpoints[i];
			int y = p.ypoints[i];
			//getPointPosition Returns the stack slice of the point with the given index, or 0 if no slice defined for this point 
			slice = pRoi.getPointPosition(i);
			if(slice==0) slice=1;
 			float val = (float)stk.getVoxel(x, y, slice-1);				
			if(val>0) seedPts.add(new PointDesc(x,y,slice-1,val));	
 		}
		return seedPts;
	}
	
	//***********************************************************************************************

	private ArrayList<PointDesc> getPathPoints(Object data2D,int width, int height, PointDesc seedPt)
	{
		GDT3D myGdt = new GDT3D();		
		ArrayList<PointDesc> geoPaths2D = new ArrayList<PointDesc>();
		geoPaths2D = myGdt.getGeoPath2D(data2D,width,height, seedPt);			
		return geoPaths2D;
	}
	
	//***********************************************************************************************
	
	private void addPointRoiToOverlay(PointRoi ptRoi)
	{
		Overlay pathOverlay = imp.getOverlay();
		if(pathOverlay==null) pathOverlay = new Overlay();		
		int[] xPts = ptRoi.getPolygon().xpoints;
		int[] yPts = ptRoi.getPolygon().ypoints;
		int nPts = ptRoi.getNCoordinates();
		PolygonRoi pathRoi = new PolygonRoi(xPts,yPts,nPts,Roi.FREELINE );
		pathRoi.setPosition(ptRoi.getZPosition());
		pathOverlay.add(pathRoi);
		imp.setOverlay(pathOverlay);
	}
	
	//***********************************************************************************************
	
	private PointRoi gdtPathToPointRoi(ArrayList<PointDesc> geoPaths2D)
	{
		PointRoi ptRoi = new PointRoi();
		int slice = 1+geoPaths2D.get(0).z;
		ptRoi.setPosition(slice);
		for(PointDesc point : geoPaths2D)
		{
			ptRoi.addPoint(point.x,point.y,slice);
		}
		return ptRoi;		
	}

	//***********************************************************************************************

	private void addToRoiManager(PointRoi ptRoi)
	{
		RoiManager roiMgr = RoiManager.getRoiManager();

		if(ptRoi!=null)
		{
			if(roiMgr==null) roiMgr = new RoiManager();
			Prefs.useNamesAsLabels=true;
			Prefs.showAllSliceOnly=true;
			int i=roiMgr.getCount();
			ptRoi.setOptions("small dot red");
			roiMgr.addRoi(ptRoi);
			roiMgr.rename(i, "Path2D_" + (i+1) + "Slice_" + ptRoi.getZPosition());
		}		
	}

	//***********************************************************************************************
}
