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


import jhd.FloodFill.Offsets.PointDesc;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.CheckboxField;
import jhd.FloodFill.GDT3D;

/**
 * Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
 * @author John H Dunsmuir
 */
public class GeodesicPath_3D implements PlugInFilter, DialogListener {

	ImagePlus		imp;
	ImageProcessor	ip;
	PointDesc[]		offsetPts;
	CheckboxField addToRoiCBF, showPathsCBF;
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
			IJ.showMessage("Missing Image Properties","Please select a 3D Geodesic Transform Image");
			imageOK=false;
		}
		else
		{
			String source = props.getProperty("GeodesicSource");
			String type = props.getProperty("GeodesicType");
			if(source!=null && type!=null)
			{
				if(source.indexOf("3D")==-1 || type.indexOf("Distance")==-1)
				{
					IJ.showMessage("Incorrect Image Properties","Please select a 3D Geodesic Transform Image");
					imageOK=false;
				}
			}
			else
			{
				IJ.showMessage("Missing Image Properties","Please select a 3D Geodesic Transform Image");
				imageOK=false;
			}
		}

		Roi roi = imp.getRoi();
		if(roi==null  || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Select a one or more points in the GDT area using the multi-Point ROI tool");
			imageOK=false;
		}

		return imageOK;
	}

	//***********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{

		this.ip = ip;

		if(!validateImage()) return;

		GenericDialog gd = new GenericDialog("Geodesic Path 3D");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addMessage("Find the shortest path from a point\n"
				+ "in a 3D GDT image to the GDT origin.",myFont,Color.BLACK);
		gd.addCheckbox("Show Path(s) in New 8 bit Image", false);
		showPathsCBF = gda.getCheckboxField(gd, "showPaths");
		gd.setInsets(0, 20, 0);
		gd.addMessage("(Scroll through the Path Image or use\n"
				+ "Image->Stacks->3D Project to view.)");
		gd.setInsets(20, 20, 0);
		gd.addCheckbox("Add Path(s) to Roi Manager", true);
		addToRoiCBF = gda.getCheckboxField(gd, "addToRoi");
		gd.setBackground(myColor);
		gd.addDialogListener(this);
		gd.showDialog();

		if(gd.wasCanceled()) return;		
		boolean showPaths = gd.getNextBoolean();
		boolean addToRoiMgr =  gd.getNextBoolean();

		ArrayList<PointDesc> seedPts = getSeedPts(imp);

		if(!seedPts.isEmpty())
		{
			ArrayList<PointDesc>[] geoPaths3D = getGeoPaths3D(seedPts);
			if(addToRoiMgr)addToRoiManager(geoPaths3D);	
			if(showPaths)showPaths(geoPaths3D);	
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
						showPathsCBF.getCheckBox().getState()==false)
				{
					dialogOK = false;
				}

			}	
		}
		return dialogOK;
	}
	
	//***********************************************************************************************

	private ArrayList<PointDesc> getSeedPts(ImagePlus imp)
	{
		ArrayList<PointDesc> seedPts = new ArrayList<PointDesc>();
		ImageStack stack = imp.getStack();
		Roi roi = imp.getRoi();
		Polygon p = roi.getPolygon();
		PointRoi pRoi=(PointRoi)roi;

		for(int j=0;j<p.npoints;j++)
		{
			//stack.getVoxel is 1 based, pRoi.getPointPosition is 0 based
			double val = stack.getVoxel(p.xpoints[j],p.ypoints[j],pRoi.getPointPosition(j)-1);
			// In the GDT the solid phase is -2, the unaccessed open phase is -1, and the seed points are 0
			if(val>0)
			{
				seedPts.add(new PointDesc(p.xpoints[j],p.ypoints[j],pRoi.getPointPosition(j)-1,val));				
			}			
		}
		return seedPts;
	}	

	//***********************************************************************************************

	private ArrayList<PointDesc>[] getGeoPaths3D(ArrayList<PointDesc> seedPts)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getNSlices();
		Object[] data = imp.getStack().getImageArray();

		GDT3D myGdt = new GDT3D();
		ArrayList<PointDesc>[] geoPaths3D = myGdt.getGeoPaths3D(data,width,height,depth, seedPts);
		return geoPaths3D;
	}


	//***********************************************************************************************

	private void addToRoiManager(ArrayList<PointDesc>[] geoPaths3D)
	{
		RoiManager roiMgr = RoiManager.getRoiManager();
		if(roiMgr==null)
		{
			roiMgr = new RoiManager();
		}
		int i=roiMgr.getCount();
		for(ArrayList<PointDesc> path : geoPaths3D)
		{
			PointRoi ptRoi = new PointRoi();
			ptRoi.setOptions("small");
			for(PointDesc point : path)
			{
				ptRoi.addPoint(point.x,point.y,point.z+1);
			}
			roiMgr.addRoi(ptRoi);
			roiMgr.rename(i, "Path3D_" + (i+1));
			i++;
		}				

	}
	//***********************************************************************************************

	private void showPaths(ArrayList<PointDesc>[] geoPaths3D)
	{
		ImagePlus pathImp = IJ.createImage("Path Image",imp.getWidth(),imp.getHeight(),imp.getNSlices(),8);
		ImageStack pathStk = pathImp.getStack();

		int i=101;
		for(ArrayList<PointDesc> path : geoPaths3D)
		{
			for(PointDesc pl : path)
			{
				pathStk.setVoxel(pl.x,pl.y,pl.z, i);
			}
			i++;
			if(i>255) break;
		}
		pathImp.setDisplayRange(0, i);
		pathImp.show();		
	}

}
