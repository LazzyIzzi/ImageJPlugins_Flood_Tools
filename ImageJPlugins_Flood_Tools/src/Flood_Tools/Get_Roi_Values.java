package Flood_Tools;
//Given an image or a stack with one or more a point rois, return the ixCol,jyRow,kzSlice and the value.

import java.awt.Polygon;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import ij.IJ;
import ij.gui.*;
import ij.measure.ResultsTable;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.*;
import ij.process.ImageProcessor;

//import jhd.FloodFill.Offsets.PointDesc;

public class Get_Roi_Values implements PlugInFilter {

	//*********************************************************************************************
	class Point3D
	{
		public Point3D(int x, int y, int z, String val) {
			super();
			this.x = x;
			this.y = y;
			this.z = z;
			this.val = val;
		}

		int x,y,z;
		String val;
	}

	ImagePlus imp;

	@Override
	public int setup(String arg, ImagePlus imp) {
		// TODO Auto-generated method stub
		this.imp=imp;
		return DOES_ALL;
	}

	@Override
	public void run(ImageProcessor ip)
	{
		List<Point3D> myPts = GetPointsAndValues(imp);
		
		if(myPts==null) return;
		
		ResultsTable rt = ResultsTable.getResultsTable("Point Value Results");
		if(rt==null) rt = new ResultsTable();
		for(Point3D pd : myPts)
		{
			rt.incrementCounter();
			rt.addValue("X", pd.x);
			rt.addValue("Y", pd.y);
			rt.addValue("Z", pd.z);
			rt.addValue("Val", pd.val);
		}
		rt.show("Point Value Results");

	}


	/**
	 * @param imp An imageJ ImagePlus object
	 * @return A list of the X,y,z location and value of the points in its PointRoi, nul id there are no PointRoi points
	 */
	public List<Point3D> GetPointsAndValues(ImagePlus imp)
	{
		List<Point3D> myPts = null;
		//Test to see if the roi is 2D or 3D
		Roi roi= imp.getRoi();
		if(roi==null)
		{
			IJ.showMessage("Please Make a Point Selection");
			return null;
		}

		if(roi.getType()==Roi.POINT)
		{
			PointRoi pointRoi = (PointRoi) roi;
			//pointRoi.getPosition() Returns the stack position (image number) of this ROI
			//or zero if the ROI is not associated with a particular stack image.
			//int slice2D = pointRoi.getPosition();
			//			if(slice2D ==0)
			if(imp.hasImageStack())
			{
				myPts = GetSlicePoints3D();
			}
			else
			{
				myPts = GetSlicePoints2D();				
			}
		}
		return myPts;
	}


	//********************************************************************************

	private List<Point3D> GetSlicePoints3D()
	{
		int ixCol,jyRow,kzSlice;
		List<Point3D> slicePoints =new ArrayList<>();

		Roi roi= imp.getRoi();
		PointRoi pRoi = (PointRoi) roi;
		Polygon p = roi.getPolygon();

		ImageStack stk = imp.getStack();
		int bitDepth = imp.getBitDepth();
		String sVal;
		switch(bitDepth)
		{
		case 8: case 16: case 32:
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				kzSlice = pRoi.getPointPosition(i)-1;
				double dVal =stk.getVoxel(ixCol, jyRow, kzSlice);
				sVal = Double.toString(dVal);				
				slicePoints.add(new Point3D(ixCol, jyRow, kzSlice,sVal));
			}			
			break;
		case 24:
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				kzSlice = pRoi.getPointPosition(i);
				int[] rgb = stk.getProcessor(kzSlice).getPixel(ixCol, jyRow, null);					
				sVal = Arrays.toString(rgb);
				slicePoints.add(new Point3D(ixCol, jyRow, kzSlice-1,sVal));	
			}
			break;
		}
		return slicePoints;
	}

	//********************************************************************************

	private List<Point3D> GetSlicePoints2D()
	{
		int ixCol,jyRow;
		String sVal;
		List<Point3D> slicePoints =new ArrayList<>();

		Roi roi= imp.getRoi();
		Polygon p = roi.getPolygon();
		int bitDepth = imp.getBitDepth();
		switch(bitDepth)
		{
		case 8: case 16:
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				int iVal =imp.getProcessor().getPixel(ixCol, jyRow);
				sVal = Integer.toString(iVal);				
				slicePoints.add(new Point3D(ixCol, jyRow, 0,sVal));					
			}
			break;
		case 32:
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				int iVal = imp.getProcessor().getPixel(ixCol, jyRow);
				float fVal = Float.intBitsToFloat(iVal);
				sVal = Float.toString(fVal);
				slicePoints.add(new Point3D(ixCol, jyRow, 0,sVal));
			}
			break;
		case 24:
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				int[] rgb = imp.getProcessor().getPixel(ixCol, jyRow, null);
				sVal = Arrays.toString(rgb);
				slicePoints.add(new Point3D(ixCol, jyRow, 0,sVal));				
			}
			break;
		}
		return slicePoints;
	}
}
